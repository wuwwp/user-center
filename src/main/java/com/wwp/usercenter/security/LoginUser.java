package com.wwp.usercenter.security;

/** 已认证请求的安全上下文主体。角色与权限已写入 Authentication，不在这里重复保存。 */
public record LoginUser(Long id) {}
