package com.kodality.termserver.commons.db.bean;

import java.sql.ResultSet;
import java.sql.SQLException;

public interface ColumnProcessor {
  Object processColumn(ResultSet rs, int index, Class<?> propType) throws SQLException;
}
