package com.kodality.termserver.commons.db.resultset;

import com.kodality.termserver.commons.model.model.LocalizedName;
import com.kodality.termserver.commons.util.JsonUtil;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ResultSetUtil {
  private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssXXX";

  public static <T> List<T> getArray(ResultSet rs, int columnIndex) throws SQLException {
    Array arr = rs.getArray(columnIndex);
    return arr == null ? null : Arrays.asList((T[]) arr.getArray());
  }

  public static Long getLong(ResultSet rs, String columnLabel) throws SQLException {
    return getLong(rs.getObject(columnLabel));
  }


  public static Long getLong(ResultSet rs, int columnIndex) throws SQLException {
    return getLong(rs.getObject(columnIndex));
  }

  public static Optional<Long> getLongO(ResultSet rs, String columnLabel) throws SQLException {
    return Optional.ofNullable(getLong(rs, columnLabel));
  }

  public static Optional<Long> getLongO(ResultSet rs, int columnIndex) throws SQLException {
    return Optional.ofNullable(getLong(rs, columnIndex));
  }

  public static LocalizedName getNames(ResultSet rs, String columnLabel) throws SQLException {
    return JsonUtil.fromJson(rs.getString(columnLabel), LocalizedName.class);
  }

  public static Long getLong(Object object) {
    if (object == null) {
      return null;
    }
    if (object instanceof BigDecimal) {
      return ((BigDecimal) object).longValue();
    }
    if (object instanceof Long) {
      return (Long) object;
    }
    if (object instanceof String) {
      return Long.valueOf((String) object);
    }
    if (object instanceof Integer) {
      return ((Integer) object).longValue();
    }
    throw new IllegalArgumentException("You can give only String, BigDecimal, Integer and Long types!");
  }

  public static List<Map<String, Object>> getRowsMap(ResultSet rs) throws SQLException {
    List<Map<String, Object>> rows = new ArrayList<>();
    ResultSetMetaData rsMetaData = rs.getMetaData();
    while (rs.next()) {
      Map<String, Object> properties = new LinkedHashMap<>();
      for (int columnIndex = 1; columnIndex <= rsMetaData.getColumnCount(); columnIndex++) {
        String columnName = rsMetaData.getColumnName(columnIndex);
        properties.put(columnName, getProperty(rs, columnIndex));
      }
      rows.add(properties);
    }
    return rows;
  }

  private static Object getProperty(ResultSet rs, int columnIndex) throws SQLException {
    String typeName = rs.getMetaData().getColumnTypeName(columnIndex);
    switch (typeName) {
      case "timestamp":
        Timestamp ts = rs.getTimestamp(columnIndex);
        return ts == null ? null : ts.toLocalDateTime();
      case "timestamptz":
        return getOffsetDateTime(rs.getTimestamp(columnIndex));
      case "json":
      case "jsonb":
        return JsonUtil.toMap(rs.getString(columnIndex));
    }

    int type = rs.getMetaData().getColumnType(columnIndex);
    switch (type) {
      case Types.VARCHAR:
      case Types.CHAR:
      case Types.LONGNVARCHAR:
      case Types.LONGVARCHAR:
      case Types.NVARCHAR:
        return rs.getString(columnIndex);
      case Types.NUMERIC:
        return rs.getBigDecimal(columnIndex);
      case Types.BIGINT:
      case Types.INTEGER:
      case Types.SMALLINT:
      case Types.TINYINT:
        return getLong(rs, columnIndex);
      case Types.DATE:
        Date date = rs.getDate(columnIndex);
        return date == null ? null : date.toLocalDate();
      case Types.TIMESTAMP:
        return getOffsetDateTime(rs.getTimestamp(columnIndex));
    }
    throw new IllegalArgumentException("unknown property type " + typeName + " / " + type);
  }

  private static OffsetDateTime getOffsetDateTime(Timestamp timestamp) {
    return timestamp == null ? null : OffsetDateTime.ofInstant(timestamp.toInstant(), ZoneId.systemDefault());
  }
}
