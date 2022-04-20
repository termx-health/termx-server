package com.kodality.termserver.commons.db.bean;

import com.fasterxml.jackson.databind.JavaType;
import com.kodality.termserver.commons.db.resultset.ResultSetUtil;
import com.kodality.termserver.commons.model.model.CodeName;
import com.kodality.termserver.commons.util.JsonUtil;
import com.kodality.termserver.commons.util.range.LocalDateRange;
import com.kodality.termserver.commons.util.range.LocalDateTimeRange;
import com.kodality.termserver.commons.util.range.OffsetDateTimeRange;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.function.Consumer;

public class PgBeanProcessor extends BeanProcessor {
  static {
    org.apache.commons.dbutils.BeanProcessor.registerColumnHandler(new DateColumnHandler());
    org.apache.commons.dbutils.BeanProcessor.registerColumnHandler(new LocalTimeColumnHandler());
    org.apache.commons.dbutils.BeanProcessor.registerColumnHandler(new LocalDateColumnHandler());
    org.apache.commons.dbutils.BeanProcessor.registerColumnHandler(new LocalDateTimeColumnHandler());
    org.apache.commons.dbutils.BeanProcessor.registerColumnHandler(new OffsetDateTimeColumnHandler());
  }

  public PgBeanProcessor(Class<?> beanClass) {
    super(beanClass);
  }

  public PgBeanProcessor(Class<?> beanClass, Consumer<PgBeanProcessor> init) {
    this(beanClass);
    init.accept(this);
  }

  public static ColumnProcessor fromJson(JavaType javaType) {
    return (rs, index, propType) -> JsonUtil.fromJson(rs.getString(index), javaType);
  }

  public static ColumnProcessor fromJson() {
    return (rs, index, propType) -> JsonUtil.fromJson(rs.getString(index), propType);
  }

  public static ColumnProcessor fromArray() {
    return (rs, index, propType) -> ResultSetUtil.getArray(rs, index);
  }

  public static ColumnProcessor toCodeName() {
    return (rs, index, propType) -> {
      String code = rs.getString(index);
      return code == null ? null : new CodeName(code);
    };
  }

  public static ColumnProcessor toIdCodeName() {
    return (rs, index, propType) -> {
      Long id = ResultSetUtil.getLong(rs, index);
      return id == null ? null : new CodeName(id);
    };
  }

  public static ColumnProcessor toLocalDateRange() {
    return (rs, index, propType) -> new LocalDateRange(rs.getString(index));
  }

  public static ColumnProcessor toLocalDateTimeRange() {
    return (rs, index, propType) -> new LocalDateTimeRange(rs.getString(index));
  }

  public static ColumnProcessor toOffsetDateTimeRange() {
    return (rs, index, propType) -> new OffsetDateTimeRange(rs.getString(index));
  }

  public static RowProcessor toOffsetDateTimeRange(String fromField, String toField) {
    return toOffsetDateTimeRange(fromField, toField, true, true);
  }

  public static RowProcessor toOffsetDateTimeRange(String fromField, String toField, boolean fromInclusive, boolean toInclusive) {
    return rs -> {
      Timestamp from = rs.getTimestamp(fromField);
      Timestamp to = rs.getTimestamp(toField);
      OffsetDateTime fromDt = OffsetDateTime.ofInstant(from.toInstant(), ZoneId.systemDefault());
      OffsetDateTime toDt = to == null ? null : OffsetDateTime.ofInstant(to.toInstant(), ZoneId.systemDefault());
      OffsetDateTimeRange range = new OffsetDateTimeRange(fromDt, toDt);
      range.setLowerInclusive(fromInclusive);
      range.setUpperInclusive(toInclusive);
      return range;
    };
  }

  public static ColumnProcessor toEnum() {
    return (rs, index, propType) -> {
      if (!Enum.class.isAssignableFrom(propType)) {
        throw new RuntimeException("Cannot process column " + index + ", type" + propType + " is not an enum");
      }
      return JsonUtil.getObjectMapper().convertValue(rs.getString(index), propType);
    };
  }


  public PgBeanProcessor addNamesColumnProcessor() {
    return addColumnProcessor("names", fromJson());
  }

  public PgBeanProcessor addValidityProcessor() {
    return addColumnProcessor("valid", toLocalDateRange());
  }

  public PgBeanProcessor addSysColumnProcessors() {
    return addColumnProcessor("sys_created", "created", fromJson())
        .addColumnProcessor("sys_modified", "modified", fromJson());
  }
}
