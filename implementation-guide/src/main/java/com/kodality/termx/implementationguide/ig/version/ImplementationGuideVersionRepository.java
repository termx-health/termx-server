package com.kodality.termx.implementationguide.ig.version;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.implementationguide.ig.version.group.ImplementationGuideGroup;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ImplementationGuideVersionRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(ImplementationGuideVersion.class, bp -> {
    bp.addColumnProcessor("depends_on", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("groups", PgBeanProcessor.fromJson(JsonUtil.getListType(ImplementationGuideGroup.class)));
  });

  private static final String select = "select igv.*, " +
      "(select jsonb_agg(igg.g) from (select json_build_object(" +
      "               'id', igg.id, " +
      "               'name', igg.name, " +
      "               'description', igg.description) as g " +
      "from sys.implementation_guide_group igg where igg.implementation_guide_version_id = igv.id and igg.sys_status = 'A') igg) as groups ";

  public void save(ImplementationGuideVersion version) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", version.getId());
    ssb.property("implementation_guide", version.getImplementationGuide());
    ssb.property("version", version.getVersion());
    ssb.property("status", version.getStatus());
    ssb.property("package_id", version.getPackageId());
    ssb.property("fhir_version", version.getFhirVersion());
    ssb.property("github_url", version.getGithubUrl());
    ssb.property("empty_github_url", version.getEmptyGithubUrl());
    ssb.property("template", version.getTemplate());
    ssb.property("algorithm", version.getAlgorithm());
    ssb.jsonProperty("depends_on", version.getDependsOn());
    SqlBuilder sb = ssb.buildSave("sys.implementation_guide_version", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    version.setId(id);
  }

  public ImplementationGuideVersion load(String ig, String version) {
    String sql = select + "from sys.implementation_guide_version igv where igv.sys_status = 'A' and igv.implementation_guide = ? and igv.version = ?";
    return getBean(sql, bp, ig, version);
  }

  public QueryResult<ImplementationGuideVersion> query(ImplementationGuideVersionQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from sys.implementation_guide_version igv " +
          "inner join sys.implementation_guide ig on ig.id = igv.implementation_guide and ig.sys_status = 'A' " +
          "where igv.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder(select + " from sys.implementation_guide_version igv " +
          "inner join sys.implementation_guide ig on ig.id = igv.implementation_guide and ig.sys_status = 'A' " +
          "where igv.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(ImplementationGuideVersionQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.and().in("ig.id", params.getImplementationGuideIds());
    sb.and().in("ig.id", params.getPermittedImplementationGuideIds());
    return sb;
  }

  public void changeStatus(String ig, String version, String status) {
    String sql = "update sys.implementation_guide_version set status = ? where implementation_guide = ? and version = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, status, ig, version, status);
  }
}
