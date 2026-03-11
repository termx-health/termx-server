package com.kodality.commons.micronaut.rest.converters;

import com.kodality.commons.util.DateUtil;
import com.kodality.commons.exception.ApiClientException;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;

import jakarta.inject.Singleton;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@Singleton
public class StringToLocalDateTimeConverter implements TypeConverter<String, LocalDateTime> {

  @Override
  public Optional<LocalDateTime> convert(String object, Class<LocalDateTime> targetType, ConversionContext context) {
    try {
      return Optional.of(DateUtil.parseDateTime(object));
    } catch (DateTimeParseException e) {
      throw new ApiClientException(e.getMessage());
    }
  }
}
