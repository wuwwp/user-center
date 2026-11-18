package com.wwp.usercenter.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.wwp.usercenter.model.enums.EmailScene;
import com.wwp.usercenter.service.EmailDeliveryException;
import com.wwp.usercenter.service.EmailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailDispatchServiceImplTest {

  private static final String EMAIL = "alice@example.com";

  @Mock private EmailSender emailSender;
  @InjectMocks private EmailDispatchServiceImpl service;

  @Test
  void should_delegate_to_email_sender() {
    service.dispatch(EMAIL, EmailScene.RESET, "123456");

    then(emailSender).should().sendVerificationCode(EMAIL, EmailScene.RESET, "123456");
  }

  @Test
  void should_swallow_delivery_exception() {
    willThrow(new EmailDeliveryException("smtp down", null))
        .given(emailSender)
        .sendVerificationCode(EMAIL, EmailScene.RESET, "123456");

    assertThatCode(() -> service.dispatch(EMAIL, EmailScene.RESET, "123456"))
        .doesNotThrowAnyException();
  }
}
