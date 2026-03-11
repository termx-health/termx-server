package com.kodality.commons.util;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class MapUtil {
  private MapUtil() {
    //
  }

  @SuppressWarnings("unchecked")
  public static <K, V> Map<K, V> toMap(Object[]... arr) {
    Map<K, V> map = new HashMap<>(arr.length);
    for (Object[] $ : arr) {
      map.put((K) $[0], (V) $[1]);
    }
    return map;
  }

  @SuppressWarnings("unchecked")
  public static <V> Map<String, V> toMap(Object... keyValues) {
    Map<String, V> map = new HashMap<>();
    if (keyValues.length == 0) {
      return map;
    }
    for (int i = 0; i < keyValues.length; i = i + 2) {
      map.put((String) keyValues[i], (V) keyValues[i + 1]);
    }
    return map;
  }

  public static Map<String, Object> flatten(Map<String, Object> src) {
    return src == null ? null : src.entrySet().stream().flatMap(MapUtil::flatten)
        .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), LinkedHashMap::putAll);
  }

  private static Stream<Entry<String, Object>> flatten(Entry<String, Object> entry) {
    if (entry == null) {
      return Stream.empty();
    }

    if (entry.getValue() instanceof Map<?, ?> map) {
      return map.entrySet().stream().flatMap(e -> flatten(new AbstractMap.SimpleEntry<>(entry.getKey() + "." + e.getKey(), e.getValue())));
    }

    if (entry.getValue() instanceof List<?> list) {
      return IntStream.range(0, list.size())
          .mapToObj(i -> new AbstractMap.SimpleEntry<String, Object>(entry.getKey() + "." + i, list.get(i)))
          .flatMap(MapUtil::flatten);
    }

    return Stream.of(entry);
  }

}
