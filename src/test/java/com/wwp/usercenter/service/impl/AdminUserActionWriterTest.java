package com.wwp.usercenter.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.wwp.usercenter.common.BusinessException;
import com.wwp.usercenter.common.ErrorCode;
import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.model.enums.AuditAction;
import com.wwp.usercenter.model.enums.DeletedFlag;
import com.wwp.usercenter.model.enums.UserStatus;
import com.wwp.usercenter.service.AuditLogService;
import com.wwp.usercenter.service.UserCredentialService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUserActionWriterTest {

  @Mock private UserMapper userMapper;
  @Mock private UserCredentialService userCredentialService;
  @Mock private AuditLogService auditLogService;
  @InjectMocks private AdminUserActionWriter writer;

  @Test
  void should_update_status_and_record_audit() {
    User target = user(UserStatus.NORMAL);
    given(userMapper.updateStatusAndIncrementTokenVersion(2L, UserStatus.BANNED)).willReturn(1);

    writer.changeStatus(1L, target, UserStatus.BANNED);

    then(auditLogService)
        .should()
        .record(eq(1L), eq(2L), eq(AuditAction.USER_BAN), any(String.class));
  }

  @Test
  void should_record_unban_action() {
    User target = user(UserStatus.BANNED);
    given(userMapper.updateStatusAndIncrementTokenVersion(2L, UserStatus.NORMAL)).willReturn(1);

    writer.changeStatus(1L, target, UserStatus.NORMAL);

    then(auditLogService)
        .should()
        .record(eq(1L), eq(2L), eq(AuditAction.USER_UNBAN), any(String.class));
  }

  @Test
  void should_throw_and_skip_audit_when_status_update_lost() {
    given(userMapper.updateStatusAndIncrementTokenVersion(2L, UserStatus.BANNED)).willReturn(0);

    assertThatThrownBy(() -> writer.changeStatus(1L, user(UserStatus.NORMAL), UserStatus.BANNED))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    then(auditLogService).should(never()).record(any(), any(), any(), any());
  }

  @Test
  void should_update_password_and_record_audit() {
    User target = user(UserStatus.NORMAL);

    writer.resetPassword(1L, target, "new-hash");

    then(userCredentialService).should().updatePassword(2L, "new-hash");
    then(auditLogService)
        .should()
        .record(eq(1L), eq(2L), eq(AuditAction.PASSWORD_RESET), any(String.class));
  }

  private static User user(UserStatus status) {
    User user = new User();
    user.setId(2L);
    user.setStatus(status);
    user.setIsDelete(DeletedFlag.NOT_DELETED);
    return user;
  }
}
