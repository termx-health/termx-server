package com.kodality.termx.bob;

import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import java.util.UUID;
import javax.inject.Singleton;

@Singleton
public class BobObjectRepository extends BaseRepository {
  private static final String select = """
      select json_build_object(
        'id', o.id,
        'uuid', o.uuid,
        'contentType', o.content_type,
        'meta', o.meta,
        'description', o.description,
        'storage', json_build_object(
          'id', os.id,
          'objectId', os.object_id,
          'storageType', os.storage_type,
          'container', os.container,
          'path', os.path,
          'filename', os.filename
          )
      ) from bob.object o inner join bob.object_storage os on os.sys_status = 'A' and os.object_id = o.id
      """;

  public QueryResult<BobObject> query(BobObjectQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from bob.object o inner join bob.object_storage os on os.sys_status = 'A' and os.object_id = o.id");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder(select);
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeansFromJson(sb.getSql(), BobObject.class, sb.getParams());
    });
  }

  private SqlBuilder filter(BobObjectQueryParams params) {
    SqlBuilder sb = new SqlBuilder("where o.sys_status = 'A'");
    sb.appendIfNotNull(params.getMeta(), (sql, p) -> sql.and("o.meta @> ?::jsonb", p));
    return sb;
  }

  public BobObject get(String uuid) {
    String sql = select + " where o.sys_status = 'A' and o.uuid = ?";
    return getBeanFromJson(sql, BobObject.class, uuid);
  }

  public String getUuid(Long objectId) {
    return queryForObject("select o.uuid from bob.object o where o.sys_status = 'A' and o.id = ?", String.class, objectId);
  }

  public Long create(BobObject obj) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("uuid", UUID.randomUUID());
    ssb.property("content_type", obj.getContentType());
    ssb.jsonProperty("meta", obj.getMeta());
    ssb.property("description", obj.getDescription());

    SqlBuilder sb = ssb.buildInsert("bob.object", "id");
    return jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
  }

  public Long createStorage(Long objectId, BobStorage s) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("object_id", objectId);
    ssb.property("storage_type", s.getStorageType());
    ssb.property("container", s.getContainer());
    ssb.property("path", s.getPath());
    ssb.property("filename", s.getFilename());

    SqlBuilder sb = ssb.buildInsert("bob.object_storage", "id");
    return jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
  }
}

