package com.wwp.usercenter.security;

import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.model.enums.DeletedFlag;
import com.wwp.usercenter.model.enums.UserStatus;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 解析 Bearer 令牌并加载用户行，校验 token_version、状态与角色。令牌无效时不设置认证信息，交由入口点返回 401。
 *
 * <p>该过滤器不注册为 Spring Bean，避免被 Servlet 容器再注册一次导致重复执行。
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtTokenProvider tokenProvider;
  private final UserMapper userMapper;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      authenticate(header.substring(BEARER_PREFIX.length()), request);
    }
    filterChain.doFilter(request, response);
  }

  private void authenticate(String token, HttpServletRequest request) {
    try {
      TokenPayload payload = tokenProvider.parse(token);
      User user = userMapper.selectById(payload.userId());
      if (!isValid(user, payload)) {
        SecurityContextHolder.clearContext();
        return;
      }
      LoginUser principal = new LoginUser(user.getId(), user.getUserAccount(), user.getRole());
      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(
              principal,
              null,
              List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
      authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
      SecurityContextHolder.getContext().setAuthentication(authentication);
    } catch (JwtException | IllegalArgumentException exception) {
      log.debug("invalid jwt: {}", exception.getMessage());
      SecurityContextHolder.clearContext();
    }
  }

  private static boolean isValid(User user, TokenPayload payload) {
    return user != null
        && user.getIsDelete() == DeletedFlag.NOT_DELETED
        && user.getStatus() == UserStatus.NORMAL
        && user.getTokenVersion() == payload.tokenVersion()
        && user.getRole().getCode() == payload.role();
  }
}
