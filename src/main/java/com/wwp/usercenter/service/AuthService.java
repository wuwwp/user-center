package com.wwp.usercenter.service;

import com.wwp.usercenter.model.dto.LoginResult;
import com.wwp.usercenter.model.dto.RegisterResult;
import com.wwp.usercenter.model.param.LoginParam;
import com.wwp.usercenter.model.param.RegisterParam;
import com.wwp.usercenter.model.param.ResetPasswordParam;
import com.wwp.usercenter.model.param.SendEmailCodeParam;

public interface AuthService {

  /**
   * 邮箱密码登录，成功后签发 JWT；凭证错误统一抛 {@code INVALID_CREDENTIALS}， 账号或 IP 被锁定时抛 {@code LOGIN_LOCKED}。
   *
   * @param clientIp 由 {@code ClientIpResolver} 解析出的客户端 IP
   */
  LoginResult login(LoginParam param, String clientIp);

  /** 邮箱验证码注册，固定普通用户角色；弱密码返回 {@code PARAM_INVALID}。 */
  RegisterResult register(RegisterParam param);

  /**
   * 按场景发送验证码。无论邮箱是否注册均返回成功，避免用户枚举。
   *
   * @param clientIp 由 {@code ClientIpResolver} 解析出的客户端 IP
   */
  void sendEmailCode(SendEmailCodeParam param, String clientIp);

  /** 登出：自增令牌版本，当前用户所有令牌立即失效。 */
  void logout(Long userId);

  /** 邮箱验证码重置密码：成功后自增令牌版本。 */
  void resetPassword(ResetPasswordParam param);
}
