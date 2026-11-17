package com.wwp.usercenter.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 解析用于限流的客户端 IP。
 *
 * <p>只有显式开启 {@code user-center.security.trust-forwarded-for} 时才读取 {@code X-Forwarded-For}，
 * 并且只取最后一跳：Nginx 用 {@code $proxy_add_x_forwarded_for} 追加它实际看到的对端地址， 前面的内容可能是客户端自带的伪造值。关闭时一律使用
 * {@code getRemoteAddr()}。
 *
 * <p>开启该开关的前提是后端端口不直接对公网暴露；否则客户端可以直连并伪造最后一跳。
 */
@Component
public class ClientIpResolver {

  private static final Pattern IP_PATTERN = Pattern.compile("^[0-9a-fA-F:.%]+$");
  private static final int MAX_IP_LENGTH = 45;
  private static final String UNKNOWN = "unknown";

  private final boolean trustForwardedFor;

  public ClientIpResolver(
      @Value("${user-center.security.trust-forwarded-for:false}") boolean trustForwardedFor) {
    this.trustForwardedFor = trustForwardedFor;
  }

  public String resolve(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();
    if (!trustForwardedFor) {
      return sanitize(remoteAddr);
    }
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor == null || forwardedFor.isBlank()) {
      return sanitize(remoteAddr);
    }
    String[] hops = forwardedFor.split(",");
    return sanitize(hops[hops.length - 1]);
  }

  /** 只接受 IP 字符集，避免把任意字符串写进 Redis 键或日志。 */
  private static String sanitize(String candidate) {
    if (candidate == null) {
      return UNKNOWN;
    }
    String trimmed = candidate.trim();
    if (trimmed.isEmpty()
        || trimmed.length() > MAX_IP_LENGTH
        || !IP_PATTERN.matcher(trimmed).matches()) {
      return UNKNOWN;
    }
    return trimmed;
  }
}
