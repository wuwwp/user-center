package com.wwp.usercenter.service;

import com.wwp.usercenter.model.enums.EmailScene;

/** 邮件发送抽象。当前仅日志实现，接入真实 SMTP 时替换实现即可，业务层不需要改动。 */
public interface EmailSender {

  void sendVerificationCode(String email, EmailScene scene, String code);
}
