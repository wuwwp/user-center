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
import com.wwp.usercenter.util.RedisCounterUtils;
import java.util.List;
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
    List<String> counts =
        redisTemplate
            .opsForValue()
            .multiGet(List.of(accountFailKey(normalizedEmail), ipFailKey(clientIp)));
    if (parseCount(counts, 0) >= ACCOUNT_FAIL_LIMIT) {
      log.warn("login rejected, account locked, email={}", EmailUtils.mask(normalizedEmail));
      throw new BusinessException(ErrorCode.LOGIN_LOCKED);
    }
    if (parseCount(counts, 1) >= IP_FAIL_LIMIT) {
      log.warn("login rejected, ip locked, ip={}", clientIp);
      throw new BusinessException(ErrorCode.LOGIN_LOCKED);
    }
  }

  @Override
  public void recordFailure(String normalizedEmail, String clientIp) {
    RedisCounterUtils.increment(redisTemplate, accountFailKey(normalizedEmail), LOCK_TTL);
    RedisCounterUtils.increment(redisTemplate, ipFailKey(clientIp), LOCK_TTL);
  }

  @Override
  public void clearAccountFailures(String normalizedEmail) {
    redisTemplate.delete(accountFailKey(normalizedEmail));
  }

  private static long parseCount(List<String> counts, int index) {
    String value = counts == null || index >= counts.size() ? null : counts.get(index);
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
