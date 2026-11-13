package com.wwp.usercenter.common;

/** 统一响应体。code 为 0 表示成功，非 0 与 {@link ErrorCode} 对应。 */
public record BaseResponse<T>(int code, String message, T data) {

  public static <T> BaseResponse<T> success(T data) {
    return new BaseResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
  }

  public static BaseResponse<Void> success() {
    return new BaseResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
  }

  public static <T> BaseResponse<T> failure(ErrorCode errorCode) {
    return new BaseResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
  }

  public static <T> BaseResponse<T> failure(ErrorCode errorCode, String message) {
    return new BaseResponse<>(errorCode.getCode(), message, null);
  }
}
