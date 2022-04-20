package com.kodality.termserver.commons.db.bean;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.apache.commons.dbutils.ColumnHandler;

public class OffsetDateTimeColumnHandler implements ColumnHandler {
  @Override
  public boolean match(Class<?> propType) {
    return propType.equals(OffsetDateTime.class);
  }

  @Override
  public Object apply(ResultSet rs, int columnIndex) throws SQLException {
    Timestamp timestamp = rs.getTimestamp(columnIndex);
    return timestamp == null ? null : OffsetDateTime.ofInstant(timestamp.toInstant(), ZoneId.systemDefault());
  }
}
