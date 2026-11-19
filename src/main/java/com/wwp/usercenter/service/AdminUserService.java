package com.wwp.usercenter.service;

import com.wwp.usercenter.model.dto.AdminPasswordResetResult;
import com.wwp.usercenter.model.dto.PageResult;
import com.wwp.usercenter.model.dto.UserSummaryDTO;
import com.wwp.usercenter.model.enums.UserStatus;

/** 管理端用户查询与状态操作。 */
public interface AdminUserService {

  /** 分页查询未注销用户；status 为空时不过滤。 */
  PageResult<UserSummaryDTO> listUsers(int page, int size, UserStatus status);

  /**
   * 封禁或解封用户，自增 token_version 使旧令牌立即失效。
   *
   * @param actorId 当前管理员 ID，用于禁止自我封禁
   */
  void changeStatus(Long actorId, String userAccount, UserStatus status);

  /** 生成一次性随机密码并更新；旧令牌立即失效。 */
  AdminPasswordResetResult resetPassword(Long actorId, String userAccount);
}
