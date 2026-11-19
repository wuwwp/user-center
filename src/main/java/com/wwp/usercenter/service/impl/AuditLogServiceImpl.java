package com.wwp.usercenter.service.impl;

import com.wwp.usercenter.mapper.AdminAuditLogMapper;
import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.entity.AdminAuditLog;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.model.enums.AuditAction;
import com.wwp.usercenter.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

  private final AdminAuditLogMapper auditLogMapper;
  private final UserMapper userMapper;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void record(Long actorId, Long targetUserId, AuditAction action, String detail) {
    User actor = userMapper.selectAccountById(actorId);
    User target = userMapper.selectAccountById(targetUserId);

    AdminAuditLog log = new AdminAuditLog();
    log.setActorId(actorId);
    log.setActorAccount(actor == null ? "unknown" : actor.getUserAccount());
    log.setTargetUserId(targetUserId);
    log.setTargetAccount(target == null ? "unknown" : target.getUserAccount());
    log.setAction(action);
    log.setDetail(detail);
    auditLogMapper.insert(log);
  }
}
