package com.wwp.usercenter.service.impl;

import static com.wwp.usercenter.common.constant.VerificationCodeConstants.CODE_BOUND;
import static com.wwp.usercenter.common.constant.VerificationCodeConstants.CODE_FORMAT;
import static com.wwp.usercenter.common.constant.VerificationCodeConstants.CODE_TTL;
import static com.wwp.usercenter.common.constant.VerificationCodeConstants.COOLDOWN;
import static com.wwp.usercenter.common.constant.VerificationCodeConstants.DAILY_COUNTER_TTL;
import static com.wwp.usercenter.common.constant.VerificationCodeConstants.DAILY_LIMIT;
import static com.wwp.usercenter.common.constant.VerificationCodeConstants.FAIL_LIMIT;
import static com.wwp.usercenter.common.constant.VerificationCodeConstants.IP_DAILY_LIMIT;
import static com.wwp.usercenter.common.constant.VerificationCodeConstants.codeKey;
import static com.wwp.usercenter.common.constant.VerificationCodeConstants.cooldownKey;
import static com.wwp.usercenter.common.constant.VerificationCodeConstants.dailyKey;
import static com.wwp.usercenter.common.constant.VerificationCodeConstants.failKey;
import static com.wwp.usercenter.common.constant.VerificationCodeConstants.ipDailyKey;

import com.wwp.usercenter.common.BusinessException;
import com.wwp.usercenter.common.ErrorCode;
import com.wwp.usercenter.model.enums.EmailScene;
import com.wwp.usercenter.service.VerificationCodeService;
import com.wwp.usercenter.util.EmailUtils;
import com.wwp.usercenter.util.RedisCounterUtils;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeServiceImpl implements VerificationCodeService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

  private final StringRedisTemplate redisTemplate;
  private final Clock clock;

  @Override
  public String issue(String email, EmailScene scene, String clientIp) {
    String normalized = EmailUtils.normalize(email);
    Boolean acquired =
        redisTemplate.opsForValue().setIfAbsent(cooldownKey(normalized), "1", COOLDOWN);
    if (!Boolean.TRUE.equals(acquired)) {
      throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "验证码发送过于频繁，请稍后再试");
    }

    String date = LocalDate.now(clock).format(DATE_FORMAT);
    incrementWithLimit(dailyKey(normalized, date), DAILY_LIMIT, "今日验证码发送次数已达上限");
    incrementWithLimit(ipDailyKey(clientIp, date), IP_DAILY_LIMIT, "该网络今日验证码发送次数已达上限");

    String code = String.format(CODE_FORMAT, RANDOM.nextInt(CODE_BOUND));
    redisTemplate.opsForValue().set(codeKey(scene.getCode(), normalized), code, CODE_TTL);
    // 重新发码时重置猜测计数，避免上一次的失败次数影响新验证码
    redisTemplate.delete(failKey(scene.getCode(), normalized));
    return code;
  }

  @Override
  public void verifyAndConsume(String email, EmailScene scene, String code) {
    String normalized = EmailUtils.normalize(email);
    String key = codeKey(scene.getCode(), normalized);
    String stored = redisTemplate.opsForValue().get(key);
    if (stored == null) {
      throw new BusinessException(ErrorCode.VERIFY_CODE_EXPIRED);
    }
    if (!stored.equals(code == null ? null : code.trim())) {
      registerFailure(scene, normalized, key);
      throw new BusinessException(ErrorCode.VERIFY_CODE_INVALID);
    }
    redisTemplate.delete(List.of(key, failKey(scene.getCode(), normalized)));
  }

  /** 累计错误次数；达到上限时作废验证码与计数，用户必须重新发码。 */
  private void registerFailure(EmailScene scene, String normalizedEmail, String codeKey) {
    String key = failKey(scene.getCode(), normalizedEmail);
    Long count = RedisCounterUtils.increment(redisTemplate, key, CODE_TTL);
    if (count == null) {
      return;
    }
    if (count >= FAIL_LIMIT) {
      redisTemplate.delete(List.of(codeKey, key));
      throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "验证码错误次数过多，请重新获取验证码");
    }
  }

  /** 每日计数首次递增时设置 TTL，超过上限直接拒绝。 */
  private void incrementWithLimit(String key, int limit, String message) {
    Long count = RedisCounterUtils.increment(redisTemplate, key, DAILY_COUNTER_TTL);
    if (count == null) {
      throw new BusinessException(ErrorCode.SYSTEM_ERROR);
    }
    if (count > limit) {
      throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, message);
    }
  }
}
