package com.wwp.usercenter.service;

/** 弱密码字典校验；只用于注册、重置、改密，不用于登录。 */
public interface PasswordPolicyService {

  /**
   * 校验密码是否命中弱密码字典。
   *
   * @throws com.wwp.usercenter.common.BusinessException 命中时抛 {@code PARAM_INVALID}
   */
  void ensureNotWeak(String password);
}
