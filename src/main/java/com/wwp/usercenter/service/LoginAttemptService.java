package com.wwp.usercenter.service;

/** 登录失败计数与锁定：账号、客户端 IP 双维度，状态存放在 Redis。 */
public interface LoginAttemptService {

  /**
   * 登录前检查锁定状态。
   *
   * @param normalizedEmail 已规范化的邮箱
   * @param clientIp 由 {@code ClientIpResolver} 解析出的客户端 IP
   * @throws com.wwp.usercenter.common.BusinessException 任一维度达到阈值时抛 {@code LOGIN_LOCKED}
   */
  void ensureLoginAllowed(String normalizedEmail, String clientIp);

  /** 记录一次凭证失败；不存在账号与密码错误走同一条计数路径。 */
  void recordFailure(String normalizedEmail, String clientIp);

  /** 登录成功后清除账号维度计数；IP 维度保留，减少对同一出口其他用户的影响。 */
  void clearAccountFailures(String normalizedEmail);
}
