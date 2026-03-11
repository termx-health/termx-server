package com.kodality.commons.micronaut;

import io.micronaut.context.annotation.Context;

@Context
public class BeanContext {
  private static io.micronaut.context.BeanContext ctx;

  public BeanContext(io.micronaut.context.BeanContext ctx) {
    BeanContext.ctx = ctx;
  }

  public static <T> T getBean(Class<T> clazz) {
    return ctx.getBean(clazz);
  }
}
