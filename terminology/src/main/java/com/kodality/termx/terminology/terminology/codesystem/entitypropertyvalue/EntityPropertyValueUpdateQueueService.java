package com.kodality.termx.terminology.terminology.codesystem.entitypropertyvalue;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import jakarta.inject.Singleton;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class EntityPropertyValueUpdateQueueService {
  private final EntityPropertyValueUpdateQueueRepository repository;

  public void markCodingValuesForUpdate(List<EntityPropertyValue> values) {
    if (values == null || values.isEmpty()) {
      return;
    }
    List<Long> csevIds = values.stream()
        .filter(v -> v.getCodeSystemEntityVersionId() != null)
        .filter(v -> isCodingValue(v.getValue()))
        .map(EntityPropertyValue::getCodeSystemEntityVersionId)
        .distinct()
        .toList();
    repository.markForUpdate(csevIds);
  }

  public void markCodingValuesForUpdatePairs(List<Pair<Long, EntityPropertyValue>> values) {
    if (values == null || values.isEmpty()) {
      return;
    }
    List<Long> csevIds = values.stream()
        .filter(v -> v.getLeft() != null)
        .filter(v -> isCodingValue(v.getRight().getValue()))
        .map(Pair::getLeft)
        .distinct()
        .toList();
    repository.markForUpdate(csevIds);
  }

  private boolean isCodingValue(Object value) {
    if (value == null) {
      return false;
    }
    try {
      var map = JsonUtil.toMap(JsonUtil.toJson(value));
      return map.containsKey("code") && map.containsKey("codeSystem");
    } catch (Exception e) {
      return false;
    }
  }
}
