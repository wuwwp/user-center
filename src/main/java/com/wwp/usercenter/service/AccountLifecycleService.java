package com.wwp.usercenter.service;

import com.wwp.usercenter.model.param.DeleteAccountParam;
import com.wwp.usercenter.model.param.RecoverAccountParam;

/** 账号注销与恢复。注销是逻辑删除，恢复要求邮箱验证码和新密码。 */
public interface AccountLifecycleService {

  /** 当前用户注销；管理员不能自助注销。 */
  void deleteAccount(Long userId, DeleteAccountParam param);

  /** 已注销账号通过 RECOVER 验证码恢复；封禁账号不可恢复。 */
  void recoverAccount(RecoverAccountParam param);
}
