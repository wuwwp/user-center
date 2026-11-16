package com.wwp.usercenter.service;

/** 邮件通道投递失败。调用方据此记录日志，但不得改变对外响应。 */
public class EmailDeliveryException extends RuntimeException {

  public EmailDeliveryException(String message, Throwable cause) {
    super(message, cause);
  }
}
