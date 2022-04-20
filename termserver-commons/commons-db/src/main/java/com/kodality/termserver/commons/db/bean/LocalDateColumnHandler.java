package com.kodality.termserver.commons.db.bean;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import org.apache.commons.dbutils.ColumnHandler;

public class LocalDateColumnHandler implements ColumnHandler {
  @Override
  public boolean match(Class<?> propType) {
    return propType.equals(LocalDate.class);
  }

  @Override
  public Object apply(ResultSet rs, int columnIndex) throws SQLException {
    Date date = rs.getDate(columnIndex);
    return date == null ? null : date.toLocalDate();
  }
}
