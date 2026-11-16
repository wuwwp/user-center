package com.wwp.usercenter.common.validation;

import com.wwp.usercenter.common.constant.ValidationConstants;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 邮箱验证码格式：固定长度数字，长度与生成器共用同一常量。 */
@Documented
@Constraint(validatedBy = {})
@Pattern(
    regexp = ValidationConstants.EMAIL_CODE_REGEXP,
    message = ValidationConstants.EMAIL_CODE_MESSAGE)
@Target({
  ElementType.RECORD_COMPONENT,
  ElementType.FIELD,
  ElementType.PARAMETER,
  ElementType.ANNOTATION_TYPE
})
@Retention(RetentionPolicy.RUNTIME)
public @interface EmailCode {

  String message() default ValidationConstants.EMAIL_CODE_MESSAGE;

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
