package com.wwp.usercenter.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 业务错误码。按模块分段：400xx 参数、401xx 认证、403xx 权限、409xx 冲突、429xx 限流、500xx 系统。 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  SUCCESS(0, HttpStatus.OK, "ok"),

  PARAM_INVALID(40000, HttpStatus.BAD_REQUEST, "参数校验失败"),
  VERIFY_CODE_INVALID(40001, HttpStatus.BAD_REQUEST, "验证码错误"),
  VERIFY_CODE_EXPIRED(40002, HttpStatus.BAD_REQUEST, "验证码已过期"),

  UNAUTHORIZED(40100, HttpStatus.UNAUTHORIZED, "未登录或令牌无效"),
  INVALID_CREDENTIALS(40101, HttpStatus.UNAUTHORIZED, "邮箱或密码错误"),
  LOGIN_LOCKED(40102, HttpStatus.UNAUTHORIZED, "登录失败次数过多，请稍后再试"),
  ACCOUNT_BANNED(40103, HttpStatus.UNAUTHORIZED, "账号已封禁"),
  ACCOUNT_DELETED(40104, HttpStatus.UNAUTHORIZED, "账号已注销"),

  FORBIDDEN(40300, HttpStatus.FORBIDDEN, "无权限"),
  NOT_FOUND(40400, HttpStatus.NOT_FOUND, "资源不存在"),
  METHOD_NOT_ALLOWED(40500, HttpStatus.METHOD_NOT_ALLOWED, "请求方法不支持"),
  NOT_ACCEPTABLE(40600, HttpStatus.NOT_ACCEPTABLE, "无法生成可接受的响应"),
  CONFLICT(40900, HttpStatus.CONFLICT, "资源冲突"),
  UNSUPPORTED_MEDIA_TYPE(41500, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的媒体类型"),
  TOO_MANY_REQUESTS(42900, HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁"),
  SYSTEM_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "系统错误");

  private final int code;
  private final HttpStatus httpStatus;
  private final String message;
}
