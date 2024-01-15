package com.kodality.termx.core.sys.release.resource;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termx.sys.release.ReleaseResource;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class ReleaseResourceRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(ReleaseResource.class, bp -> {
    bp.addColumnProcessor("resource_names", PgBeanProcessor.fromJson());
  });

  public void save(Long releaseId, ReleaseResource resource) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", resource.getId());
    ssb.property("release_id", releaseId);
    ssb.property("resource_type", resource.getResourceType());
    ssb.property("resource_id", resource.getResourceId());
    ssb.property("resource_version", resource.getResourceVersion());
    ssb.jsonProperty("resource_names", resource.getResourceNames());
    ssb.property("error_count", resource.getErrorCount());

    SqlBuilder sb = ssb.buildSave("sys.release_resource", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    resource.setId(id);
  }

  public void cancel(Long releaseId, Long resourceId) {
    SqlBuilder sb = new SqlBuilder("update sys.release_resource set sys_status = 'C' " +
        "where release_id = ? and id = ? and sys_status = 'A'", releaseId, resourceId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public List<ReleaseResource> loadAll(Long releaseId) {
    SqlBuilder sb = new SqlBuilder("select * from sys.release_resource where sys_status = 'A' and release_id = ?", releaseId);
    return getBeans(sb.getSql(), bp, sb.getParams());
  }
}
