package com.kodality.commons.csv.record;

import java.util.HashMap;

public class RecordValueMap extends HashMap<String, String> {

  @Override
  public String put(String key, String value) {
    return super.put(key == null ? null : key.toLowerCase(), value);
  }

  @Override
  public String get(Object key) {
    return super.get(key == null ? null : ((String) key).toLowerCase());
  }
}
