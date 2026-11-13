package com.wwp.usercenter.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 用户角色，数据库存 tinyint。管理员只能由初始化脚本或运维 SQL 产生。 */
@Getter
@RequiredArgsConstructor
public enum UserRole {
  USER(0, "普通用户"),
  ADMIN(1, "管理员");

  @EnumValue private final int code;
  private final String description;
}
