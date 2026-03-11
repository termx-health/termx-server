package com.kodality.commons.db.sql;

import com.kodality.commons.util.JsonUtil;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class SaveSqlBuilder {
  private final Map<String, String> fields = new LinkedHashMap<>();
  private final Map<String, Supplier<Object>> values = new LinkedHashMap<>();

  public void property(String field, String q, Supplier<Object> param) {
    fields.put(field, q);
    values.put(field, param);
  }

  public SaveSqlBuilder property(String field, Supplier<Object> param) {
    property(field, "?", param);
    return this;
  }

  public SaveSqlBuilder property(String field, String q, Object param) {
    property(field, q, () -> param);
    return this;
  }

  public SaveSqlBuilder property(String field, Object param) {
    property(field, () -> param);
    return this;
  }

  public SaveSqlBuilder jsonProperty(String field, Object param) {
    return jsonProperty(field, param, true);
  }

  public SaveSqlBuilder jsonProperty(String field, Object param, boolean trunc) {
    String q = trunc ? "core.jsonb_trunc(?::jsonb)" : "?::jsonb";
    property(field, q, () -> JsonUtil.toJson(param));
    return this;
  }

  public SaveSqlBuilder enumProperty(String field, Enum param) {
    property(field, "?", () -> JsonUtil.getObjectMapper().convertValue(param, String.class));
    return this;
  }

  public SqlBuilder buildSave(String table, String idField, String... whereFields) {
    if (values.containsKey(idField) && values.get(idField).get() != null) {
      return buildUpdate(table, idField, whereFields);
    }
    return buildInsert(table, idField);
  }

  public SqlBuilder buildInsert(String table, String idField) {
    List<String> changeFields = fields.keySet().stream().filter(f -> !f.equals(idField)).collect(toList());
    if (changeFields.isEmpty()) {
      throw new IllegalStateException("nothing to update");
    }
    return new SqlBuilder("insert into " + table).append(buildInsertParams(changeFields)).append("returning " + idField);
  }

  public SqlBuilder buildInsertParams() {
    return buildInsertParams(fields.keySet());
  }

  public SqlBuilder buildInsertParams(Collection<String> fields) {
    return new SqlBuilder().append("(")
        .append(StringUtils.join(fields, ","))
        .append(")")
        .append("select")
        .append(fields.stream().map(f -> this.fields.get(f)).collect(joining(", ")),
                fields.stream().map(f -> values.get(f).get()).collect(toList()).toArray());
  }

  public SqlBuilder buildUpdate(String table, String idField, String... whereFields) {
    List<String> changeFields = fields.keySet().stream().filter(f -> !f.equals(idField)).collect(toList());
    if (changeFields.isEmpty()) {
      throw new IllegalStateException("nothing to update");
    }
    SqlBuilder sb = new SqlBuilder("update " + table + " set");
    sb.append(buildUpdateParams(changeFields));
    sb.append("where").eq(idField, values.get(idField).get());
    if (whereFields != null) {
      Stream.of(whereFields).forEach(f -> sb.and().eq(f, values.get(f).get()));
    }
    sb.append("returning " + idField);
    return sb;
  }

  public SqlBuilder buildUpdateParams() {
    return buildUpdateParams(fields.keySet());
  }

  public SqlBuilder buildUpdateParams(Collection<String> fields) {
    return new SqlBuilder().append(fields.stream().map(f -> f + " = " + this.fields.get(f)).collect(joining(", ")),
                                   fields.stream().map(f -> values.get(f).get()).collect(toList()).toArray());
  }

  public SqlBuilder buildUpsert(String table, String... whereFields) {
    SqlBuilder insert = new SqlBuilder("insert into " + table).append(buildInsertParams());
    SqlBuilder update = new SqlBuilder("update " + table + " set").append(buildUpdateParams());
    update.append("where true");
    Stream.of(whereFields).forEach(f -> update.and().eq(f, values.get(f).get()));

    SqlBuilder sb = new SqlBuilder();
    sb.append("with upsert as (").append(update).append("returning *)");
    sb.append(insert).append("where not exists (select * from upsert)");
    return sb;
  }

}
