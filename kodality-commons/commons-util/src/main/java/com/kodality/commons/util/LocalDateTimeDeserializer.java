package com.kodality.commons.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.databind.DeserializationContext;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LocalDateTimeDeserializer extends com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer {

  public LocalDateTimeDeserializer() {
    super(DateTimeFormatter.ISO_DATE_TIME);//XXX needed?
  }

  @Override
  public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    if (parser.hasTokenId(JsonTokenId.ID_STRING)) {
      String string = parser.getText().trim();
      if (string.length() == 0) {
        return null;
      }
      return DateUtil.parseDateTime(string);
    }
    return super.deserialize(parser, context);
  }

}
