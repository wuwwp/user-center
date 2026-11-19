package com.wwp.usercenter.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wwp.usercenter.config.SecurityConfig;
import com.wwp.usercenter.controller.UserController;
import com.wwp.usercenter.mapper.AdminAuditLogMapper;
import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.model.enums.DeletedFlag;
import com.wwp.usercenter.model.enums.UserRole;
import com.wwp.usercenter.model.enums.UserStatus;
import com.wwp.usercenter.model.param.DeleteAccountParam;
import com.wwp.usercenter.model.param.RecoverAccountParam;
import com.wwp.usercenter.service.AccountLifecycleService;
import com.wwp.usercenter.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class UserControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JwtTokenProvider jwtTokenProvider;
  @MockitoBean private UserMapper userMapper;
  @MockitoBean private AdminAuditLogMapper auditLogMapper;
  @MockitoBean private UserService userService;
  @MockitoBean private AccountLifecycleService accountLifecycleService;

  @Test
  void should_return_401_when_deleting_without_token() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"123456\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void should_delete_current_account() throws Exception {
    userToken();

    mockMvc
        .perform(
            delete("/api/v1/users/me")
                .header("Authorization", "Bearer user-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"123456\"}"))
        .andExpect(status().isOk());

    then(accountLifecycleService).should().deleteAccount(1L, new DeleteAccountParam("123456"));
  }

  @Test
  void should_reject_invalid_delete_code() throws Exception {
    userToken();

    mockMvc
        .perform(
            delete("/api/v1/users/me")
                .header("Authorization", "Bearer user-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"abc\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(40000));
  }

  @Test
  void should_recover_without_token() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/users/me/recover")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"alice@example.com","code":"123456","newPassword":"NewPassw0rd1"}
                    """))
        .andExpect(status().isOk());

    then(accountLifecycleService).should().recoverAccount(any(RecoverAccountParam.class));
  }

  private void userToken() {
    given(jwtTokenProvider.parse("user-token")).willReturn(new TokenPayload(1L, 0));
    given(userMapper.selectAuthStateById(1L)).willReturn(user());
  }

  private static User user() {
    User user = new User();
    user.setId(1L);
    user.setRole(UserRole.USER);
    user.setStatus(UserStatus.NORMAL);
    user.setIsDelete(DeletedFlag.NOT_DELETED);
    user.setTokenVersion(0);
    return user;
  }
}
