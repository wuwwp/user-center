package com.wwp.usercenter.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.model.enums.DeletedFlag;
import com.wwp.usercenter.model.enums.UserRole;
import com.wwp.usercenter.model.enums.UserStatus;
import com.wwp.usercenter.util.UserAccountUtils;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 管理端闭环：管理员登录、分页、封禁/解封、重置密码与普通用户越权。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminFlowIntegrationTest {

  @Container @ServiceConnection
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(DockerImageName.parse("mysql:8.4")).withDatabaseName("usercenter");

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("spring.data.redis.password", () -> "");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private StringRedisTemplate redisTemplate;
  @Autowired private UserMapper userMapper;
  @Autowired private PasswordEncoder passwordEncoder;

  private User admin;
  private User user;

  @BeforeEach
  void setUp() {
    redisTemplate.execute(
        (RedisCallback<Void>)
            connection -> {
              connection.serverCommands().flushDb();
              return null;
            });
    admin = createUser(UserRole.ADMIN, "AdminPassw0rd1");
    user = createUser(UserRole.USER, "UserPassw0rd1");
  }

  @Test
  void should_complete_admin_flow_and_invalidate_user_tokens() throws Exception {
    String adminToken = login(admin.getEmail(), "AdminPassw0rd1");
    String userToken = login(user.getEmail(), "UserPassw0rd1");

    mockMvc
        .perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].emailMasked", containsString("***")))
        .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());

    mockMvc
        .perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());

    // 管理员不能封禁自己
    changeStatus(adminToken, admin.getUserAccount(), UserStatus.BANNED)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(40000));

    // 封禁普通用户：旧令牌失效，登录返回 40103
    changeStatus(adminToken, user.getUserAccount(), UserStatus.BANNED).andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", "Bearer " + userToken))
        .andExpect(status().isUnauthorized());
    expectLogin(user.getEmail(), "UserPassw0rd1", 40103);

    // 解封后可以重新登录
    changeStatus(adminToken, user.getUserAccount(), UserStatus.NORMAL).andExpect(status().isOk());
    String tokenAfterUnban = login(user.getEmail(), "UserPassw0rd1");

    // 管理员重置密码：旧密码失效，新密码可登录，旧令牌失效
    MvcResult resetResult =
        mockMvc
            .perform(
                post("/api/v1/admin/users/" + user.getUserAccount() + "/password/reset")
                    .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
    String newPassword =
        JsonPath.read(resetResult.getResponse().getContentAsString(), "$.newPassword");
    assertThat(newPassword).hasSize(16);

    expectLogin(user.getEmail(), "UserPassw0rd1", 40101);
    login(user.getEmail(), newPassword);
    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", "Bearer " + tokenAfterUnban))
        .andExpect(status().isUnauthorized());
  }

  private User createUser(UserRole role, String password) {
    User created = new User();
    created.setUserAccount(UserAccountUtils.generate());
    created.setEmail("admin_it_" + UUID.randomUUID() + "@example.com");
    created.setNickname("集成测试用户");
    created.setPasswordHash(passwordEncoder.encode(password));
    created.setRole(role);
    created.setStatus(UserStatus.NORMAL);
    created.setIsDelete(DeletedFlag.NOT_DELETED);
    created.setTokenVersion(0);
    userMapper.insert(created);
    return created;
  }

  private String login(String email, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email":"%s","password":"%s"}
                        """
                            .formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
  }

  private void expectLogin(String email, String password, int code) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","password":"%s"}
                    """
                        .formatted(email, password)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(code));
  }

  private ResultActions changeStatus(String token, String account, UserStatus status)
      throws Exception {
    return mockMvc.perform(
        put("/api/v1/admin/users/" + account + "/status")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"status":"%s"}
                """
                    .formatted(status.name())));
  }
}
