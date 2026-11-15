package com.wwp.usercenter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wwp.usercenter.model.entity.User;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

  private static final String SECRET =
      Base64.getEncoder()
          .encodeToString("test-only-secret-must-be-32-bytes".getBytes(StandardCharsets.UTF_8));

  private final JwtTokenProvider provider =
      new JwtTokenProvider(new JwtProperties(SECRET, Duration.ofHours(2)));

  @Test
  void should_round_trip_claims() {
    User user = new User();
    user.setId(42L);
    user.setTokenVersion(3);

    TokenPayload payload = provider.parse(provider.createToken(user));

    assertThat(payload.userId()).isEqualTo(42L);
    assertThat(payload.tokenVersion()).isEqualTo(3);
  }

  @Test
  void should_reject_tampered_token() {
    User user = new User();
    user.setId(1L);
    user.setTokenVersion(0);

    String token = provider.createToken(user) + "x";

    assertThatThrownBy(() -> provider.parse(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void should_reject_expired_token() {
    JwtTokenProvider expiredProvider =
        new JwtTokenProvider(new JwtProperties(SECRET, Duration.ofSeconds(-1)));
    User user = new User();
    user.setId(1L);
    user.setTokenVersion(0);

    String token = expiredProvider.createToken(user);

    assertThatThrownBy(() -> provider.parse(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void should_not_include_role_claim() {
    User user = new User();
    user.setId(1L);
    user.setTokenVersion(0);

    String token = provider.createToken(user);
    String payload =
        new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);

    assertThat(payload).doesNotContain("\"role\"");
  }

  @Test
  void should_reject_short_secret() {
    String shortSecret = Base64.getEncoder().encodeToString(new byte[16]);

    assertThatThrownBy(
            () -> new JwtTokenProvider(new JwtProperties(shortSecret, Duration.ofHours(2))))
        .isInstanceOf(IllegalStateException.class);
  }
}
