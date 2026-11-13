package com.wwp.usercenter.service;

import com.wwp.usercenter.model.dto.CurrentUserDTO;

public interface UserService {

  /** 返回当前用户脱敏后的资料，不含密码哈希。 */
  CurrentUserDTO getCurrentUser(Long userId);
}
