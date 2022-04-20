package com.kodality.termserver.commons.db.bean;

import java.sql.ResultSet;
import java.sql.SQLException;

public interface RowProcessor {
  Object process(ResultSet rs) throws SQLException;
}
