package com.wwp.usercenter.security;

import com.wwp.usercenter.common.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.ObjectMapper;

/** 安全过滤器链中统一写出 RFC 9457 ProblemDetail，避免 401/403 各自拼装响应体。 */
final class SecurityErrorResponseWriter {

  private static final String BEARER_CHALLENGE = "Bearer";

  private SecurityErrorResponseWriter() {}

  static void write(HttpServletResponse response, ObjectMapper objectMapper, ErrorCode errorCode)
      throws IOException {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(errorCode.getHttpStatus(), errorCode.getMessage());
    problem.setTitle(errorCode.getMessage());
    problem.setProperty("code", errorCode.getCode());

    response.setStatus(errorCode.getHttpStatus().value());
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    if (errorCode.getHttpStatus() == HttpStatus.UNAUTHORIZED) {
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER_CHALLENGE);
    }
    objectMapper.writeValue(response.getWriter(), problem);
  }
}
