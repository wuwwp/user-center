package com.wwp.usercenter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

  private final ClientIpResolver resolver = new ClientIpResolver();

  @Test
  void should_use_remote_addr_and_ignore_forwarded_for() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("198.51.100.10");
    // RemoteIpValve 已经处理并改写了转发头，应用层不应再读取它
    request.addHeader("X-Forwarded-For", "6.6.6.6");

    assertThat(resolver.resolve(request)).isEqualTo("198.51.100.10");
  }

  @Test
  void should_keep_ipv6_remote_addr() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("2001:db8::1");

    assertThat(resolver.resolve(request)).isEqualTo("2001:db8::1");
  }

  @Test
  void should_return_unknown_when_remote_addr_invalid() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("evil-string");

    assertThat(resolver.resolve(request)).isEqualTo(ClientIpResolver.UNKNOWN);
  }

  @Test
  void should_return_unknown_when_remote_addr_absent() {
    assertThat(resolver.resolve(mock(HttpServletRequest.class)))
        .isEqualTo(ClientIpResolver.UNKNOWN);
  }
}
