package com.wwp.usercenter.model.dto;

/** 注册成功后返回对外用户标识，前端可直接提示并跳转登录。 */
public record RegisterResult(String userAccount) {}
