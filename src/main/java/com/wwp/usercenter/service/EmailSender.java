package com.wwp.usercenter.service;

import com.wwp.usercenter.model.enums.EmailScene;

/** 邮件发送抽象。按 {@code user-center.mail.mode} 选择日志或 SMTP 实现，业务层不感知具体通道。 */
public interface EmailSender {

  /**
   * 投递验证码邮件。
   *
   * @throws EmailDeliveryException 通道不可用或邮件构建失败时抛出，调用方负责记录且不得改变对外响应
   */
  void sendVerificationCode(String email, EmailScene scene, String code);
}
