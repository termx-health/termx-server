package com.kodality.termserver.commons.db.bean;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalTime;
import org.apache.commons.dbutils.ColumnHandler;

public class LocalTimeColumnHandler implements ColumnHandler {
  @Override
  public boolean match(Class<?> propType) {
    return propType.equals(LocalTime.class);
  }

  @Override
  public Object apply(ResultSet rs, int columnIndex) throws SQLException {
    Time time = rs.getTime(columnIndex);
    return time == null ? null : time.toLocalTime();
  }
}
