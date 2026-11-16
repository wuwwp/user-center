package com.wwp.usercenter.service.impl;

import com.wwp.usercenter.common.constant.VerificationCodeConstants;
import com.wwp.usercenter.model.enums.EmailScene;
import com.wwp.usercenter.service.EmailSender;
import com.wwp.usercenter.util.EmailUtils;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * SMTP 邮件实现，通过 {@code user-center.mail.mode=smtp} 启用。
 *
 * <p>只发送纯文本验证码，不包含链接；发送失败由调用方记录，不能改变对外响应。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "user-center.mail.mode", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

  private static final String SUBJECT_PREFIX = "【用户中心】";

  private final JavaMailSender mailSender;
  private final String from;
  private final String fromName;

  public SmtpEmailSender(
      JavaMailSender mailSender,
      @Value("${user-center.mail.from:}") String from,
      @Value("${user-center.mail.from-name:用户中心}") String fromName) {
    if (from == null || from.isBlank()) {
      throw new IllegalStateException("user-center.mail.from 不能为空");
    }
    this.mailSender = mailSender;
    this.from = from;
    this.fromName = fromName;
  }

  @Override
  public void sendVerificationCode(String email, EmailScene scene, String code) {
    MimeMessage message = mailSender.createMimeMessage();
    try {
      MimeMessageHelper helper =
          new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
      helper.setFrom(from, fromName);
      helper.setTo(email);
      helper.setSubject(SUBJECT_PREFIX + scene.getDescription() + "验证码");
      helper.setText(buildText(scene, code), false);
      mailSender.send(message);
      log.info(
          "verification code mail sent, scene={}, email={}",
          scene.getCode(),
          EmailUtils.mask(email));
    } catch (MessagingException | UnsupportedEncodingException exception) {
      throw new MailPreparationException("构建验证码邮件失败", exception);
    }
  }

  private static String buildText(EmailScene scene, String code) {
    return "您的验证码是："
        + code
        + "\n用途："
        + scene.getDescription()
        + "\n有效期："
        + VerificationCodeConstants.CODE_TTL.toMinutes()
        + " 分钟\n如果不是您本人操作，请忽略本邮件。";
  }
}
