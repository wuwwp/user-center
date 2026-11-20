package com.wwp.usercenter.service.impl;

import static com.wwp.usercenter.common.constant.LoginGuardConstants.ACCOUNT_FAIL_LIMIT;
import static com.wwp.usercenter.common.constant.LoginGuardConstants.IP_FAIL_LIMIT;
import static com.wwp.usercenter.common.constant.LoginGuardConstants.LOCK_TTL;
import static com.wwp.usercenter.common.constant.LoginGuardConstants.accountFailKey;
import static com.wwp.usercenter.common.constant.LoginGuardConstants.ipFailKey;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import com.wwp.usercenter.common.BusinessException;
import com.wwp.usercenter.common.ErrorCode;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class LoginAttemptServiceImplTest {

  private static final String EMAIL = "alice@example.com";
  private static final String IP = "203.0.113.7";

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private LoginAttemptServiceImpl service;

  @BeforeEach
  void setUp() {
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    service = new LoginAttemptServiceImpl(redisTemplate);
  }

  @Test
  void should_allow_login_when_no_failures() {
    given(valueOperations.multiGet(counterKeys())).willReturn(Arrays.asList(null, null));

    assertThatCode(() -> service.ensureLoginAllowed(EMAIL, IP)).doesNotThrowAnyException();
  }

  @Test
  void should_reject_when_account_limit_reached() {
    given(valueOperations.multiGet(counterKeys()))
        .willReturn(Arrays.asList(String.valueOf(ACCOUNT_FAIL_LIMIT), null));

    assertThatThrownBy(() -> service.ensureLoginAllowed(EMAIL, IP))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.LOGIN_LOCKED);
  }

  @Test
  void should_reject_when_ip_limit_reached() {
    given(valueOperations.multiGet(counterKeys()))
        .willReturn(Arrays.asList("1", String.valueOf(IP_FAIL_LIMIT)));

    assertThatThrownBy(() -> service.ensureLoginAllowed(EMAIL, IP))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.LOGIN_LOCKED);
  }

  @Test
  void should_set_ttl_only_on_first_failure() {
    given(valueOperations.increment(accountFailKey(EMAIL))).willReturn(1L);
    given(valueOperations.increment(ipFailKey(IP))).willReturn(1L);

    service.recordFailure(EMAIL, IP);

    then(redisTemplate).should().expire(accountFailKey(EMAIL), LOCK_TTL);
    then(redisTemplate).should().expire(ipFailKey(IP), LOCK_TTL);
  }

  @Test
  void should_not_extend_ttl_on_later_failure() {
    given(valueOperations.increment(accountFailKey(EMAIL))).willReturn(2L);
    given(valueOperations.increment(ipFailKey(IP))).willReturn(2L);

    service.recordFailure(EMAIL, IP);

    then(redisTemplate).should(never()).expire(anyString(), any(Duration.class));
  }

  @Test
  void should_clear_account_counter_after_success() {
    service.clearAccountFailures(EMAIL);

    then(redisTemplate).should().delete(accountFailKey(EMAIL));
  }

  @Test
  void should_treat_non_numeric_counter_as_zero() {
    given(valueOperations.multiGet(counterKeys())).willReturn(Arrays.asList("broken", "0"));

    assertThatCode(() -> service.ensureLoginAllowed(EMAIL, IP)).doesNotThrowAnyException();
  }

  @Test
  void should_treat_missing_counter_values_as_zero() {
    given(valueOperations.multiGet(counterKeys())).willReturn(List.of());

    assertThatCode(() -> service.ensureLoginAllowed(EMAIL, IP)).doesNotThrowAnyException();
  }

  @Test
  void should_treat_null_multi_get_result_as_zero() {
    given(valueOperations.multiGet(counterKeys())).willReturn(null);

    assertThatCode(() -> service.ensureLoginAllowed(EMAIL, IP)).doesNotThrowAnyException();
  }

  private static List<String> counterKeys() {
    return List.of(accountFailKey(EMAIL), ipFailKey(IP));
  }
}
