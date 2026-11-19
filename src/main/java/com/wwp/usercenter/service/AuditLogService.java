package com.wwp.usercenter.service;

import com.wwp.usercenter.model.enums.AuditAction;

/** 管理员操作审计写入；必须与业务写入处于同一事务。 */
public interface AuditLogService {

  void record(Long actorId, Long targetUserId, AuditAction action, String detail);
}
