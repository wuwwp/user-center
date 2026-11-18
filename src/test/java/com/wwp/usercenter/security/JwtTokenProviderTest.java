package com.wwp.usercenter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wwp.usercenter.model.entity.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

  private static final String SECRET =
      Base64.getEncoder()
          .encodeToString("test-only-secret-must-be-32-bytes".getBytes(StandardCharsets.UTF_8));
  private static final Clock CLOCK = Clock.systemUTC();

  private final JwtTokenProvider provider =
      new JwtTokenProvider(new JwtProperties(SECRET, Duration.ofHours(2)), CLOCK);

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
    Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(3)), ZoneOffset.UTC);
    JwtTokenProvider expiredProvider =
        new JwtTokenProvider(new JwtProperties(SECRET, Duration.ofHours(2)), past);
    User user = new User();
    user.setId(1L);
    user.setTokenVersion(0);

    String token = expiredProvider.createToken(user);

    assertThatThrownBy(() -> provider.parse(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void should_reject_non_positive_expiration() {
    assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties(SECRET, Duration.ZERO), CLOCK))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void should_reject_non_hs256_algorithm() {
    String secret512 =
        Base64.getEncoder().encodeToString("k".repeat(64).getBytes(StandardCharsets.UTF_8));
    JwtTokenProvider provider512 =
        new JwtTokenProvider(new JwtProperties(secret512, Duration.ofHours(2)), CLOCK);
    SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret512));
    Instant now = Instant.now(CLOCK);
    String token =
        Jwts.builder()
            .issuer("user-center")
            .subject("1")
            .audience()
            .add("user-center-api")
            .and()
            .claim("ver", 0)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(60)))
            .signWith(key, Jwts.SIG.HS512)
            .compact();

    assertThatThrownBy(() -> provider512.parse(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void should_reject_token_without_audience() {
    SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    Instant now = Instant.now(CLOCK);
    String token =
        Jwts.builder()
            .issuer("user-center")
            .subject("1")
            .claim("ver", 0)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(60)))
            .signWith(key, Jwts.SIG.HS256)
            .compact();

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
            () -> new JwtTokenProvider(new JwtProperties(shortSecret, Duration.ofHours(2)), CLOCK))
        .isInstanceOf(IllegalStateException.class);
  }
}
