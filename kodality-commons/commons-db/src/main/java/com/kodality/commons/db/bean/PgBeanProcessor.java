package com.kodality.commons.db.bean;

import com.fasterxml.jackson.databind.JavaType;
import com.kodality.commons.db.resultset.ResultSetUtil;
import com.kodality.commons.model.CodeName;
import com.kodality.commons.util.JsonUtil;
import com.kodality.commons.util.range.LocalDateRange;
import com.kodality.commons.util.range.LocalDateTimeRange;
import com.kodality.commons.util.range.OffsetDateTimeRange;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.function.Consumer;
import org.apache.commons.lang3.EnumUtils;

public class PgBeanProcessor extends BeanProcessor {
  static {
    registerColumnHandler(new DateColumnHandler());
    registerColumnHandler(new LocalTimeColumnHandler());
    registerColumnHandler(new LocalDateColumnHandler());
    registerColumnHandler(new LocalDateTimeColumnHandler());
    registerColumnHandler(new OffsetDateTimeColumnHandler());
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

  public static ColumnProcessor fromInterval() {
    return (rs, index, propType) -> ResultSetUtil.getInterval(rs, index);
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

  @Deprecated(forRemoval = true)
  public static <E extends Enum<E>> ColumnProcessor toEnum(Class<E> enumClass) {
    return (rs, index, propType) -> EnumUtils.getEnum(enumClass, rs.getString(index));
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
