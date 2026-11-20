package com.wwp.usercenter.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.wwp.usercenter.mapper.AdminAuditLogMapper;
import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.entity.AdminAuditLog;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.model.enums.AuditAction;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceImplTest {

  @Mock private AdminAuditLogMapper auditLogMapper;
  @Mock private UserMapper userMapper;
  @InjectMocks private AuditLogServiceImpl service;

  @Test
  void should_write_actor_and_target_snapshots() {
    given(userMapper.selectAccountsByIds(List.of(1L, 2L)))
        .willReturn(List.of(account(1L, "admin0001"), account(2L, "u00000002")));

    service.record(1L, 2L, AuditAction.USER_BAN, "oldStatus=NORMAL,newStatus=BANNED");

    ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
    then(auditLogMapper).should().insert(captor.capture());
    AdminAuditLog log = captor.getValue();
    assertThat(log.getActorId()).isEqualTo(1L);
    assertThat(log.getActorAccount()).isEqualTo("admin0001");
    assertThat(log.getTargetUserId()).isEqualTo(2L);
    assertThat(log.getTargetAccount()).isEqualTo("u00000002");
    assertThat(log.getAction()).isEqualTo(AuditAction.USER_BAN);
    assertThat(log.getDetail()).isEqualTo("oldStatus=NORMAL,newStatus=BANNED");
  }

  @Test
  void should_use_unknown_when_account_snapshot_is_missing() {
    given(userMapper.selectAccountsByIds(List.of(1L, 2L))).willReturn(List.of());

    service.record(1L, 2L, AuditAction.PASSWORD_RESET, "reason=admin_reset");

    ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
    then(auditLogMapper).should().insert(captor.capture());
    assertThat(captor.getValue().getActorAccount()).isEqualTo("unknown");
    assertThat(captor.getValue().getTargetAccount()).isEqualTo("unknown");
  }

  private static User account(Long id, String account) {
    User user = new User();
    user.setId(id);
    user.setUserAccount(account);
    return user;
  }
}
