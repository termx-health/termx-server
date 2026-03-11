package com.kodality.commons.db.sql;

import com.kodality.commons.util.MapUtil;
import java.util.Map;
import java.util.stream.Collectors;

public final class Jsonb {

  public static String agg(String select) {
    return "select jsonb_agg(t.t) from (" + select + ") as t";
  }

  public static String object(String... params) {
    return object(MapUtil.toMap(params));
  }

  public static String object(Map<String, String> fields) {
    return "jsonb_build_object(" + fields.keySet().stream().map(k -> "'" + k + "', " + fields.get(k)).collect(Collectors.joining(", ")) + ")";
  }

}
