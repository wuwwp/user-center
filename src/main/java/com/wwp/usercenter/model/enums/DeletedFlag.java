package com.wwp.usercenter.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 逻辑注销标记。这里不使用 MyBatis-Plus 的全局逻辑删除，因为注销后仍需要读取该行以支持恢复流程。 */
@Getter
@RequiredArgsConstructor
public enum DeletedFlag {
  NOT_DELETED(0, "未注销"),
  DELETED(1, "已注销");

  @EnumValue private final int code;
  private final String description;
}
