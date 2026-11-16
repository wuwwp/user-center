package com.wwp.usercenter.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jayway.jsonpath.JsonPath;
import com.wwp.usercenter.common.constant.VerificationCodeConstants;
import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.model.enums.EmailScene;
import com.wwp.usercenter.model.enums.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 真实 MySQL/Redis 下的账号闭环：注册、登录、改密、重置、登出、封禁后拒绝重置。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
          .withDatabaseName("usercenter")
          .withInitScript("db/init.sql");

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("spring.data.redis.password", () -> "");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private StringRedisTemplate redisTemplate;
  @Autowired private UserMapper userMapper;

  private String email;

  @BeforeEach
  void setUp() {
    email = "it_" + UUID.randomUUID() + "@example.com";
    redisTemplate.execute(
        (RedisCallback<Void>)
            connection -> {
              connection.serverCommands().flushDb();
              return null;
            });
  }

  @Test
  void should_complete_account_lifecycle_and_reject_banned_reset() throws Exception {
    String registerCode = requestCode(EmailScene.REGISTER);
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","code":"%s","password":"ItPassw0rd1"}
                    """
                        .formatted(email, registerCode)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));

    String token = login("ItPassw0rd1");
    mockMvc
        .perform(
            put("/api/v1/users/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"oldPassword":"ItPassw0rd1","newPassword":"ItPassw0rd2"}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());

    String tokenAfterChange = login("ItPassw0rd2");
    mockMvc
        .perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + tokenAfterChange))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", "Bearer " + tokenAfterChange))
        .andExpect(status().isUnauthorized());

    String resetCode = requestCode(EmailScene.RESET);
    mockMvc
        .perform(
            post("/api/v1/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","code":"%s","newPassword":"ItPassw0rd3"}
                    """
                        .formatted(email, resetCode)))
        .andExpect(status().isOk());
    login("ItPassw0rd3");

    User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
    User banned = new User();
    banned.setId(user.getId());
    banned.setStatus(UserStatus.BANNED);
    userMapper.updateById(banned);

    String bannedCode = requestCode(EmailScene.RESET);
    mockMvc
        .perform(
            post("/api/v1/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","code":"%s","newPassword":"ItPassw0rd4"}
                    """
                        .formatted(email, bannedCode)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(40103));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","password":"ItPassw0rd3"}
                    """
                        .formatted(email)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(40103));
  }

  /** 测试里跳过 60 秒冷却；冷却本身由 VerificationCodeServiceImplTest 覆盖。 */
  private String requestCode(EmailScene scene) throws Exception {
    redisTemplate.delete(VerificationCodeConstants.cooldownKey(email));
    mockMvc
        .perform(
            post("/api/v1/auth/email/code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","scene":"%s"}
                    """
                        .formatted(email, scene.getCode())))
        .andExpect(status().isOk());
    String code =
        redisTemplate.opsForValue().get(VerificationCodeConstants.codeKey(scene.getCode(), email));
    assertThat(code).isNotBlank();
    return code;
  }

  private String login(String password) throws Exception {
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
    return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
  }
}
