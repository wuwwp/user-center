package com.wwp.usercenter.service;

import com.wwp.usercenter.model.dto.CurrentUserDTO;
import com.wwp.usercenter.model.param.ChangePasswordParam;

public interface UserService {

  /** 返回当前用户脱敏后的资料，不含密码哈希。 */
  CurrentUserDTO getCurrentUser(Long userId);

  /** 校验原密码后修改密码，成功后该用户所有令牌失效。 */
  void changePassword(Long userId, ChangePasswordParam param);
}
