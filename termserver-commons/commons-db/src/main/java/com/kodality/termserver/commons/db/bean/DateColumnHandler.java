package com.kodality.termserver.commons.db.bean;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import org.apache.commons.dbutils.ColumnHandler;

public class DateColumnHandler implements ColumnHandler {

  @Override
  public boolean match(Class<?> propType) {
    return propType.equals(Date.class);
  }

  @Override
  public Object apply(ResultSet rs, int columnIndex) throws SQLException {
    return rs.getTimestamp(columnIndex);
  }
}
