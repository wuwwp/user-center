package com.wwp.usercenter.service;

import com.wwp.usercenter.model.enums.EmailScene;

/** 异步投递验证码，避免 SMTP 延迟占用请求线程。 */
public interface EmailDispatchService {

  /** 提交投递任务；投递失败只记录日志，不改变调用方响应。 */
  void dispatch(String email, EmailScene scene, String code);
}
