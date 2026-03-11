package com.kodality.commons.db.repo;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryParams;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.commons.util.PipeUtil;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.commons.dbutils.BeanProcessor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.Nullable;

import static java.util.stream.Collectors.joining;

public class BaseBeanRepository extends BaseRepository {
  @Inject
  @Nullable
  protected JdbcTemplate jdbcTemplate;

  protected JdbcTemplate getJdbcTemplate() {
    return jdbcTemplate;
  }

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

  protected static <T> RowMapper<T> rowMapper(BeanProcessor beanProcessor, Class<T> theClass) {
    return (rs, idx) -> beanProcessor.toBean(rs, theClass);
  }

}
