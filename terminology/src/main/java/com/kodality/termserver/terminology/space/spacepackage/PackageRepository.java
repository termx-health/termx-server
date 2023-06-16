package com.kodality.termserver.terminology.space.spacepackage;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termserver.ts.space.spacepackage.Package;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class PackageRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(Package.class);

  public void save(Package p, Long spaceId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", p.getId());
    ssb.property("space_id", spaceId);
    ssb.property("code", p.getCode());
    ssb.property("status", p.getStatus());
    ssb.property("git", p.getGit());

    SqlBuilder sb = ssb.buildSave("terminology.package", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    p.setId(id);
  }

  public Package load(Long id) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.package where id = ? and sys_status = 'A'", id);
    return getBean(sb.getSql(), bp, sb.getParams());
  }

  public Package load(Long spaceId, String code) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.package where space_id = ? and code = ? and sys_status = 'A'", spaceId, code);
    return getBean(sb.getSql(), bp, sb.getParams());
  }

  public List<Package> loadAll(Long spaceId) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.package where space_id = ? and sys_status = 'A'", spaceId);
    return getBeans(sb.getSql(), bp, sb.getParams());
  }

  public void delete(Long id) {
    SqlBuilder sb = new SqlBuilder("update terminology.package set sys_status = 'C' where id = ? and sys_status = 'A'", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
