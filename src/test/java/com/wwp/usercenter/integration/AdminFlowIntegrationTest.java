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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** 管理端闭环：管理员登录、分页、封禁/解封、重置密码与普通用户越权。 */
class AdminFlowIntegrationTest extends IntegrationTestSupport {

  @Autowired private UserMapper userMapper;
  @Autowired private PasswordEncoder passwordEncoder;

  private User admin;
  private User user;

  @BeforeEach
  void setUp() {
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
        .andExpect(jsonPath("$.items[0].emailMasked", containsString("***")));

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

    expectLogin(user.getEmail(), "UserPassw0rd1", 40101);
    login(user.getEmail(), newPassword);
    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", "Bearer " + tokenAfterUnban))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void should_filter_and_order_admin_list_and_hide_deleted_users() throws Exception {
    User deleted =
        createUser(UserRole.USER, "DeletedPassw0rd1", UserStatus.NORMAL, DeletedFlag.DELETED);
    User banned =
        createUser(UserRole.USER, "BannedPassw0rd1", UserStatus.BANNED, DeletedFlag.NOT_DELETED);
    String adminToken = login(admin.getEmail(), "AdminPassw0rd1");

    mockMvc
        .perform(
            get("/api/v1/admin/users?status=BANNED")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].userAccount").value(banned.getUserAccount()))
        .andExpect(jsonPath("$.items[0].status").value("BANNED"));

    MvcResult all =
        mockMvc
            .perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
    List<String> accounts =
        JsonPath.read(all.getResponse().getContentAsString(), "$.items[*].userAccount");
    assertThat(accounts).doesNotContain(deleted.getUserAccount());
    assertThat(accounts.get(0)).isEqualTo(banned.getUserAccount());

    changeStatus(adminToken, deleted.getUserAccount(), UserStatus.BANNED)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(40400));
    changeStatus(adminToken, "u00000000", UserStatus.NORMAL).andExpect(status().isNotFound());
  }

  @Test
  void should_search_admin_list_by_account_and_email() throws Exception {
    User target = createUser(UserRole.USER, "SearchPassw0rd1");
    String adminToken = login(admin.getEmail(), "AdminPassw0rd1");

    mockMvc
        .perform(
            get("/api/v1/admin/users")
                .param("keyword", target.getUserAccount())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].userAccount").value(target.getUserAccount()));

    mockMvc
        .perform(
            get("/api/v1/admin/users")
                .param("keyword", target.getEmail())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].userAccount").value(target.getUserAccount()));
  }

  private User createUser(UserRole role, String password) {
    return createUser(role, password, UserStatus.NORMAL, DeletedFlag.NOT_DELETED);
  }

  private User createUser(UserRole role, String password, UserStatus status, DeletedFlag deleted) {
    User created = new User();
    created.setUserAccount(UserAccountUtils.generate());
    created.setEmail("admin_it_" + UUID.randomUUID() + "@example.com");
    created.setNickname("集成测试用户");
    created.setPasswordHash(passwordEncoder.encode(password));
    created.setRole(role);
    created.setStatus(status);
    created.setIsDelete(deleted);
    created.setTokenVersion(0);
    userMapper.insert(created);
    return created;
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
