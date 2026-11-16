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

/** 强密码规则：8~64 位可见 ASCII，且同时包含字母与数字。 */
@Documented
@Constraint(validatedBy = {})
@Pattern(
    regexp = ValidationConstants.PASSWORD_REGEXP,
    message = ValidationConstants.PASSWORD_MESSAGE)
@Target({
  ElementType.RECORD_COMPONENT,
  ElementType.FIELD,
  ElementType.PARAMETER,
  ElementType.ANNOTATION_TYPE
})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

  String message() default ValidationConstants.PASSWORD_MESSAGE;

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
