package com.kodality.commons.micronaut.rest.converters;

import com.kodality.commons.util.DateUtil;
import com.kodality.commons.exception.ApiClientException;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;

import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@Singleton
public class StringToLocalDateConverter implements TypeConverter<String, LocalDate> {

  @Override
  public Optional<LocalDate> convert(String object, Class<LocalDate> targetType, ConversionContext context) {
    try {
      return Optional.of(DateUtil.parseDate(object));
    } catch (DateTimeParseException e) {
      throw new ApiClientException(e.getMessage());
    }
  }
}
