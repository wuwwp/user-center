package com.wwp.usercenter.service.impl;

import com.wwp.usercenter.model.enums.EmailScene;
import com.wwp.usercenter.service.EmailDeliveryException;
import com.wwp.usercenter.service.EmailDispatchService;
import com.wwp.usercenter.service.EmailSender;
import com.wwp.usercenter.util.EmailUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDispatchServiceImpl implements EmailDispatchService {

  private final EmailSender emailSender;

  @Async("emailExecutor")
  @Override
  public void dispatch(String email, EmailScene scene, String code) {
    try {
      emailSender.sendVerificationCode(email, scene, code);
    } catch (EmailDeliveryException exception) {
      // 异步线程里没有调用方可以接收异常，只能记录；对外响应已经返回成功
      log.error(
          "send verification code failed, scene={}, email={}",
          scene.getCode(),
          EmailUtils.mask(email),
          exception);
    }
  }
}
