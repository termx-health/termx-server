package org.termx.ts.codesystem;

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
  // Per-value FHIR extensions — `CodeSystem.concept.property[].extension`. Stored
  // as raw JSON so the model stays provider-neutral (termx-api has no zmei/FHIR
  // dep). Mappers cast to/from `List<Extension>` at the FHIR boundary.
  private Object extensions;
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
    private Object display;
    private String version;
    private String targetEffectiveTime;

    public EntityPropertyValueCodingValue(String code, String codeSystem) {
      this.code = code;
      this.codeSystem = codeSystem;
    }
  }

  @Getter
  @Setter
  @NoArgsConstructor
  public static class EntityPropertyValueCodingDesignationValue {
    private String name;
    private String language;
    private String use;
  }
}
