package com.wwp.usercenter.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.wwp.usercenter.common.BusinessException;
import com.wwp.usercenter.common.ErrorCode;
import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.model.enums.DeletedFlag;
import com.wwp.usercenter.model.enums.UserRole;
import com.wwp.usercenter.model.enums.UserStatus;
import com.wwp.usercenter.model.param.ChangePasswordParam;
import com.wwp.usercenter.service.PasswordPolicyService;
import com.wwp.usercenter.service.UserCredentialService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

  private static final String OLD_PASSWORD = "OldPassw0rd";
  private static final String NEW_PASSWORD = "NewPassw0rd";
  private static final String OLD_HASH = "old-hash";
  private static final String NEW_HASH = "new-hash";

  @Mock private UserMapper userMapper;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private UserCredentialService userCredentialService;
  @Mock private PasswordPolicyService passwordPolicyService;
  @InjectMocks private UserServiceImpl userService;

  @Test
  void should_update_password_when_old_password_matches() {
    given(userMapper.selectById(9L)).willReturn(activeUser());
    given(passwordEncoder.matches(OLD_PASSWORD, OLD_HASH)).willReturn(true);
    given(passwordEncoder.encode(NEW_PASSWORD)).willReturn(NEW_HASH);

    userService.changePassword(9L, new ChangePasswordParam(OLD_PASSWORD, NEW_PASSWORD));

    then(userCredentialService).should().updatePassword(9L, NEW_HASH);
  }

  @Test
  void should_reject_when_old_password_wrong() {
    given(userMapper.selectById(9L)).willReturn(activeUser());
    given(passwordEncoder.matches(OLD_PASSWORD, OLD_HASH)).willReturn(false);

    assertThatThrownBy(() ->
            userService.changePassword(9L, new ChangePasswordParam(OLD_PASSWORD, NEW_PASSWORD)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    then(userCredentialService).shouldHaveNoInteractions();
  }

  @Test
  void should_reject_when_new_password_same_as_old() {
    given(userMapper.selectById(9L)).willReturn(activeUser());
    given(passwordEncoder.matches(OLD_PASSWORD, OLD_HASH)).willReturn(true);

    assertThatThrownBy(() ->
            userService.changePassword(9L, new ChangePasswordParam(OLD_PASSWORD, OLD_PASSWORD)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PARAM_INVALID);
    then(userCredentialService).shouldHaveNoInteractions();
  }

  @Test
  void should_reject_when_new_password_weak() {
    given(userMapper.selectById(9L)).willReturn(activeUser());
    given(passwordEncoder.matches(OLD_PASSWORD, OLD_HASH)).willReturn(true);
    willThrow(new BusinessException(ErrorCode.PARAM_INVALID))
        .given(passwordPolicyService)
        .ensureNotWeak(NEW_PASSWORD);

    assertThatThrownBy(() ->
            userService.changePassword(9L, new ChangePasswordParam(OLD_PASSWORD, NEW_PASSWORD)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PARAM_INVALID);
    then(userCredentialService).shouldHaveNoInteractions();
  }

  @Test
  void should_reject_when_account_deleted() {
    User user = activeUser();
    user.setIsDelete(DeletedFlag.DELETED);
    given(userMapper.selectById(9L)).willReturn(user);

    assertThatThrownBy(() ->
            userService.changePassword(9L, new ChangePasswordParam(OLD_PASSWORD, NEW_PASSWORD)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    then(passwordEncoder).shouldHaveNoInteractions();
  }

  @Test
  void should_return_current_user_without_password_hash() {
    given(userMapper.selectCurrentUserById(9L)).willReturn(activeUser());

    var dto = userService.getCurrentUser(9L);

    assertThat(dto.userAccount()).isEqualTo("u9a8b7c6d");
    assertThat(dto.email()).isEqualTo("alice@example.com");
    then(userMapper).should(never()).selectById(any());
    then(passwordEncoder).shouldHaveNoInteractions();
    then(userCredentialService).shouldHaveNoInteractions();
  }

  @Test
  void should_return_not_found_when_current_user_absent() {
    given(userMapper.selectCurrentUserById(9L)).willReturn(null);

    assertThatThrownBy(() -> userService.getCurrentUser(9L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  private static User activeUser() {
    User user = new User();
    user.setId(9L);
    user.setUserAccount("u9a8b7c6d");
    user.setEmail("alice@example.com");
    user.setPasswordHash(OLD_HASH);
    user.setRole(UserRole.USER);
    user.setStatus(UserStatus.NORMAL);
    user.setIsDelete(DeletedFlag.NOT_DELETED);
    user.setTokenVersion(0);
    return user;
  }
}
