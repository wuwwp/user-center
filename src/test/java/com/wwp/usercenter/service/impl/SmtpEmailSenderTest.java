package com.wwp.usercenter.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.wwp.usercenter.config.MailProperties;
import com.wwp.usercenter.model.enums.EmailScene;
import com.wwp.usercenter.service.EmailDeliveryException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderTest {

  @Mock private JavaMailSender mailSender;

  private SmtpEmailSender sender;

  @BeforeEach
  void setUp() {
    sender = new SmtpEmailSender(mailSender, new MailProperties("no-reply@example.com", "用户中心"));
  }

  @Test
  void should_send_plain_text_code_mail() throws Exception {
    MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
    given(mailSender.createMimeMessage()).willReturn(message);

    sender.sendVerificationCode("alice@example.com", EmailScene.REGISTER, "123456");

    then(mailSender).should().send(message);
    assertThat(MimeUtility.decodeText(message.getSubject())).isEqualTo("【用户中心】注册验证码");
    assertThat(((InternetAddress) message.getFrom()[0]).getAddress())
        .isEqualTo("no-reply@example.com");
    assertThat(message.getAllRecipients()[0].toString()).isEqualTo("alice@example.com");
    assertThat(message.getContent().toString()).contains("123456").contains("5 分钟");
  }

  @Test
  void should_wrap_transport_failure() throws Exception {
    MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
    given(mailSender.createMimeMessage()).willReturn(message);
    willThrow(new MailSendException("smtp down")).given(mailSender).send(message);

    assertThatThrownBy(
            () -> sender.sendVerificationCode("alice@example.com", EmailScene.REGISTER, "123456"))
        .isInstanceOf(EmailDeliveryException.class);
  }

  @Test
  void should_reject_when_from_is_missing() {
    assertThatThrownBy(() -> new SmtpEmailSender(mailSender, new MailProperties(" ", "用户中心")))
        .isInstanceOf(IllegalStateException.class);
  }
}
