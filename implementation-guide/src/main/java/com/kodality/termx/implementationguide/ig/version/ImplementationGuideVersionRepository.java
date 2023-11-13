package com.kodality.termx.implementationguide.ig.version;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ImplementationGuideVersionRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(ImplementationGuideVersion.class);

  public void save(ImplementationGuideVersion version) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", version.getId());
    ssb.property("implementation_guide", version.getImplementationGuide());
    ssb.property("version", version.getVersion());
    ssb.property("status", version.getStatus());
    ssb.property("fhir_version", version.getFhirVersion());
    ssb.property("github_url", version.getGithubUrl());
    ssb.property("empty_github_url", version.getEmptyGithubUrl());
    ssb.property("template", version.getTemplate());
    ssb.property("algorithm", version.getAlgorithm());
    SqlBuilder sb = ssb.buildSave("sys.implementation_guide_version", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    version.setId(id);
  }

  public ImplementationGuideVersion load(String ig, String version) {
    String sql = "select * from sys.implementation_guide_version igv where igv.sys_status = 'A' and igv.implementation_guide = ? and igv.version = ?";
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
      SqlBuilder sb = new SqlBuilder("select igv.* from sys.implementation_guide_version igv " +
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
    sb.and().in("cs.code_system", params.getPermittedImplementationGuideIds());
    return sb;
  }
}
