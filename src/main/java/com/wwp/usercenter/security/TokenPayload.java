package com.wwp.usercenter.security;

/** 从 JWT 中解析出的、本服务需要的声明。 */
public record TokenPayload(long userId, int role, int tokenVersion) {}
