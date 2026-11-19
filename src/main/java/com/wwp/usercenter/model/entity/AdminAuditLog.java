package com.wwp.usercenter.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wwp.usercenter.model.enums.AuditAction;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 管理员操作审计记录，只追加，不通过业务接口更新或删除。 */
@Getter
@Setter
@TableName("admin_audit_log")
public class AdminAuditLog {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long actorId;
  private String actorAccount;
  private Long targetUserId;
  private String targetAccount;
  private AuditAction action;
  private String detail;
  private LocalDateTime createdAt;
}
