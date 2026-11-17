package com.wwp.usercenter.service;

import com.wwp.usercenter.model.enums.EmailScene;

/** 邮箱验证码的生成、限发与一次性校验。 */
public interface VerificationCodeService {

  /**
   * 生成并写入验证码，同时执行同邮箱冷却、同邮箱每日上限与同 IP 每日上限检查。
   *
   * @param clientIp 由 {@code ClientIpResolver} 解析出的客户端 IP
   * @return 验证码原文，由调用方决定是否真实投递
   * @throws com.wwp.usercenter.common.BusinessException 触发限发时抛出 {@code TOO_MANY_REQUESTS}
   */
  String issue(String email, EmailScene scene, String clientIp);

  /**
   * 校验并消费验证码，场景不匹配或已过期视为过期。
   *
   * @throws com.wwp.usercenter.common.BusinessException 不存在或已过期抛 {@code VERIFY_CODE_EXPIRED}，内容不符抛
   *     {@code VERIFY_CODE_INVALID}，错误次数达到上限抛 {@code TOO_MANY_REQUESTS}
   */
  void verifyAndConsume(String email, EmailScene scene, String code);
}
