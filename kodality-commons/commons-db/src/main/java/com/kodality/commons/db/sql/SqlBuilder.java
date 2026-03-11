package com.kodality.commons.db.sql;

import com.kodality.commons.util.JsonUtil;
import com.kodality.commons.util.PipeUtil;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import static java.util.stream.Collectors.toList;

@Slf4j
public class SqlBuilder implements Serializable {
  private final StringBuilder sb = new StringBuilder();
  private final List<Object> params = new ArrayList<>();

  public SqlBuilder() {
  }

  public SqlBuilder(String sql, Object... params) {
    append(sql, params);
  }

  public SqlBuilder add(Object... params) {
    if (params != null) {
      this.add(Arrays.asList(params));
    }
    return this;
  }

  public SqlBuilder add(Collection<?> params) {
    if (params != null) {
      this.params.addAll(params);
    }
    return this;
  }

  public SqlBuilder append(String sql, Object... params) {
    if (!isEmpty()) {
      sb.append(" ");
    }
    sb.append(sql);
    return add(params);
  }

  public SqlBuilder append(SqlBuilder sql) {
    return sql == null ? this : append(sql.getSql(), sql.getParams());
  }

  public SqlBuilder appendIfTrue(boolean condition, String sql, Object... params) {
    return condition ? append(sql, params) : this;
  }

  public SqlBuilder appendIfNotNull(String str, Object param) {
    return param == null ? this : append(str, param);
  }

  public <T> SqlBuilder appendIfNotNull(T param, BiConsumer<SqlBuilder, T> appender) {
    if (param != null) {
      appender.accept(this, param);
    }
    return this;
  }

  public SqlBuilder and() {
    return append("and");
  }

  public SqlBuilder not() {
    return append("not");
  }

  public SqlBuilder and(SqlBuilder sql) {
    return and().append(sql);
  }

  public SqlBuilder and(String str, Object... params) {
    return and().append(str, params);
  }

  public SqlBuilder or() {
    return append("or");
  }

  public SqlBuilder or(SqlBuilder sql) {
    return or().append(sql);
  }

  public SqlBuilder or(String str, Object... params) {
    return or().append(str, params);
  }

  public SqlBuilder eq(String str, Object param) {
    append(str);
    if (param == null) {
      return append("is null");
    }
    return append("=").append("?", param);
  }

  public SqlBuilder ne(String str, Object param) {
    append(str);
    if (param == null) {
      return append("is not null");
    }
    return append("!=").append("?", param);
  }

  public SqlBuilder in(String columnName, Object... params) {
    return in(columnName, Arrays.asList(params));
  }

  public SqlBuilder in(String columnName, String params) {
    return in(columnName, params, s -> s);
  }

  public SqlBuilder in(String columnName, String params, Function<String, Object> mapper) {
    if (params == null) {
      return append("true");
    }
    List<Object> parsedParams = Arrays.stream(StringUtils.split(params, ",")).map(mapper).collect(Collectors.toList());
    return in(columnName, parsedParams);
  }

  public SqlBuilder in(String columnName, Collection<?> params) {
    if (params == null) {
      return append("true");
    }
    if (params.isEmpty()) {
      return append("false");
    }
    if (params.size() == 1) {
      return eq(columnName, params.iterator().next());
    }
    String sql = columnName + " in (?" + StringUtils.repeat(",?", params.size() - 1) + ")";
    return append(sql, params.toArray());
  }

  public SqlBuilder notIn(String columnName, Object... params) {
    return notIn(columnName, Arrays.asList(params));
  }

  public SqlBuilder notIn(String columnName, Collection<?> params) {
    if (params == null || params.isEmpty()) {
      return append("true");
    }
    if (params.size() == 1) {
      return ne(columnName, params.iterator().next());
    }
    String sql = columnName + " not in (?" + StringUtils.repeat(",?", params.size() - 1) + ")";
    return append(sql, params.toArray());
  }

  public SqlBuilder notIn(String columnName, String params) {
    return notIn(columnName, params, s -> s);
  }

  public SqlBuilder notIn(String columnName, String params, Function<String, Object> mapper) {
    if (StringUtils.isEmpty(params)) {
      return append("true");
    }
    List<Object> parsedParams = Arrays.stream(StringUtils.split(params, ",")).map(mapper).collect(Collectors.toList());
    return notIn(columnName, parsedParams);
  }

  public <T> SqlBuilder andNotIn(String columnName, List<T> params, Function<T, Object> mapper) {
    if (params == null || params.isEmpty()) {
      return this;
    }
    List<Object> p = params.stream().map(mapper).filter(Objects::nonNull).collect(Collectors.toList());
    if (p.isEmpty()) {
      return this;
    }
    return this.and().notIn(columnName, p);
  }

  public SqlBuilder jsonContains(String columnName, Object pojo) {
    return append(columnName + " @>  ?", JsonUtil.toJson(pojo));
  }

  public SqlBuilder orderBy(String str) {
    return append("order by").append(str);
  }

  public SqlBuilder pipe(String keyField, String valueField, String pipeParam) {
    if (StringUtils.isEmpty(pipeParam)) {
      return this;
    }
    String[] pipe = PipeUtil.parsePipe(pipeParam);
    if (StringUtils.isEmpty(pipe[0])) {
      return eq(valueField, pipe[1]);
    }
    return append("(").eq(keyField, pipe[0]).and().eq(valueField, pipe[1]).append(")");
  }

  public Object[] getParams() {
    return params.toArray();
  }

  public List<Object> getParamsAsList() {
    return params;
  }

  public boolean isEmpty() {
    return sb.length() == 0;
  }

  public String getSql() {
    log.debug(this.toString());
    return sb.toString();
  }

  @Override
  public String toString() {
    return "SQL: [\n  " + sb.toString() + "\n], args: {\n  " + params + "\n}, pretty: {\n  " + getPretty() + "\n}";
  }

  public String toPrettyString() {
    return getPretty();
  }

  private String getPretty() {
    try {
      Object[] p = params.stream().map(param -> {
        if (param == null) {
          return "NULL";
        }
        if (param instanceof String) {
          return "'" + param + "'";
        }
        if (param instanceof LocalDate) {
          return "to_date('" + DateTimeFormatter.ofPattern("yyyy-MM-dd").format((LocalDate) param) + "', 'yyyy-MM-dd')";
        }
        if (param instanceof LocalDateTime) {
          return "'" + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format((LocalDateTime) param) + "'";
        }
        if (param instanceof Date) {
          return "'" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((Date) param) + "'";
        }
        return param.toString();
      }).collect(toList()).toArray();
      return String.format(sb.toString().replaceAll("%", "%%").replaceAll("\\?", "%s"), p);
    } catch (Exception e) {
      return "Could not make pretty: " + e.getMessage();
    }
  }

}
