package com.kodality.termx.core.sys.spacepackage;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termx.sys.spacepackage.Package;
import java.util.List;
import jakarta.inject.Singleton;

@Singleton
public class PackageRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(Package.class);

  public void save(Package p, Long spaceId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", p.getId());
    ssb.property("space_id", spaceId);
    ssb.property("code", p.getCode());
    ssb.property("status", p.getStatus());

    SqlBuilder sb = ssb.buildSave("sys.package", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    p.setId(id);
  }

  public Package load(Long spaceId, Long id) {
    SqlBuilder sb = new SqlBuilder("select * from sys.package where space_id = ? and id = ? and sys_status = 'A'", spaceId, id);
    return getBean(sb.getSql(), bp, sb.getParams());
  }

  public Package load(Long spaceId, String code) {
    SqlBuilder sb = new SqlBuilder("select * from sys.package where space_id = ? and code = ? and sys_status = 'A'", spaceId, code);
    return getBean(sb.getSql(), bp, sb.getParams());
  }

  public List<Package> loadAll(Long spaceId) {
    SqlBuilder sb = new SqlBuilder("select * from sys.package where space_id = ? and sys_status = 'A'", spaceId);
    return getBeans(sb.getSql(), bp, sb.getParams());
  }

  public void delete(Long spaceId, Long id) {
    SqlBuilder sb = new SqlBuilder("update sys.package set sys_status = 'C' where space_id = ? and id = ? and sys_status = 'A'", spaceId, id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
