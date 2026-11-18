package com.wwp.usercenter.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.wwp.usercenter.common.BusinessException;
import com.wwp.usercenter.common.ErrorCode;
import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.dto.LoginResult;
import com.wwp.usercenter.model.dto.RegisterResult;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.model.enums.DeletedFlag;
import com.wwp.usercenter.model.enums.EmailScene;
import com.wwp.usercenter.model.enums.UserRole;
import com.wwp.usercenter.model.enums.UserStatus;
import com.wwp.usercenter.model.param.LoginParam;
import com.wwp.usercenter.model.param.RegisterParam;
import com.wwp.usercenter.model.param.ResetPasswordParam;
import com.wwp.usercenter.model.param.SendEmailCodeParam;
import com.wwp.usercenter.security.JwtTokenProvider;
import com.wwp.usercenter.service.EmailDispatchService;
import com.wwp.usercenter.service.LoginAttemptService;
import com.wwp.usercenter.service.PasswordPolicyService;
import com.wwp.usercenter.service.UserCredentialService;
import com.wwp.usercenter.service.VerificationCodeService;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

  private static final String EMAIL = "alice@example.com";
  private static final String PASSWORD = "Passw0rd!";
  private static final String OLD_HASH = "stored-hash";
  private static final String NEW_HASH = "new-hash";
  private static final String CLIENT_IP = "203.0.113.7";

  @Mock private UserMapper userMapper;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private VerificationCodeService verificationCodeService;
  @Mock private EmailDispatchService emailDispatchService;
  @Mock private UserCredentialService userCredentialService;
  @Mock private LoginAttemptService loginAttemptService;
  @Mock private PasswordPolicyService passwordPolicyService;

  private AuthServiceImpl authService;

  @BeforeEach
  void setUp() {
    given(passwordEncoder.encode(anyString())).willReturn(NEW_HASH);
    authService =
        new AuthServiceImpl(
            userMapper,
            passwordEncoder,
            jwtTokenProvider,
            verificationCodeService,
            emailDispatchService,
            userCredentialService,
            loginAttemptService,
            passwordPolicyService);
  }

  @Test
  void should_login_and_record_login_when_credentials_valid() {
    User user = activeUser();
    given(userMapper.selectOne(any())).willReturn(user);
    given(passwordEncoder.matches(PASSWORD, OLD_HASH)).willReturn(true);
    given(jwtTokenProvider.createToken(user)).willReturn("jwt");
    given(jwtTokenProvider.getExpiration()).willReturn(Duration.ofHours(2));

    LoginResult result =
        authService.login(new LoginParam(" Alice@Example.com ", PASSWORD), CLIENT_IP);

    assertThat(result.token()).isEqualTo("jwt");
    assertThat(result.tokenType()).isEqualTo("Bearer");
    assertThat(result.expiresIn()).isEqualTo(7200);
    then(loginAttemptService).should().clearAccountFailures(EMAIL);
    then(userCredentialService).should().recordLogin(user.getId());
    then(passwordPolicyService).shouldHaveNoInteractions();
  }

  @Test
  void should_reject_login_when_password_wrong() {
    given(userMapper.selectOne(any())).willReturn(activeUser());
    given(passwordEncoder.matches(PASSWORD, OLD_HASH)).willReturn(false);

    assertThatThrownBy(() -> authService.login(new LoginParam(EMAIL, PASSWORD), CLIENT_IP))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    then(loginAttemptService).should().recordFailure(EMAIL, CLIENT_IP);
    then(userCredentialService).shouldHaveNoInteractions();
  }

  @Test
  void should_still_hash_when_account_absent_to_prevent_enumeration() {
    given(userMapper.selectOne(any())).willReturn(null);

    assertThatThrownBy(() -> authService.login(new LoginParam(EMAIL, PASSWORD), CLIENT_IP))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    then(passwordEncoder).should().matches(eq(PASSWORD), anyString());
    then(loginAttemptService).should().recordFailure(EMAIL, CLIENT_IP);
  }

  @Test
  void should_reject_login_when_account_deleted() {
    User user = activeUser();
    user.setIsDelete(DeletedFlag.DELETED);
    given(userMapper.selectOne(any())).willReturn(user);
    given(passwordEncoder.matches(PASSWORD, OLD_HASH)).willReturn(true);

    assertThatThrownBy(() -> authService.login(new LoginParam(EMAIL, PASSWORD), CLIENT_IP))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCOUNT_DELETED);
  }

  @Test
  void should_reject_login_when_account_banned() {
    User user = activeUser();
    user.setStatus(UserStatus.BANNED);
    given(userMapper.selectOne(any())).willReturn(user);
    given(passwordEncoder.matches(PASSWORD, OLD_HASH)).willReturn(true);

    assertThatThrownBy(() -> authService.login(new LoginParam(EMAIL, PASSWORD), CLIENT_IP))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCOUNT_BANNED);
  }

  @Test
  void should_register_with_normal_role_and_consume_code() {
    given(userMapper.selectOne(any())).willReturn(null);

    RegisterResult result = authService.register(new RegisterParam(EMAIL, "123456", PASSWORD));

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    then(userMapper).should().insert(captor.capture());
    User inserted = captor.getValue();
    assertThat(inserted.getEmail()).isEqualTo(EMAIL);
    assertThat(inserted.getUserAccount()).startsWith("u").hasSize(9);
    assertThat(inserted.getRole()).isEqualTo(UserRole.USER);
    assertThat(inserted.getStatus()).isEqualTo(UserStatus.NORMAL);
    assertThat(inserted.getIsDelete()).isEqualTo(DeletedFlag.NOT_DELETED);
    assertThat(inserted.getTokenVersion()).isZero();
    assertThat(inserted.getPasswordHash()).isEqualTo(NEW_HASH);
    assertThat(result.userAccount()).isEqualTo(inserted.getUserAccount());
    then(verificationCodeService).should().verifyAndConsume(EMAIL, EmailScene.REGISTER, "123456");
  }

  @Test
  void should_reject_register_when_email_exists() {
    given(userMapper.selectOne(any())).willReturn(activeUser());

    assertThatThrownBy(() -> authService.register(new RegisterParam(EMAIL, "123456", PASSWORD)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.CONFLICT);
    then(verificationCodeService).shouldHaveNoInteractions();
    then(userMapper).should(never()).insert(any(User.class));
  }

  @Test
  void should_guide_to_recover_when_registering_deleted_email() {
    User user = activeUser();
    user.setIsDelete(DeletedFlag.DELETED);
    given(userMapper.selectOne(any())).willReturn(user);

    assertThatThrownBy(() -> authService.register(new RegisterParam(EMAIL, "123456", PASSWORD)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("恢复");
  }

  @Test
  void should_reject_register_when_unique_key_conflicts_on_insert() {
    given(userMapper.selectOne(any())).willReturn(null);
    given(userMapper.insert(any(User.class))).willThrow(new DuplicateKeyException("uk_email"));

    assertThatThrownBy(() -> authService.register(new RegisterParam(EMAIL, "123456", PASSWORD)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.CONFLICT);
  }

  @Test
  void should_send_code_when_reset_scene_and_account_active() {
    given(userMapper.selectOne(any())).willReturn(activeUser());
    given(verificationCodeService.issue(EMAIL, EmailScene.RESET, CLIENT_IP)).willReturn("123456");

    authService.sendEmailCode(new SendEmailCodeParam(EMAIL, EmailScene.RESET), CLIENT_IP);

    then(emailDispatchService).should().dispatch(EMAIL, EmailScene.RESET, "123456");
  }

  @Test
  void should_not_send_reset_code_when_account_banned() {
    User user = activeUser();
    user.setStatus(UserStatus.BANNED);
    given(userMapper.selectOne(any())).willReturn(user);
    given(verificationCodeService.issue(EMAIL, EmailScene.RESET, CLIENT_IP)).willReturn("123456");

    authService.sendEmailCode(new SendEmailCodeParam(EMAIL, EmailScene.RESET), CLIENT_IP);

    then(emailDispatchService).shouldHaveNoInteractions();
  }

  @Test
  void should_reject_reset_when_account_banned() {
    User user = activeUser();
    user.setStatus(UserStatus.BANNED);
    given(userMapper.selectOne(any())).willReturn(user);

    assertThatThrownBy(
            () -> authService.resetPassword(new ResetPasswordParam(EMAIL, "654321", PASSWORD)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCOUNT_BANNED);
    then(userCredentialService).shouldHaveNoInteractions();
  }

  @Test
  void should_not_send_code_but_return_success_when_account_absent() {
    given(userMapper.selectOne(any())).willReturn(null);
    given(verificationCodeService.issue(EMAIL, EmailScene.RESET, CLIENT_IP)).willReturn("123456");

    authService.sendEmailCode(new SendEmailCodeParam(EMAIL, EmailScene.RESET), CLIENT_IP);

    then(emailDispatchService).shouldHaveNoInteractions();
  }

  @Test
  void should_not_send_register_code_when_email_already_registered() {
    given(userMapper.selectOne(any())).willReturn(activeUser());
    given(verificationCodeService.issue(EMAIL, EmailScene.REGISTER, CLIENT_IP))
        .willReturn("123456");

    authService.sendEmailCode(new SendEmailCodeParam(EMAIL, EmailScene.REGISTER), CLIENT_IP);

    then(emailDispatchService).shouldHaveNoInteractions();
  }

  @Test
  void should_revoke_all_tokens_when_logout() {
    authService.logout(9L);

    then(userCredentialService).should().revokeTokens(9L);
  }

  @Test
  void should_reset_password_and_bump_version_when_code_valid() {
    given(userMapper.selectOne(any())).willReturn(activeUser());

    authService.resetPassword(new ResetPasswordParam(EMAIL, "654321", PASSWORD));

    then(verificationCodeService).should().verifyAndConsume(EMAIL, EmailScene.RESET, "654321");
    then(userCredentialService).should().updatePassword(9L, NEW_HASH);
  }

  @Test
  void should_reject_reset_when_account_deleted() {
    User user = activeUser();
    user.setIsDelete(DeletedFlag.DELETED);
    given(userMapper.selectOne(any())).willReturn(user);

    assertThatThrownBy(
            () -> authService.resetPassword(new ResetPasswordParam(EMAIL, "654321", PASSWORD)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCOUNT_DELETED);
    then(userCredentialService).shouldHaveNoInteractions();
  }

  @Test
  void should_reject_login_before_password_check_when_locked() {
    willThrow(new BusinessException(ErrorCode.LOGIN_LOCKED))
        .given(loginAttemptService)
        .ensureLoginAllowed(EMAIL, CLIENT_IP);

    assertThatThrownBy(() -> authService.login(new LoginParam(EMAIL, PASSWORD), CLIENT_IP))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.LOGIN_LOCKED);
    then(userMapper).shouldHaveNoInteractions();
    then(passwordEncoder).should(never()).matches(anyString(), anyString());
  }

  @Test
  void should_reject_register_when_password_weak_without_consuming_code() {
    given(userMapper.selectOne(any())).willReturn(null);
    willThrow(new BusinessException(ErrorCode.PARAM_INVALID))
        .given(passwordPolicyService)
        .ensureNotWeak(PASSWORD);

    assertThatThrownBy(() -> authService.register(new RegisterParam(EMAIL, "123456", PASSWORD)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PARAM_INVALID);
    then(verificationCodeService).shouldHaveNoInteractions();
    then(userMapper).should(never()).insert(any(User.class));
  }

  @Test
  void should_reject_reset_when_password_weak_without_consuming_code() {
    willThrow(new BusinessException(ErrorCode.PARAM_INVALID))
        .given(passwordPolicyService)
        .ensureNotWeak(PASSWORD);

    assertThatThrownBy(
            () -> authService.resetPassword(new ResetPasswordParam(EMAIL, "654321", PASSWORD)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PARAM_INVALID);
    then(verificationCodeService).shouldHaveNoInteractions();
    then(userCredentialService).shouldHaveNoInteractions();
  }

  private static User activeUser() {
    User user = new User();
    user.setId(9L);
    user.setUserAccount("u9a8b7c6d");
    user.setEmail(EMAIL);
    user.setPasswordHash(OLD_HASH);
    user.setRole(UserRole.USER);
    user.setStatus(UserStatus.NORMAL);
    user.setIsDelete(DeletedFlag.NOT_DELETED);
    user.setTokenVersion(2);
    return user;
  }
}
