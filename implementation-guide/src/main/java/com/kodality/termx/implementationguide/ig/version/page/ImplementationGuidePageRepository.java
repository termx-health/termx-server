package com.kodality.termx.implementationguide.ig.version.page;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import java.util.List;
import jakarta.inject.Singleton;

@Singleton
public class ImplementationGuidePageRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(ImplementationGuidePage.class, bp -> {
    bp.addColumnProcessor("group", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("space", PgBeanProcessor.fromJson());
  });

  private final static String select = "select igp.* , " +
            "(select json_build_object('id', igg.id, 'name', igg.name, 'description', igg.description) from sys.implementation_guide_group igg where igg.id = igp.group_id and igg.sys_status = 'A') as group, "+
            "(select json_build_object('id', s.id, 'code', s.code, 'names', s.names) from sys.space s where s.id = igp.space_id and s.sys_status = 'A') as space ";

  public void save(ImplementationGuidePage page, String ig, Long versionId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", page.getId());
    ssb.property("implementation_guide", ig);
    ssb.property("implementation_guide_version_id", versionId);
    ssb.property("space_id", page.getSpace().getId());
    ssb.property("page", page.getPage());
    ssb.property("name", page.getName());
    ssb.property("type", page.getType());
    ssb.property("group_id", page.getGroup().getId());
    SqlBuilder sb = ssb.buildSave("sys.implementation_guide_page", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    page.setId(id);
  }


  public void retain(List<ImplementationGuidePage> pages, String ig, Long versionId) {
    SqlBuilder sb = new SqlBuilder("update sys.implementation_guide_page set sys_status = 'C'");
    sb.append(" where implementation_guide = ? and implementation_guide_version_id = ? and sys_status = 'A'", ig, versionId);
    sb.andNotIn("id", pages, ImplementationGuidePage::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public List<ImplementationGuidePage> loadAll(Long versionId) {
    String sql = select + " from sys.implementation_guide_page igp where igp.sys_status = 'A' and igp.implementation_guide_version_id = ?";
    return getBeans(sql, bp, versionId);
  }
}
