package com.wwp.usercenter.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

  @Test
  void should_use_remote_addr_when_forwarded_for_not_trusted() {
    ClientIpResolver resolver = new ClientIpResolver(false);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("198.51.100.10");
    request.addHeader("X-Forwarded-For", "203.0.113.99");

    assertThat(resolver.resolve(request)).isEqualTo("198.51.100.10");
  }

  @Test
  void should_use_last_hop_when_forwarded_for_trusted() {
    ClientIpResolver resolver = new ClientIpResolver(true);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");
    // 前面是客户端伪造，最后一跳才是 Nginx 追加的真实对端地址
    request.addHeader("X-Forwarded-For", "6.6.6.6, 198.51.100.10");

    assertThat(resolver.resolve(request)).isEqualTo("198.51.100.10");
  }

  @Test
  void should_fallback_to_remote_addr_when_header_blank() {
    ClientIpResolver resolver = new ClientIpResolver(true);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("198.51.100.10");
    request.addHeader("X-Forwarded-For", "   ");

    assertThat(resolver.resolve(request)).isEqualTo("198.51.100.10");
  }

  @Test
  void should_return_unknown_when_candidate_is_not_an_ip() {
    ClientIpResolver resolver = new ClientIpResolver(true);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Forwarded-For", "evil-string");

    assertThat(resolver.resolve(request)).isEqualTo("unknown");
  }

  @Test
  void should_keep_ipv6_address() {
    ClientIpResolver resolver = new ClientIpResolver(true);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Forwarded-For", "2001:db8::1");

    assertThat(resolver.resolve(request)).isEqualTo("2001:db8::1");
  }
}
