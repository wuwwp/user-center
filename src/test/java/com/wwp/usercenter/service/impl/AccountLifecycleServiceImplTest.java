package com.wwp.usercenter.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.wwp.usercenter.common.BusinessException;
import com.wwp.usercenter.common.ErrorCode;
import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.model.enums.DeletedFlag;
import com.wwp.usercenter.model.enums.EmailScene;
import com.wwp.usercenter.model.enums.UserRole;
import com.wwp.usercenter.model.enums.UserStatus;
import com.wwp.usercenter.model.param.DeleteAccountParam;
import com.wwp.usercenter.model.param.RecoverAccountParam;
import com.wwp.usercenter.service.LoginAttemptService;
import com.wwp.usercenter.service.PasswordPolicyService;
import com.wwp.usercenter.service.VerificationCodeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountLifecycleServiceImplTest {

  @Mock private UserMapper userMapper;
  @Mock private VerificationCodeService verificationCodeService;
  @Mock private PasswordPolicyService passwordPolicyService;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private LoginAttemptService loginAttemptService;
  @InjectMocks private AccountLifecycleServiceImpl service;

  @Test
  void should_delete_active_account_with_delete_code() {
    given(userMapper.selectLifecycleStateById(2L))
        .willReturn(user(UserStatus.NORMAL, DeletedFlag.NOT_DELETED, UserRole.USER));
    given(userMapper.softDeleteAccount(2L)).willReturn(1);

    service.deleteAccount(2L, new DeleteAccountParam("123456"));

    then(verificationCodeService)
        .should()
        .verifyAndConsume("alice@example.com", EmailScene.DELETE, "123456");
    then(userMapper).should().softDeleteAccount(2L);
  }

  @Test
  void should_treat_concurrent_delete_as_idempotent() {
    given(userMapper.selectLifecycleStateById(2L))
        .willReturn(user(UserStatus.NORMAL, DeletedFlag.NOT_DELETED, UserRole.USER));
    given(userMapper.softDeleteAccount(2L)).willReturn(0);

    service.deleteAccount(2L, new DeleteAccountParam("123456"));

    then(userMapper).should().softDeleteAccount(2L);
  }

  @Test
  void should_reject_admin_self_delete() {
    given(userMapper.selectLifecycleStateById(1L))
        .willReturn(user(UserStatus.NORMAL, DeletedFlag.NOT_DELETED, UserRole.ADMIN));

    assertThatThrownBy(() -> service.deleteAccount(1L, new DeleteAccountParam("123456")))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.FORBIDDEN);
    then(verificationCodeService).shouldHaveNoInteractions();
  }

  @Test
  void should_reject_banned_account_delete() {
    given(userMapper.selectLifecycleStateById(2L))
        .willReturn(user(UserStatus.BANNED, DeletedFlag.NOT_DELETED, UserRole.USER));

    assertThatThrownBy(() -> service.deleteAccount(2L, new DeleteAccountParam("123456")))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCOUNT_BANNED);
    then(verificationCodeService).shouldHaveNoInteractions();
  }

  @Test
  void should_reject_deleted_account_delete() {
    given(userMapper.selectLifecycleStateById(2L))
        .willReturn(user(UserStatus.NORMAL, DeletedFlag.DELETED, UserRole.USER));

    assertThatThrownBy(() -> service.deleteAccount(2L, new DeleteAccountParam("123456")))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  @Test
  void should_recover_deleted_account_and_clear_failures() {
    given(userMapper.selectRecoverStateByEmail("alice@example.com"))
        .willReturn(user(UserStatus.NORMAL, DeletedFlag.DELETED, UserRole.USER));
    given(passwordEncoder.encode("NewPassw0rd1")).willReturn("new-hash");
    given(userMapper.recoverAccount(2L, "new-hash")).willReturn(1);

    service.recoverAccount(new RecoverAccountParam("Alice@Example.com", "123456", "NewPassw0rd1"));

    then(passwordPolicyService).should().ensureNotWeak("NewPassw0rd1");
    then(verificationCodeService)
        .should()
        .verifyAndConsume("alice@example.com", EmailScene.RECOVER, "123456");
    then(userMapper).should().recoverAccount(2L, "new-hash");
    then(loginAttemptService).should().clearAccountFailures("alice@example.com");
  }

  @Test
  void should_reject_recover_when_account_is_not_deleted() {
    given(userMapper.selectRecoverStateByEmail("alice@example.com"))
        .willReturn(user(UserStatus.NORMAL, DeletedFlag.NOT_DELETED, UserRole.USER));

    assertThatThrownBy(() ->
            service.recoverAccount(
                new RecoverAccountParam("alice@example.com", "123456", "NewPassw0rd1")))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VERIFY_CODE_INVALID);
    then(passwordEncoder).should(never()).encode(anyString());
  }

  @Test
  void should_reject_recover_when_account_is_banned() {
    given(userMapper.selectRecoverStateByEmail("alice@example.com"))
        .willReturn(user(UserStatus.BANNED, DeletedFlag.DELETED, UserRole.USER));

    assertThatThrownBy(() ->
            service.recoverAccount(
                new RecoverAccountParam("alice@example.com", "123456", "NewPassw0rd1")))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCOUNT_BANNED);
  }

  @Test
  void should_reject_recover_when_update_lost_race() {
    given(userMapper.selectRecoverStateByEmail("alice@example.com"))
        .willReturn(user(UserStatus.NORMAL, DeletedFlag.DELETED, UserRole.USER));
    given(passwordEncoder.encode("NewPassw0rd1")).willReturn("new-hash");
    given(userMapper.recoverAccount(2L, "new-hash")).willReturn(0);

    assertThatThrownBy(() ->
            service.recoverAccount(
                new RecoverAccountParam("alice@example.com", "123456", "NewPassw0rd1")))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VERIFY_CODE_INVALID);
  }

  private static User user(UserStatus status, DeletedFlag deleted, UserRole role) {
    User user = new User();
    user.setId(2L);
    user.setEmail("alice@example.com");
    user.setRole(role);
    user.setStatus(status);
    user.setIsDelete(deleted);
    user.setTokenVersion(0);
    return user;
  }
}
