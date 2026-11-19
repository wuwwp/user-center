package com.wwp.usercenter.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wwp.usercenter.common.constant.VerificationCodeConstants;
import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.model.enums.EmailScene;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

/** 注销与恢复闭环：DELETE 场景隔离、旧令牌失效、恢复后重新登录。 */
class AccountLifecycleIntegrationTest extends IntegrationTestSupport {

  @Autowired private UserMapper userMapper;
  @Autowired private PasswordEncoder passwordEncoder;

  private String email;

  @BeforeEach
  void setUp() {
    email = "lc_" + UUID.randomUUID() + "@example.com";
  }

  @Test
  void should_delete_and_recover_account() throws Exception {
    register("OldPassw0rd1");
    String token = login(email, "OldPassw0rd1");

    String deleteCode = requestCode(EmailScene.DELETE);
    attemptDelete(token, deleteCode).andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
    expectLogin(email, "OldPassw0rd1", 40104);

    String recoverCode = requestCode(EmailScene.RECOVER);
    attemptRecover(recoverCode, "NewPassw0rd1").andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());

    String tokenAfterRecover = login(email, "NewPassw0rd1");
    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", "Bearer " + tokenAfterRecover))
        .andExpect(status().isOk());

    User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
    assertThat(user.isDeleted()).isFalse();
  }

  @Test
  void should_not_reuse_reset_code_for_delete() throws Exception {
    register("OldPassw0rd1");
    String token = login(email, "OldPassw0rd1");

    String resetCode = requestCode(EmailScene.RESET);
    attemptDelete(token, resetCode)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(40002));

    login(email, "OldPassw0rd1");
  }

  @Test
  void should_reject_admin_self_delete() throws Exception {
    User admin = new User();
    admin.setUserAccount("admin_" + UUID.randomUUID().toString().substring(0, 8));
    admin.setEmail(email);
    admin.setPasswordHash(passwordEncoder.encode("AdminPassw0rd1"));
    admin.setRole(com.wwp.usercenter.model.enums.UserRole.ADMIN);
    admin.setStatus(com.wwp.usercenter.model.enums.UserStatus.NORMAL);
    admin.setIsDelete(com.wwp.usercenter.model.enums.DeletedFlag.NOT_DELETED);
    admin.setTokenVersion(0);
    userMapper.insert(admin);

    String token = login(email, "AdminPassw0rd1");
    redisTemplate
        .opsForValue()
        .set(
            VerificationCodeConstants.codeKey(EmailScene.DELETE.getCode(), email),
            "123456",
            VerificationCodeConstants.CODE_TTL);

    attemptDelete(token, "123456")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(40300));
  }

  private ResultActions attemptDelete(String token, String code) throws Exception {
    return mockMvc.perform(
        delete("/api/v1/users/me")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"code":"%s"}
                """
                    .formatted(code)));
  }

  private ResultActions attemptRecover(String code, String newPassword) throws Exception {
    return mockMvc.perform(
        post("/api/v1/users/me/recover")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"email":"%s","code":"%s","newPassword":"%s"}
                """
                    .formatted(email, code, newPassword)));
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
        .andExpect(status().isOk());
  }

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
}
