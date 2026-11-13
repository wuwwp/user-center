package com.wwp.usercenter.service;

import com.wwp.usercenter.model.dto.LoginResult;
import com.wwp.usercenter.model.param.LoginParam;

public interface AuthService {

  /** 邮箱密码登录，成功后签发 JWT；凭证错误统一抛 {@code INVALID_CREDENTIALS}。 */
  LoginResult login(LoginParam param);
}
