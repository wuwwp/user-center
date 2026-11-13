package com.wwp.usercenter.security;

import com.wwp.usercenter.model.enums.UserRole;

/** 已认证请求的安全上下文主体。 */
public record LoginUser(Long id, String userAccount, UserRole role) {}
