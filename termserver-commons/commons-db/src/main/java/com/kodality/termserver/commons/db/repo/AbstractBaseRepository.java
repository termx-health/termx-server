package com.kodality.termserver.commons.db.repo;

import com.kodality.termserver.commons.db.bean.PgBeanProcessor;
import com.kodality.termserver.commons.db.sql.SqlBuilder;
import com.kodality.termserver.commons.model.model.QueryParams;
import com.kodality.termserver.commons.model.model.QueryResult;
import com.kodality.termserver.commons.util.PipeUtil;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.commons.dbutils.BeanProcessor;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import static java.util.stream.Collectors.joining;

public abstract class AbstractBaseRepository {

  protected abstract JdbcTemplate getJdbcTemplate();

  protected <T> T getBean(String sql, Class<T> theClass, Object... args) {
    return getBean(sql, new PgBeanProcessor(theClass), args);
  }

  protected <T> T getBean(String sql, PgBeanProcessor beanProcessor, Object... args) {
    return getJdbcTemplate().query(sql, rs -> {
      if (!rs.next()) {
        return null;
      }
      return beanProcessor.toBean(rs);
    }, args);
  }

  protected <T> List<T> getBeans(String sql, Class<T> theClass, Object... args) {
    return getBeans(sql, new PgBeanProcessor(theClass), args);
  }

  protected <T> List<T> getBeans(String sql, PgBeanProcessor beanProcessor, Object... args) {
    return getJdbcTemplate().query(sql, (ResultSetExtractor<List<T>>) beanProcessor::toBeanList, args);
  }

  protected <T> T queryForObject(String sql, Class<T> theClass, Object... args) {
    try {
      return getJdbcTemplate().queryForObject(sql, theClass, args);
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  protected <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
    try {
      return getJdbcTemplate().queryForObject(sql, rowMapper, args);
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  protected void handleException(DataAccessException exception, Map<String, Function<Exception, RuntimeException>> stateMapping) {
    if (exception.getCause() instanceof PSQLException e) {
      String state = e.getSQLState();

      if (stateMapping.containsKey(state)) {
        throw stateMapping.get(state).apply(e);
      }
    }
    throw exception;
  }

  protected static <T> RowMapper<T> rowMapper(BeanProcessor beanProcessor, Class<T> theClass) {
    return (rs, idx) -> beanProcessor.toBean(rs, theClass);
  }

  protected static SqlBuilder upsert(SqlBuilder insert, SqlBuilder update) {
    SqlBuilder sb = new SqlBuilder();
    sb.append("WITH UPSERT AS (");
    sb.append(update);
    sb.append(" RETURNING *)");
    sb.append(insert);
    sb.append("WHERE NOT EXISTS (SELECT * FROM upsert)");
    return sb;
  }

  protected static <Q extends QueryParams, R> QueryResult<R> query(Q params, Function<Q, Integer> count, Function<Q, List<R>> query) {
    Integer total = count.apply(params);
    QueryResult<R> result = new QueryResult<>(total, params);
    if (total > 0 && (params.getLimit() > 0 || params.getLimit() == -1)) {
      result.setData(query.apply(params));
    } else {
      result.setData(Collections.emptyList());
    }
    return result;
  }

  protected static SqlBuilder limit(QueryParams params) {
    return limit(params.getLimit(), params.getOffset());
  }

  private static SqlBuilder limit(Integer limit, Integer offset) {
    SqlBuilder sb = new SqlBuilder();
    if (limit == -1) {
      return sb;
    }
    return sb.append("offset ? rows fetch first (?) rows only", offset, limit);
  }

  protected static SqlBuilder order(QueryParams params, Map<String, ?> columnMapping) {
    return order(params.getSort(), columnMapping);
  }

  private static SqlBuilder order(List<String> params, Map<String, ?> columnMapping) {
    SqlBuilder sb = new SqlBuilder();
    if (params == null || params.isEmpty()) {
      return sb;
    }

    sb.append("order by").append(
        params.stream().map(param -> {
          SqlBuilder ssb = parseSort(param, columnMapping);
          sb.add(ssb.getParams());
          return ssb.getSql();
        }).collect(joining(", ")));
    return sb;
  }

  private static SqlBuilder parseSort(String param, Map<String, ?> columnMapping) {
    String field = param;
    String sortArg = null;
    String direction = "asc";
    if (field.startsWith("-")) {
      direction = "desc";
      field = field.substring(1);
    }
    if (field.contains("|")) {
      String[] pipe = PipeUtil.parsePipe(field);
      field = pipe[0];
      sortArg = pipe[1];
    }
    if (columnMapping.get(field) == null) {
      throw new IllegalArgumentException("Order parameter '" + field + "' should be mapped!");
    }
    Object mapping = columnMapping.get(field);
    if (mapping instanceof Function) {
      return ((Function<String, SqlBuilder>) mapping).apply(sortArg).append(direction);
    }
    return new SqlBuilder(mapping + " " + direction);
  }

  /**
   * alias.field -> alias_field
   */
  protected static String prefix(String alias, String... fields) {
    return Stream.of(fields).map(f -> String.format("%1$s.%2$s %1$s_%2$s", alias, f)).collect(joining(", "));
  }

}
