package com.kodality.commons.db.bean;

import org.apache.commons.dbutils.ColumnHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

public class DateColumnHandler implements ColumnHandler {
  
  @Override
  public boolean match(Class propType) {
    return propType.equals(Date.class);
  }

  @Override
  public Object apply(ResultSet rs, int columnIndex) throws SQLException {
    return rs.getTimestamp(columnIndex);
  }
}
