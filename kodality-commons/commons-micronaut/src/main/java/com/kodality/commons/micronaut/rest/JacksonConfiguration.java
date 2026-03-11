package com.kodality.commons.micronaut.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kodality.commons.util.JsonUtil;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;

@Factory
public class JacksonConfiguration {
  @Singleton
  @Primary
  @Bean
  @Replaces(ObjectMapper.class)
  public ObjectMapper mapper() {
    return JsonUtil.getObjectMapper();
  }

}
