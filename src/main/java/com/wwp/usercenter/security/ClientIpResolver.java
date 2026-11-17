package com.wwp.usercenter.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 返回容器解析后的客户端 IP。
 *
 * <p>生产通过 {@code server.forward-headers-strategy=native} 启用 Tomcat {@code RemoteIpValve}：
 * 由它从可信代理链中解析真实客户端写入 {@code remoteAddr}，并改写/移除 {@code X-Forwarded-For}。
 * 因此应用层不再自行解析转发头，否则会把阀改写后的、客户端可伪造的值当成真实 IP。
 *
 * <p>开发环境没有代理阀时，{@code remoteAddr} 就是直连对端地址。
 */
@Component
public class ClientIpResolver {

  static final String UNKNOWN = "unknown";
  private static final Pattern IP_PATTERN = Pattern.compile("[0-9a-fA-F:.%]+");
  private static final int MAX_IP_LENGTH = 45;

  public String resolve(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();
    if (remoteAddr == null) {
      return UNKNOWN;
    }
    String trimmed = remoteAddr.trim();
    if (trimmed.length() > MAX_IP_LENGTH || !IP_PATTERN.matcher(trimmed).matches()) {
      return UNKNOWN;
    }
    return trimmed;
  }
}
