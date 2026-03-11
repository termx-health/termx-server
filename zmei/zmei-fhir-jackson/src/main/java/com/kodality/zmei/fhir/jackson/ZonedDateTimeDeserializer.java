package com.kodality.zmei.fhir.jackson;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.datatype.jsr310.deser.JSR310DateTimeDeserializerBase;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class ZonedDateTimeDeserializer extends JSR310DateTimeDeserializerBase<ZonedDateTime> {

  public ZonedDateTimeDeserializer() {
    super(ZonedDateTime.class, null);
  }

  @Override
  public ZonedDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    if (parser.hasTokenId(JsonTokenId.ID_STRING)) {
      String input = parser.getText().trim();
      if (input.length() == 0) {
        return null;
      }
      return ZonedDateTimeParser.parse(input);
    }
    return null;
  }

  @Override
  protected ZonedDateTimeDeserializer withDateFormat(DateTimeFormatter dtf) {
    return new ZonedDateTimeDeserializer();
  }

  @Override
  protected JSR310DateTimeDeserializerBase<ZonedDateTime> withLeniency(Boolean leniency) {
    return new ZonedDateTimeDeserializer();
  }

  @Override
  protected JSR310DateTimeDeserializerBase<ZonedDateTime> withShape(JsonFormat.Shape shape) {
    return new ZonedDateTimeDeserializer();
  }
}
