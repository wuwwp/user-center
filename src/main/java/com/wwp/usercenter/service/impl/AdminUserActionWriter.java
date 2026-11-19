package com.wwp.usercenter.service.impl;

import com.wwp.usercenter.common.BusinessException;
import com.wwp.usercenter.common.ErrorCode;
import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.model.enums.AuditAction;
import com.wwp.usercenter.model.enums.UserStatus;
import com.wwp.usercenter.service.AuditLogService;
import com.wwp.usercenter.service.UserCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理端状态写入与审计的短事务边界；密码哈希等耗时操作在调用前完成。 */
@Service
@RequiredArgsConstructor
public class AdminUserActionWriter {

  private final UserMapper userMapper;
  private final UserCredentialService userCredentialService;
  private final AuditLogService auditLogService;

  @Transactional
  public void changeStatus(Long actorId, User target, UserStatus newStatus) {
    int updated = userMapper.updateStatusAndIncrementTokenVersion(target.getId(), newStatus);
    if (updated == 0) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    AuditAction action =
        newStatus == UserStatus.BANNED ? AuditAction.USER_BAN : AuditAction.USER_UNBAN;
    auditLogService.record(
        actorId,
        target.getId(),
        action,
        "oldStatus=" + target.getStatus() + ",newStatus=" + newStatus);
  }

  @Transactional
  public void resetPassword(Long actorId, User target, String passwordHash) {
    userCredentialService.updatePassword(target.getId(), passwordHash);
    auditLogService.record(
        actorId, target.getId(), AuditAction.PASSWORD_RESET, "reason=admin_reset");
  }
}
