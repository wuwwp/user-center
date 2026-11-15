package com.wwp.usercenter.service.impl;

import com.wwp.usercenter.model.enums.EmailScene;
import com.wwp.usercenter.service.EmailSender;
import com.wwp.usercenter.util.EmailUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 开发期邮件实现：把验证码输出到日志。生产环境需接入 SMTP 并关闭 {@code user-center.mail.log-code}。
 *
 * <p>验证码属于敏感信息，仅在显式开启日志输出时记录，且邮箱做掩码处理。
 */
@Slf4j
@Service
public class LoggingEmailSender implements EmailSender {

  private final boolean logCode;

  public LoggingEmailSender(@Value("${user-center.mail.log-code:true}") boolean logCode) {
    this.logCode = logCode;
  }

  @Override
  public void sendVerificationCode(String email, EmailScene scene, String code) {
    if (logCode) {
      log.info(
          "[dev-mail] verification code, scene={}, email={}, code={}",
          scene.getCode(),
          EmailUtils.mask(email),
          code);
      return;
    }
    log.warn(
        "mail transport is not configured, verification code dropped, scene={}", scene.getCode());
  }
}
