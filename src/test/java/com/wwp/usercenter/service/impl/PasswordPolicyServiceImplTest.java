package com.wwp.usercenter.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wwp.usercenter.common.BusinessException;
import com.wwp.usercenter.common.ErrorCode;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PasswordPolicyServiceImplTest {

  private final PasswordPolicyServiceImpl service = new PasswordPolicyServiceImpl();

  @Test
  void should_reject_dictionary_entry_from_classpath() {
    assertThatThrownBy(() -> service.ensureNotWeak("Password1"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PARAM_INVALID);
  }

  @Test
  void should_reject_case_variant() {
    assertThatThrownBy(() -> service.ensureNotWeak("PASSWORD1"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void should_reject_leet_variant() {
    assertThatThrownBy(() -> service.ensureNotWeak("P@ssw0rd"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void should_reject_eight_char_digit_entry() {
    assertThatThrownBy(() -> service.ensureNotWeak("12345678"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void should_accept_non_dictionary_password() {
    assertThatCode(() -> service.ensureNotWeak("Str0ng-Passw0rd!9")).doesNotThrowAnyException();
  }

  @Test
  void should_match_injected_dictionary_after_normalization() {
    PasswordPolicyServiceImpl custom = new PasswordPolicyServiceImpl(Set.of("letmein"));

    assertThatThrownBy(() -> custom.ensureNotWeak("LetMeIn")).isInstanceOf(BusinessException.class);
    assertThatCode(() -> custom.ensureNotWeak("LetMeIn2026")).doesNotThrowAnyException();
  }

  @Test
  void should_ignore_null_or_empty_password() {
    assertThatCode(() -> service.ensureNotWeak(null)).doesNotThrowAnyException();
    assertThatCode(() -> service.ensureNotWeak("")).doesNotThrowAnyException();
  }
}
