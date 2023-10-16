package com.kodality.termx.core.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({TYPE, METHOD})
public @interface Authorized {
  String ANY = "*";

  /**
   * automagical:
   * given full privilege string resource.type.action will be taken as is
   * given type.action, resource will be first request parameter or '*' if no parameters.
   * resource can also be {parameter}
   */
  String[] value() default {};

  /**
   * can also be a {parameter}
   */
  String resource() default ANY;

  /**
   * resource() + . + privilege()
   */
  String privilege() default "";
}
