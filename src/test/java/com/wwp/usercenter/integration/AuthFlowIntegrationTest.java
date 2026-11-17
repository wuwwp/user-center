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
import org.springframework.test.web.servlet.ResultActions;
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

  @Test
  void should_lock_account_after_five_failed_logins() throws Exception {
    register("ItPassw0rd1");

    for (int i = 0; i < 5; i++) {
      expectLogin("ItWrongPass1", 40101);
    }

    // 第 6 次即使密码正确也被锁定，响应不区分账号是否存在
    expectLogin("ItPassw0rd1", 40102);
  }

  @Test
  void should_clear_account_failures_after_successful_login() throws Exception {
    register("ItPassw0rd1");
    for (int i = 0; i < 4; i++) {
      expectLogin("ItWrongPass1", 40101);
    }
    login("ItPassw0rd1");

    for (int i = 0; i < 4; i++) {
      expectLogin("ItWrongPass1", 40101);
    }
  }

  @Test
  void should_lock_ip_after_many_failures_across_accounts() throws Exception {
    for (int i = 0; i < 50; i++) {
      String other = "ip_" + UUID.randomUUID() + "@example.com";
      attemptLogin(other, "ItWrongPass1")
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(40101));
    }

    attemptLogin(email, "ItWrongPass1")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(40102));
  }

  @Test
  void should_reject_weak_password_without_consuming_code() throws Exception {
    String registerCode = requestCode(EmailScene.REGISTER);
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","code":"%s","password":"Password1"}
                    """
                        .formatted(email, registerCode)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(40000));

    // 弱密码在校验验证码之前被拒绝，验证码仍可用于正常注册
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","code":"%s","password":"ItPassw0rd1"}
                    """
                        .formatted(email, registerCode)))
        .andExpect(status().isOk());

    String resetCode = requestCode(EmailScene.RESET);
    mockMvc
        .perform(
            post("/api/v1/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","code":"%s","newPassword":"P@ssw0rd"}
                    """
                        .formatted(email, resetCode)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(40000));

    mockMvc
        .perform(
            post("/api/v1/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","code":"%s","newPassword":"ItPassw0rd2"}
                    """
                        .formatted(email, resetCode)))
        .andExpect(status().isOk());
  }

  @Test
  void should_invalidate_code_after_five_wrong_guesses() throws Exception {
    register("ItPassw0rd1");
    String resetCode = requestCode(EmailScene.RESET);
    String wrongCode = "000000".equals(resetCode) ? "111111" : "000000";

    for (int i = 0; i < 4; i++) {
      expectResetCode(wrongCode, 40001);
    }
    expectResetCode(wrongCode, 42900);

    // 达到上限后验证码被作废，正确验证码也只能得到“已过期”
    expectResetCode(resetCode, 40002);
  }

  @Test
  void should_reject_token_after_version_or_status_change() throws Exception {
    register("ItPassw0rd1");
    String token = login("ItPassw0rd1");
    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
    userMapper.incrementTokenVersion(user.getId());
    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());

    String tokenAfterChange = login("ItPassw0rd1");
    User banned = new User();
    banned.setId(user.getId());
    banned.setStatus(UserStatus.BANNED);
    userMapper.updateById(banned);
    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", "Bearer " + tokenAfterChange))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void should_return_identical_login_error_for_unknown_account_and_wrong_password()
      throws Exception {
    MvcResult unknown =
        attemptLogin("unknown_" + UUID.randomUUID() + "@example.com", "ItWrongPass1")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(40101))
            .andReturn();

    register("ItPassw0rd1");
    MvcResult wrongPassword =
        attemptLogin(email, "ItWrongPass1")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(40101))
            .andReturn();

    String unknownMessage = JsonPath.read(unknown.getResponse().getContentAsString(), "$.message");
    String wrongMessage =
        JsonPath.read(wrongPassword.getResponse().getContentAsString(), "$.message");
    assertThat(unknownMessage).isEqualTo(wrongMessage);
  }

  private void register(String password) throws Exception {
    String code = requestCode(EmailScene.REGISTER);
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","code":"%s","password":"%s"}
                    """
                        .formatted(email, code, password)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));
  }

  private ResultActions attemptLogin(String targetEmail, String password) throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"email":"%s","password":"%s"}
                """
                    .formatted(targetEmail, password)));
  }

  private void expectLogin(String password, int code) throws Exception {
    attemptLogin(email, password)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(code));
  }

  private void expectResetCode(String code, int expectedCode) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","code":"%s","newPassword":"ItPassw0rd2"}
                    """
                        .formatted(email, code)))
        .andExpect(jsonPath("$.code").value(expectedCode));
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
