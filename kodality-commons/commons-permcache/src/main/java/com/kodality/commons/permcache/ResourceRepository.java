package com.kodality.commons.permcache;

import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.util.JsonUtil;
import jakarta.inject.Singleton;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;


@Singleton
public class ResourceRepository extends BaseRepository {
  private static RowMapper<CacheResource> rm = new ResourceRowMapper();

  public CacheResource save(String resourceId, String resourceType, Object content) {
    String json = JsonUtil.toJson(content);

    SqlBuilder sb = new SqlBuilder();
    sb.append("with updated as (");
    sb.append("UPDATE cache.resource SET content = ?::jsonb, last_refreshed = now() where resource_id = ? and resource_type = ?", json, resourceId, resourceType);
    sb.append("RETURNING *)");
    sb.append(", inserted as (");
    sb.append("insert into cache.resource(resource_id, resource_type, content, last_refreshed) SELECT ?,?,?::jsonb, now()", resourceId, resourceType, json);
    sb.append("WHERE NOT EXISTS (SELECT 1 FROM updated) returning *)");
    sb.append("select * from updated union all select * from inserted");
    return jdbcTemplate.queryForObject(sb.getSql(), rm, sb.getParams());
  }

  public CacheResource load(Long id) {
    String sql = "select * from cache.resource where id = ?";
    try {
      return jdbcTemplate.queryForObject(sql, rm, id);
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  public CacheResource find(String resourceId, String resourceType) {
    String sql = "select * from cache.resource where resource_id = ? and resource_type = ?";
    try {
      return jdbcTemplate.queryForObject(sql, rm, resourceId, resourceType);
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  private static final class ResourceRowMapper implements RowMapper<CacheResource> {

    @Override
    public CacheResource mapRow(ResultSet rs, int x) throws SQLException {
      return new CacheResource()
          .setId(rs.getLong("id"))
          .setResourceId(rs.getString("resource_id"))
          .setResourecType(rs.getString("resource_type"))
          .setContent(JsonUtil.toMap(rs.getString("content")))
          .setLastRefreshed(OffsetDateTime.ofInstant(rs.getTimestamp("last_refreshed").toInstant(), ZoneId.systemDefault()));
    }
  }
}
