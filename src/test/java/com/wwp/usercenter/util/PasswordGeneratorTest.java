package com.wwp.usercenter.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wwp.usercenter.common.constant.ValidationConstants;
import org.junit.jupiter.api.Test;

class PasswordGeneratorTest {

  @Test
  void should_generate_policy_compliant_password() {
    String password = PasswordGenerator.generate(16);

    assertThat(password).hasSize(16);
    assertThat(password).matches(ValidationConstants.PASSWORD_REGEXP);
    assertThat(password).containsPattern("[A-Za-z]");
    assertThat(password).containsPattern("[0-9]");
  }

  @Test
  void should_generate_different_passwords() {
    assertThat(PasswordGenerator.generate(16)).isNotEqualTo(PasswordGenerator.generate(16));
  }

  @Test
  void should_reject_too_short_length() {
    assertThatThrownBy(() -> PasswordGenerator.generate(1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
