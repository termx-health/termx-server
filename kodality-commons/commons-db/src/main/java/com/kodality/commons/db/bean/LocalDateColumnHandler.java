package com.kodality.commons.db.bean;

import org.apache.commons.dbutils.ColumnHandler;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public class LocalDateColumnHandler implements ColumnHandler {
  @Override
  public boolean match(Class propType) {
    return propType.equals(LocalDate.class);
  }

  @Override
  public Object apply(ResultSet rs, int columnIndex) throws SQLException {
    Date date = rs.getDate(columnIndex);
    return date == null ? null : date.toLocalDate();
  }
}
