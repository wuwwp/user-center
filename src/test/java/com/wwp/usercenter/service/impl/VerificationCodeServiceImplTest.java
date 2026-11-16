package com.wwp.usercenter.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.wwp.usercenter.common.BusinessException;
import com.wwp.usercenter.common.ErrorCode;
import com.wwp.usercenter.common.constant.VerificationCodeConstants;
import com.wwp.usercenter.model.enums.EmailScene;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class VerificationCodeServiceImplTest {

  private static final String EMAIL = "alice@example.com";
  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private VerificationCodeServiceImpl service;

  @BeforeEach
  void setUp() {
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    service = new VerificationCodeServiceImpl(redisTemplate, Clock.system(ZONE));
  }

  @Test
  void should_store_six_digit_code_with_scene_key_when_issue() {
    given(valueOperations.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(60))))
        .willReturn(true);
    given(valueOperations.increment(anyString())).willReturn(1L);

    String code = service.issue(" Alice@Example.com ", EmailScene.REGISTER);

    assertThat(code).matches("\\d{6}");
    ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
    then(valueOperations).should().set(key.capture(), eq(code), eq(Duration.ofMinutes(5)));
    assertThat(key.getValue()).isEqualTo("verify:register:" + EMAIL);
  }

  @Test
  void should_use_configured_zone_for_daily_key_across_midnight() {
    // UTC 16:30 已是北京时间次日 00:30，日期键必须按 Asia/Shanghai 计算
    Clock clock = Clock.fixed(Instant.parse("2026-11-16T16:30:00Z"), ZONE);
    VerificationCodeServiceImpl serviceWithClock =
        new VerificationCodeServiceImpl(redisTemplate, clock);
    given(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(60))))
        .willReturn(true);
    given(valueOperations.increment(anyString())).willReturn(1L);

    serviceWithClock.issue(EMAIL, EmailScene.REGISTER);

    then(valueOperations).should().increment("verify:daily:" + EMAIL + ":20261117");
  }

  @Test
  void should_reject_when_cooldown_active() {
    given(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(60))))
        .willReturn(false);

    assertThatThrownBy(() -> service.issue(EMAIL, EmailScene.REGISTER))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
    then(valueOperations).shouldHaveNoMoreInteractions();
  }

  @Test
  void should_reject_when_daily_limit_reached() {
    given(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(60))))
        .willReturn(true);
    given(valueOperations.increment(anyString())).willReturn(11L);

    assertThatThrownBy(() -> service.issue(EMAIL, EmailScene.RESET))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
    then(valueOperations).should(never()).set(anyString(), anyString(), eq(Duration.ofMinutes(5)));
  }

  @Test
  void should_expire_daily_counter_on_first_send() {
    given(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(60))))
        .willReturn(true);
    given(valueOperations.increment(anyString())).willReturn(1L);

    service.issue(EMAIL, EmailScene.REGISTER);

    then(redisTemplate)
        .should()
        .expire(anyString(), eq(VerificationCodeConstants.DAILY_COUNTER_TTL));
  }

  @Test
  void should_fail_with_expired_when_code_absent_for_scene() {
    given(valueOperations.get("verify:reset:" + EMAIL)).willReturn(null);

    assertThatThrownBy(() -> service.verifyAndConsume(EMAIL, EmailScene.RESET, "123456"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VERIFY_CODE_EXPIRED);
    then(redisTemplate).should(never()).delete(anyString());
  }

  @Test
  void should_fail_with_invalid_and_keep_code_when_mismatch() {
    given(valueOperations.get("verify:register:" + EMAIL)).willReturn("123456");

    assertThatThrownBy(() -> service.verifyAndConsume(EMAIL, EmailScene.REGISTER, "654321"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VERIFY_CODE_INVALID);
    then(redisTemplate).should(never()).delete(anyString());
  }

  @Test
  void should_consume_code_when_match() {
    given(valueOperations.get("verify:register:" + EMAIL)).willReturn("123456");

    service.verifyAndConsume("Alice@Example.com", EmailScene.REGISTER, " 123456 ");

    then(redisTemplate).should().delete("verify:register:" + EMAIL);
  }
}
