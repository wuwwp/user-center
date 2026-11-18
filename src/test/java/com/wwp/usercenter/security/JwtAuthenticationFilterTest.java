package com.wwp.usercenter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.model.enums.DeletedFlag;
import com.wwp.usercenter.model.enums.UserRole;
import com.wwp.usercenter.model.enums.UserStatus;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

  @Mock private JwtTokenProvider tokenProvider;
  @Mock private UserMapper userMapper;

  private JwtAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    filter = new JwtAuthenticationFilter(tokenProvider, userMapper);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void should_authenticate_when_token_valid_and_version_matches() throws Exception {
    given(tokenProvider.parse("token")).willReturn(new TokenPayload(7L, 3));
    given(userMapper.selectAuthStateById(7L)).willReturn(user(7L, UserStatus.NORMAL, 3));

    doFilter("Bearer token");

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication).isNotNull();
    assertThat(authentication.getPrincipal()).isEqualTo(new LoginUser(7L));
    assertThat(authentication.getAuthorities())
        .extracting("authority")
        .containsExactly("ROLE_ADMIN");
  }

  @Test
  void should_not_authenticate_when_token_version_stale() throws Exception {
    given(tokenProvider.parse("token")).willReturn(new TokenPayload(7L, 2));
    given(userMapper.selectAuthStateById(7L)).willReturn(user(7L, UserStatus.NORMAL, 3));

    doFilter("Bearer token");

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void should_not_authenticate_when_user_banned() throws Exception {
    given(tokenProvider.parse("token")).willReturn(new TokenPayload(7L, 3));
    given(userMapper.selectAuthStateById(7L)).willReturn(user(7L, UserStatus.BANNED, 3));

    doFilter("Bearer token");

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void should_not_authenticate_when_user_deleted() throws Exception {
    User deleted = user(7L, UserStatus.NORMAL, 3);
    deleted.setIsDelete(DeletedFlag.DELETED);
    given(tokenProvider.parse("token")).willReturn(new TokenPayload(7L, 3));
    given(userMapper.selectAuthStateById(7L)).willReturn(deleted);

    doFilter("Bearer token");

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void should_not_authenticate_when_token_malformed() throws Exception {
    given(tokenProvider.parse("token")).willThrow(new JwtException("malformed"));

    doFilter("Bearer token");

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void should_authenticate_with_case_insensitive_bearer_scheme() throws Exception {
    given(tokenProvider.parse("token")).willReturn(new TokenPayload(7L, 3));
    given(userMapper.selectAuthStateById(7L)).willReturn(user(7L, UserStatus.NORMAL, 3));

    doFilter("bearer token");

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
  }

  @Test
  void should_not_authenticate_without_bearer_header() throws Exception {
    doFilter(null);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void should_not_authenticate_with_wrong_scheme() throws Exception {
    doFilter("Basic dXNlcjpwYXNz");

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  private void doFilter(String authorization) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    if (authorization != null) {
      request.addHeader("Authorization", authorization);
    }
    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
  }

  private static User user(long id, UserStatus status, int tokenVersion) {
    User user = new User();
    user.setId(id);
    user.setRole(UserRole.ADMIN);
    user.setStatus(status);
    user.setIsDelete(DeletedFlag.NOT_DELETED);
    user.setTokenVersion(tokenVersion);
    return user;
  }
}
