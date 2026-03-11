package com.kodality.commons.db.resultset;

import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class MapExtractor implements ResultSetExtractor<Map<String, String>> {
  private final String keyField;
  private final String valueField;

  public MapExtractor() {
    this("key", "value");
  }

  public MapExtractor(String keyField, String valueField) {
    this.keyField = keyField;
    this.valueField = valueField;
  }

  @Override
  public Map<String, String> extractData(ResultSet rs) throws SQLException {
    Map<String, String> result = new HashMap<>();
    while (rs.next()) {
      result.put(rs.getString(keyField), rs.getString(valueField));
    }
    return result;
  }
}
