package com.kodality.commons.db.bean;

import org.apache.commons.dbutils.ColumnHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class LocalDateTimeColumnHandler implements ColumnHandler {
  @Override
  public boolean match(Class propType) {
    return propType.equals(LocalDateTime.class);
  }

  @Override
  public Object apply(ResultSet rs, int columnIndex) throws SQLException {
    Timestamp timestamp = rs.getTimestamp(columnIndex);
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }
}
