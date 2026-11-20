package com.wwp.usercenter.common;

import org.springframework.http.ProblemDetail;

/** RFC 9457 响应体的唯一组装入口，确保 MVC 与安全过滤器链返回同一契约。 */
public final class ProblemDetailFactory {

  private ProblemDetailFactory() {}

  public static ProblemDetail of(ErrorCode errorCode, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.getHttpStatus(), detail);
    problem.setTitle(errorCode.getMessage());
    problem.setProperty("code", errorCode.getCode());
    return problem;
  }
}
