package com.kodality.termx.core.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import io.micronaut.core.util.StringUtils;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class SimpleDateDeserializer extends JsonDeserializer<LocalDate> {

  @Override
  public LocalDate deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
    String dateString = jsonParser.getValueAsString();
    if (StringUtils.isEmpty(dateString)){
      return null;
    }
    return LocalDate.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
  }
}
