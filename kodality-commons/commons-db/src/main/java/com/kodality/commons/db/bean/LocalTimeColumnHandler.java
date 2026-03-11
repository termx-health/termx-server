package com.kodality.commons.db.bean;

import org.apache.commons.dbutils.ColumnHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalTime;

public class LocalTimeColumnHandler implements ColumnHandler {
  @Override
  public boolean match(Class propType) {
    return propType.equals(LocalTime.class);
  }

  @Override
  public Object apply(ResultSet rs, int columnIndex) throws SQLException {
    Time time = rs.getTime(columnIndex);
    return time == null ? null : time.toLocalTime();
  }
}
