package com.wwp.usercenter.model.param;

import com.wwp.usercenter.model.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

/** 管理端状态变更入参；枚举只包含 NORMAL / BANNED。 */
public record AdminUserStatusParam(@NotNull(message = "状态不能为空") UserStatus status) {}
