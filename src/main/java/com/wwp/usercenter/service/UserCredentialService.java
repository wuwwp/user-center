package com.wwp.usercenter.service;

/** 账号凭证写入的事务边界。方法保持短小，BCrypt 计算等耗时操作必须在调用前完成， 避免哈希计算占用数据库连接（见 docs/plan.md §8.2）。 */
public interface UserCredentialService {

  /** 仅更新最后登录时间。 */
  void recordLogin(Long userId);

  /** 更新密码并自增令牌版本，使该用户所有旧令牌立即失效。 */
  void updatePassword(Long userId, String passwordHash);

  /** 自增令牌版本，使该用户所有旧令牌立即失效（登出、封禁、角色变更等场景）。 */
  void revokeTokens(Long userId);
}
