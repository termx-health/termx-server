package com.kodality.termserver.commons.util;

import java.util.HashMap;
import java.util.Map;

public final class MapUtil {
  private MapUtil() {
    //
  }

  @SuppressWarnings("unchecked")
  public static <V> Map<String, V> toMap(Object... keyValues) {
    Map<String, V> map = new HashMap<>();
    if (keyValues.length == 0) {
      return map;
    }
    for (int i = 0; i < keyValues.length; i = i + 2) {
      String key = (String) keyValues[i];
      V value = (V) keyValues[i + 1];
      if (value != null) {
        map.put(key, value);
      }
    }
    return map;
  }

}
