package com.kodality.termserver.codesystem;

import com.kodality.commons.util.JsonUtil;
import io.micronaut.core.annotation.Introspected;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class CodeSystemSupplement {
  private Long id;
  private String codeSystem;
  private String targetType;
  private Object target;
  private String description;
  private OffsetDateTime created;

  public Object getTarget() {
    String json = JsonUtil.toJson(target);
    if (CodeSystemSupplementType.property.equals(targetType)) {
      return JsonUtil.fromJson(json, EntityProperty.class);
    } else if (CodeSystemSupplementType.propertyValue.equals(targetType)) {
      return JsonUtil.fromJson(json, EntityPropertyValue.class);
    } else if (CodeSystemSupplementType.designation.equals(targetType)) {
      return JsonUtil.fromJson(json, Designation.class);
    }
    return target;
  }
}
