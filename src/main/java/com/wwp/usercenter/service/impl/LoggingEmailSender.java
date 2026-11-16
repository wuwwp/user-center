package com.wwp.usercenter.service.impl;

import com.wwp.usercenter.model.enums.EmailScene;
import com.wwp.usercenter.service.EmailSender;
import com.wwp.usercenter.util.EmailUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 开发期邮件实现：把验证码输出到日志。通过 {@code user-center.mail.mode=log} 启用（默认）。
 *
 * <p>生产使用 {@code smtp} 模式，避免验证码落日志。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "user-center.mail.mode", havingValue = "log", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

  @Override
  public void sendVerificationCode(String email, EmailScene scene, String code) {
    log.info(
        "[dev-mail] verification code, scene={}, email={}, code={}",
        scene.getCode(),
        EmailUtils.mask(email),
        code);
  }
}
