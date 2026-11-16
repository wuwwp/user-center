package com.wwp.usercenter.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wwp.usercenter.config.SecurityConfig;
import com.wwp.usercenter.controller.AuthController;
import com.wwp.usercenter.controller.UserController;
import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.dto.CurrentUserDTO;
import com.wwp.usercenter.model.dto.LoginResult;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.model.enums.DeletedFlag;
import com.wwp.usercenter.model.enums.UserRole;
import com.wwp.usercenter.model.enums.UserStatus;
import com.wwp.usercenter.model.param.LoginParam;
import com.wwp.usercenter.service.AuthService;
import com.wwp.usercenter.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {UserController.class, AuthController.class})
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class SecurityConfigTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JwtTokenProvider jwtTokenProvider;
  @MockitoBean private UserMapper userMapper;
  @MockitoBean private AuthService authService;
  @MockitoBean private UserService userService;

  @Test
  void should_return_401_when_not_authenticated() throws Exception {
    mockMvc
        .perform(get("/api/v1/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(40100));
  }

  @Test
  void should_return_current_user_when_token_valid() throws Exception {
    given(jwtTokenProvider.parse("valid-token")).willReturn(new TokenPayload(7L, 0));
    given(userMapper.selectAuthStateById(7L)).willReturn(user(7L, UserRole.ADMIN));
    given(userService.getCurrentUser(7L))
        .willReturn(
            new CurrentUserDTO("admin0001", "管理员", "admin@user-center.local", UserRole.ADMIN));

    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.userAccount").value("admin0001"));
  }

  @Test
  void should_return_403_when_user_accesses_admin_api() throws Exception {
    given(jwtTokenProvider.parse("user-token")).willReturn(new TokenPayload(8L, 0));
    given(userMapper.selectAuthStateById(8L)).willReturn(user(8L, UserRole.USER));

    mockMvc
        .perform(get("/api/v1/admin/users").header("Authorization", "Bearer user-token"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(40300));
  }

  @Test
  void should_return_400_when_login_param_invalid() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"short\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(40000));
  }

  @Test
  void should_return_401_when_logout_without_token() throws Exception {
    mockMvc
        .perform(post("/api/v1/auth/logout"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(40100));
  }

  @Test
  void should_logout_when_authenticated() throws Exception {
    given(jwtTokenProvider.parse("valid-token")).willReturn(new TokenPayload(7L, 0));
    given(userMapper.selectAuthStateById(7L)).willReturn(user(7L, UserRole.USER));

    mockMvc
        .perform(post("/api/v1/auth/logout").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));

    then(authService).should().logout(7L);
  }

  @Test
  void should_return_400_when_register_password_weak() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"alice@example.com\",\"code\":\"123456\",\"password\":\"onlyletters\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(40000));
  }

  @Test
  void should_return_400_when_register_code_not_six_digits() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"alice@example.com\",\"code\":\"12ab\",\"password\":\"Test12345678\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(40000));
  }

  @Test
  void should_return_400_when_email_scene_unknown() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/email/code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"alice@example.com\",\"scene\":\"unknown\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(40000));
  }

  @Test
  void should_return_401_when_change_password_without_token() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"OldPassw0rd\",\"newPassword\":\"NewPassw0rd\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(40100));
  }

  @Test
  void should_return_token_when_login_succeeds() throws Exception {
    given(authService.login(any(LoginParam.class)))
        .willReturn(new LoginResult("token-value", "Bearer", 7200));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@user-center.local\",\"password\":\"Test12345678\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.token").value("token-value"));
  }

  private static User user(long id, UserRole role) {
    User user = new User();
    user.setId(id);
    user.setUserAccount("u" + id);
    user.setRole(role);
    user.setStatus(UserStatus.NORMAL);
    user.setIsDelete(DeletedFlag.NOT_DELETED);
    user.setTokenVersion(0);
    return user;
  }
}
