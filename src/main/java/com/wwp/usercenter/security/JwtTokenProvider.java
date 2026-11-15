package com.wwp.usercenter.security;

import com.wwp.usercenter.model.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.Getter;
import org.springframework.stereotype.Component;

/** 签发与解析 HS256 JWT。算法固定为 HS256，解析时拒绝其他算法与非法签名。 */
@Component
public class JwtTokenProvider {

  private static final String ISSUER = "user-center";

  private final SecretKey key;
  @Getter private final Duration expiration;

  public JwtTokenProvider(JwtProperties properties) {
    byte[] secret;
    try {
      secret = Decoders.BASE64.decode(properties.secret());
    } catch (RuntimeException exception) {
      throw new IllegalStateException("security.jwt.secret 必须是合法的 Base64 字符串", exception);
    }
    if (secret.length < 32) {
      throw new IllegalStateException("security.jwt.secret 解码后至少需要 32 字节（256 位）");
    }
    this.key = Keys.hmacShaKeyFor(secret);
    this.expiration = properties.expiration();
  }

  public String createToken(User user) {
    Instant now = Instant.now();
    return Jwts.builder()
        .issuer(ISSUER)
        .subject(String.valueOf(user.getId()))
        .claim("ver", user.getTokenVersion())
        .id(UUID.randomUUID().toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(expiration)))
        .signWith(key, Jwts.SIG.HS256)
        .compact();
  }

  public TokenPayload parse(String token) {
    Claims claims =
        Jwts.parser()
            .verifyWith(key)
            .requireIssuer(ISSUER)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    long userId = Long.parseLong(claims.getSubject());
    return new TokenPayload(userId, intClaim(claims, "ver"));
  }

  private static int intClaim(Claims claims, String name) {
    Object value = claims.get(name);
    if (!(value instanceof Number number)) {
      throw new IllegalArgumentException("JWT 缺少声明: " + name);
    }
    return number.intValue();
  }
}
