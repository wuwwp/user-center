package com.wwp.usercenter.security;

/** 从 JWT 中解析出的、本服务需要的声明。角色不放在令牌里，以数据库为准。 */
public record TokenPayload(long userId, int tokenVersion) {}
