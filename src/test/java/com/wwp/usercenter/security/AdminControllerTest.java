package com.wwp.usercenter.security;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wwp.usercenter.config.SecurityConfig;
import com.wwp.usercenter.controller.AdminController;
import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.dto.AdminPasswordResetResult;
import com.wwp.usercenter.model.dto.PageResult;
import com.wwp.usercenter.model.dto.UserSummaryDTO;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.model.enums.DeletedFlag;
import com.wwp.usercenter.model.enums.UserRole;
import com.wwp.usercenter.model.enums.UserStatus;
import com.wwp.usercenter.service.AdminUserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AdminControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JwtTokenProvider jwtTokenProvider;
  @MockitoBean private UserMapper userMapper;
  @MockitoBean private AdminUserService adminUserService;

  @Test
  void should_return_401_when_not_authenticated() throws Exception {
    mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
  }

  @Test
  void should_return_403_when_user_accesses_admin_api() throws Exception {
    given(jwtTokenProvider.parse("user-token")).willReturn(new TokenPayload(2L, 0));
    given(userMapper.selectAuthStateById(2L)).willReturn(user(2L, UserRole.USER));

    mockMvc
        .perform(get("/api/v1/admin/users").header("Authorization", "Bearer user-token"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(40300));
  }

  @Test
  void should_return_user_page_for_admin() throws Exception {
    adminToken();
    given(adminUserService.listUsers(1, 10, null))
        .willReturn(
            new PageResult<>(
                List.of(
                    new UserSummaryDTO(
                        "u00000001",
                        "用户",
                        "a***@example.com",
                        UserRole.USER,
                        UserStatus.NORMAL,
                        false,
                        null,
                        null)),
                1,
                1,
                10,
                1));

    mockMvc
        .perform(get("/api/v1/admin/users").header("Authorization", "Bearer admin-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].userAccount").value("u00000001"))
        .andExpect(jsonPath("$.items[0].emailMasked").value("a***@example.com"))
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.totalPages").value(1));
  }

  @Test
  void should_reject_page_less_than_one() throws Exception {
    adminToken();

    mockMvc
        .perform(get("/api/v1/admin/users?page=0").header("Authorization", "Bearer admin-token"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(40000));
  }

  @Test
  void should_reject_size_over_limit() throws Exception {
    adminToken();

    mockMvc
        .perform(get("/api/v1/admin/users?size=101").header("Authorization", "Bearer admin-token"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(40000));
  }

  @Test
  void should_change_status_for_admin() throws Exception {
    adminToken();

    mockMvc
        .perform(
            put("/api/v1/admin/users/u00000001/status")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"BANNED\"}"))
        .andExpect(status().isOk());

    then(adminUserService).should().changeStatus(1L, "u00000001", UserStatus.BANNED);
  }

  @Test
  void should_reset_password_and_return_it_once() throws Exception {
    adminToken();
    given(adminUserService.resetPassword(1L, "u00000001"))
        .willReturn(new AdminPasswordResetResult("u00000001", "NewPassw0rd123!"));

    mockMvc
        .perform(
            post("/api/v1/admin/users/u00000001/password/reset")
                .header("Authorization", "Bearer admin-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userAccount").value("u00000001"))
        .andExpect(jsonPath("$.newPassword").value("NewPassw0rd123!"));
  }

  @Test
  void should_reject_unknown_status_value() throws Exception {
    adminToken();

    mockMvc
        .perform(
            put("/api/v1/admin/users/u00000001/status")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"UNKNOWN\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(40000));
  }

  private void adminToken() {
    given(jwtTokenProvider.parse("admin-token")).willReturn(new TokenPayload(1L, 0));
    given(userMapper.selectAuthStateById(1L)).willReturn(user(1L, UserRole.ADMIN));
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
