package com.wwp.usercenter.service.impl;

import static com.wwp.usercenter.common.constant.LoginGuardConstants.ACCOUNT_FAIL_LIMIT;
import static com.wwp.usercenter.common.constant.LoginGuardConstants.IP_FAIL_LIMIT;
import static com.wwp.usercenter.common.constant.LoginGuardConstants.LOCK_TTL;
import static com.wwp.usercenter.common.constant.LoginGuardConstants.accountFailKey;
import static com.wwp.usercenter.common.constant.LoginGuardConstants.ipFailKey;

import com.wwp.usercenter.common.BusinessException;
import com.wwp.usercenter.common.ErrorCode;
import com.wwp.usercenter.service.LoginAttemptService;
import com.wwp.usercenter.util.EmailUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptServiceImpl implements LoginAttemptService {

  private final StringRedisTemplate redisTemplate;

  @Override
  public void ensureLoginAllowed(String normalizedEmail, String clientIp) {
    String accountKey = accountFailKey(normalizedEmail);
    String ipKey = ipFailKey(clientIp);
    if (currentCount(accountKey) >= ACCOUNT_FAIL_LIMIT) {
      log.warn("login rejected, account locked, email={}", EmailUtils.mask(normalizedEmail));
      throw new BusinessException(ErrorCode.LOGIN_LOCKED);
    }
    if (currentCount(ipKey) >= IP_FAIL_LIMIT) {
      log.warn("login rejected, ip locked, ip={}", clientIp);
      throw new BusinessException(ErrorCode.LOGIN_LOCKED);
    }
  }

  @Override
  public void recordFailure(String normalizedEmail, String clientIp) {
    incrementWithTtl(accountFailKey(normalizedEmail));
    incrementWithTtl(ipFailKey(clientIp));
  }

  @Override
  public void clearAccountFailures(String normalizedEmail) {
    redisTemplate.delete(accountFailKey(normalizedEmail));
  }

  /** 首次 INCR 返回 1 时设置 TTL；后续失败不会延长锁定窗口。 */
  private void incrementWithTtl(String key) {
    Long count = redisTemplate.opsForValue().increment(key);
    if (count != null && count == 1L) {
      redisTemplate.expire(key, LOCK_TTL);
    }
  }

  private long currentCount(String key) {
    String value = redisTemplate.opsForValue().get(key);
    if (value == null) {
      return 0L;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException exception) {
      // 键被外部写入非法值时按未锁定处理，避免把用户永久挡在门外
      log.warn("invalid login failure counter, treat as zero");
      return 0L;
    }
  }
}
