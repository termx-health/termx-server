package com.kodality.zmei.fhir.jackson;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.datatype.jsr310.deser.JSR310DateTimeDeserializerBase;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class OffsetDateTimeDeserializer extends JSR310DateTimeDeserializerBase<OffsetDateTime> {

  public OffsetDateTimeDeserializer() {
    super(OffsetDateTime.class, DateTimeFormatter.ISO_ZONED_DATE_TIME);
  }

  @Override
  public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    if (parser.hasTokenId(JsonTokenId.ID_STRING)) {
      String input = parser.getText().trim();
      if (input.length() == 0) {
        return null;
      }
      return OffsetDateTimeParser.parse(input);
    }
    return null;
  }

  @Override
  protected OffsetDateTimeDeserializer withDateFormat(DateTimeFormatter dtf) {
    return new OffsetDateTimeDeserializer();
  }

  @Override
  protected OffsetDateTimeDeserializer withLeniency(Boolean leniency) {
    return new OffsetDateTimeDeserializer();
  }

  @Override
  protected OffsetDateTimeDeserializer withShape(JsonFormat.Shape shape) {
    return new OffsetDateTimeDeserializer();
  }

}
