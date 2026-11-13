package com.wwp.usercenter.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 用户状态，数据库存 tinyint。封禁后不可登录，旧令牌立即失效。 */
@Getter
@RequiredArgsConstructor
public enum UserStatus {
  NORMAL(0, "正常"),
  BANNED(1, "封禁");

  @EnumValue private final int code;
  private final String description;
}
