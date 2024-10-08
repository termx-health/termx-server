package com.kodality.termx.ts.codesystem;

import com.kodality.commons.util.JsonUtil;
import io.micronaut.core.annotation.Introspected;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class EntityPropertyValue {
  private Long id;
  private Object value;
  private Long entityPropertyId;
  private Long codeSystemEntityVersionId;

  private String entityProperty;
  private String entityPropertyType;

  private boolean supplement;

  public EntityPropertyValueCodingValue asCodingValue() {
    return JsonUtil.fromJson(JsonUtil.toJson(value), EntityPropertyValueCodingValue.class);
  }

  public OffsetDateTime asDateTimeValue() {
    return OffsetDateTime.parse((String) value);
  }

  public BigDecimal asDecimal() {
    return new BigDecimal(String.valueOf(value));
  }

  @Getter
  @Setter
  @NoArgsConstructor
  public static class EntityPropertyValueCodingValue {
    private String code;
    private String codeSystem;

    public EntityPropertyValueCodingValue(String code, String codeSystem) {
      this.code = code;
      this.codeSystem = codeSystem;
    }
  }
}
