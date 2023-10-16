package com.kodality.termx.core.sys.provenance;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.kodality.commons.util.JsonUtil;
import com.kodality.commons.util.MapUtil;
import com.kodality.termx.core.sys.provenance.Provenance.ProvenanceChange;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class ProvenanceUtil {
  private ProvenanceUtil() {}

  public static <T> Map<String, ProvenanceChange> diff(T left, T right, String... excludeFields) {
    Map<String, Object> mleft = JsonUtil.getObjectMapper().convertValue(left, Map.class);
    Map<String, Object> mright = JsonUtil.getObjectMapper().convertValue(right, Map.class);
    Stream.of(excludeFields).forEach(f -> {
      mleft.remove(f);
      mright.remove(f);
    });
    MapDifference<String, Object> diff = Maps.difference(MapUtil.flatten(mleft), MapUtil.flatten(mright));
    Map<String, ProvenanceChange> result = new HashMap<>();
    diff.entriesOnlyOnLeft().forEach((k, v) -> result.put(k, ProvenanceChange.of(v, null)));
    diff.entriesOnlyOnRight().forEach((k, v) -> result.put(k, ProvenanceChange.of(null, v)));
    diff.entriesDiffering().forEach((k, v) -> result.put(k, ProvenanceChange.of(v.leftValue(), v.rightValue())));
    return result;
  }
}
