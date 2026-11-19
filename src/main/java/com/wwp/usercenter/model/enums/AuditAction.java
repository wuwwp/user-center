package com.wwp.usercenter.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 管理员审计动作。数据库存小写下划线字符串，便于后续按动作检索。 */
@Getter
@RequiredArgsConstructor
public enum AuditAction {
  USER_BAN("user_ban", "封禁用户"),
  USER_UNBAN("user_unban", "解封用户"),
  PASSWORD_RESET("password_reset", "重置密码");

  @EnumValue private final String code;
  private final String description;
}
