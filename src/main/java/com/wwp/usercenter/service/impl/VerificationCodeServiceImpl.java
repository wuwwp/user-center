package com.wwp.usercenter.service.impl;

import static com.wwp.usercenter.common.constant.VerificationCodeConstants.CODE_BOUND;
import static com.wwp.usercenter.common.constant.VerificationCodeConstants.CODE_FORMAT;
import static com.wwp.usercenter.common.constant.VerificationCodeConstants.CODE_TTL;
import static com.wwp.usercenter.common.constant.VerificationCodeConstants.COOLDOWN;
import static com.wwp.usercenter.common.constant.VerificationCodeConstants.DAILY_COUNTER_TTL;
import static com.wwp.usercenter.common.constant.VerificationCodeConstants.DAILY_LIMIT;
import static com.wwp.usercenter.common.constant.VerificationCodeConstants.codeKey;
import static com.wwp.usercenter.common.constant.VerificationCodeConstants.cooldownKey;
import static com.wwp.usercenter.common.constant.VerificationCodeConstants.dailyKey;

import com.wwp.usercenter.common.BusinessException;
import com.wwp.usercenter.common.ErrorCode;
import com.wwp.usercenter.model.enums.EmailScene;
import com.wwp.usercenter.service.VerificationCodeService;
import com.wwp.usercenter.util.EmailUtils;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
  public String issue(String email, EmailScene scene) {
    String normalized = EmailUtils.normalize(email);
    Boolean acquired =
        redisTemplate.opsForValue().setIfAbsent(cooldownKey(normalized), "1", COOLDOWN);
    if (!Boolean.TRUE.equals(acquired)) {
      throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "验证码发送过于频繁，请稍后再试");
    }

    String dailyKey = dailyKey(normalized, LocalDate.now(clock).format(DATE_FORMAT));
    Long count = redisTemplate.opsForValue().increment(dailyKey);
    if (count == null) {
      throw new BusinessException(ErrorCode.SYSTEM_ERROR);
    }
    if (count == 1L) {
      redisTemplate.expire(dailyKey, DAILY_COUNTER_TTL);
    }
    if (count > DAILY_LIMIT) {
      throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "今日验证码发送次数已达上限");
    }

    String code = String.format(CODE_FORMAT, RANDOM.nextInt(CODE_BOUND));
    redisTemplate.opsForValue().set(codeKey(scene.getCode(), normalized), code, CODE_TTL);
    return code;
  }

  @Override
  public void verifyAndConsume(String email, EmailScene scene, String code) {
    String key = codeKey(scene.getCode(), EmailUtils.normalize(email));
    String stored = redisTemplate.opsForValue().get(key);
    if (stored == null) {
      throw new BusinessException(ErrorCode.VERIFY_CODE_EXPIRED);
    }
    if (!stored.equals(code == null ? null : code.trim())) {
      throw new BusinessException(ErrorCode.VERIFY_CODE_INVALID);
    }
    redisTemplate.delete(key);
  }
}
