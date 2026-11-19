package com.wwp.usercenter.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wwp.usercenter.common.BusinessException;
import com.wwp.usercenter.common.ErrorCode;
import com.wwp.usercenter.common.constant.ValidationConstants;
import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.dto.AdminPasswordResetResult;
import com.wwp.usercenter.model.dto.PageResult;
import com.wwp.usercenter.model.dto.UserSummaryDTO;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.model.enums.DeletedFlag;
import com.wwp.usercenter.model.enums.UserRole;
import com.wwp.usercenter.model.enums.UserStatus;
import com.wwp.usercenter.service.UserCredentialService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

  @Mock private UserMapper userMapper;
  @Mock private UserCredentialService userCredentialService;
  @Mock private PasswordEncoder passwordEncoder;
  @InjectMocks private AdminUserServiceImpl service;

  @Test
  void should_return_masked_page_of_users() {
    User user = user(2L, "u00000002", UserStatus.NORMAL, DeletedFlag.NOT_DELETED);
    given(userMapper.selectAdminUserPage(any(), any()))
        .willAnswer(invocation -> {
          Page<User> page = invocation.getArgument(0);
          page.setRecords(List.of(user));
          page.setTotal(1);
          return page;
        });

    PageResult<UserSummaryDTO> result = service.listUsers(1, 10, null);

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.page()).isEqualTo(1);
    assertThat(result.size()).isEqualTo(10);
    assertThat(result.totalPages()).isEqualTo(1);
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).userAccount()).isEqualTo("u00000002");
    assertThat(result.items().get(0).emailMasked()).isEqualTo("a***@example.com");
  }

  @Test
  void should_ban_user_and_bump_token_version() {
    given(userMapper.selectAdminStateByAccount("u00000002"))
        .willReturn(user(2L, "u00000002", UserStatus.NORMAL, DeletedFlag.NOT_DELETED));
    given(userMapper.updateStatusAndIncrementTokenVersion(2L, UserStatus.BANNED)).willReturn(1);

    service.changeStatus(1L, "u00000002", UserStatus.BANNED);

    then(userMapper).should().updateStatusAndIncrementTokenVersion(2L, UserStatus.BANNED);
  }

  @Test
  void should_reject_banning_self() {
    given(userMapper.selectAdminStateByAccount("admin0001"))
        .willReturn(user(1L, "admin0001", UserStatus.NORMAL, DeletedFlag.NOT_DELETED));

    assertThatThrownBy(() -> service.changeStatus(1L, "admin0001", UserStatus.BANNED))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PARAM_INVALID);
    then(userMapper).should(never()).updateStatusAndIncrementTokenVersion(any(), any());
  }

  @Test
  void should_be_idempotent_when_status_unchanged() {
    given(userMapper.selectAdminStateByAccount("u00000002"))
        .willReturn(user(2L, "u00000002", UserStatus.BANNED, DeletedFlag.NOT_DELETED));

    service.changeStatus(1L, "u00000002", UserStatus.BANNED);

    then(userMapper).should(never()).updateStatusAndIncrementTokenVersion(any(), any());
  }

  @Test
  void should_return_not_found_when_target_deleted() {
    given(userMapper.selectAdminStateByAccount("u00000002"))
        .willReturn(user(2L, "u00000002", UserStatus.NORMAL, DeletedFlag.DELETED));

    assertThatThrownBy(() -> service.changeStatus(1L, "u00000002", UserStatus.BANNED))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  @Test
  void should_return_not_found_when_target_absent() {
    given(userMapper.selectAdminStateByAccount("missing")).willReturn(null);

    assertThatThrownBy(() -> service.changeStatus(1L, "missing", UserStatus.BANNED))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  @Test
  void should_reset_password_with_policy_compliant_random_value() {
    given(userMapper.selectAdminStateByAccount("u00000002"))
        .willReturn(user(2L, "u00000002", UserStatus.NORMAL, DeletedFlag.NOT_DELETED));
    given(passwordEncoder.encode(anyString())).willReturn("new-hash");

    AdminPasswordResetResult result = service.resetPassword(1L, "u00000002");

    assertThat(result.userAccount()).isEqualTo("u00000002");
    assertThat(result.newPassword()).hasSize(16).matches(ValidationConstants.PASSWORD_REGEXP);
    then(userCredentialService).should().updatePassword(2L, "new-hash");
  }

  @Test
  void should_not_reset_password_when_target_deleted() {
    given(userMapper.selectAdminStateByAccount("u00000002"))
        .willReturn(user(2L, "u00000002", UserStatus.NORMAL, DeletedFlag.DELETED));

    assertThatThrownBy(() -> service.resetPassword(1L, "u00000002"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    then(userCredentialService).shouldHaveNoInteractions();
  }

  private static User user(long id, String account, UserStatus status, DeletedFlag deleted) {
    User user = new User();
    user.setId(id);
    user.setUserAccount(account);
    user.setNickname("用户");
    user.setEmail("alice@example.com");
    user.setRole(UserRole.USER);
    user.setStatus(status);
    user.setIsDelete(deleted);
    user.setTokenVersion(0);
    user.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
    return user;
  }
}
