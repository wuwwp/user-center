package com.wwp.usercenter.model.dto;

/** 管理员重置密码结果；明文密码只返回这一次，不落日志。 */
public record AdminPasswordResetResult(String userAccount, String newPassword) {}
