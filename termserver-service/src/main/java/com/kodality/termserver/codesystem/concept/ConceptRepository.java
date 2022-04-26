package com.kodality.termserver.codesystem.concept;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.ConceptQueryParams;
import jakarta.inject.Singleton;

@Singleton
public class ConceptRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(Concept.class);

  public void save(Concept concept) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", concept.getId());
    ssb.property("code", concept.getCode());
    ssb.property("code_system", concept.getCodeSystem());
    ssb.property("description", concept.getDescription());

    SqlBuilder sb = ssb.buildUpsert("concept", "id");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public Concept load(Long id) {
    String sql = "select * from concept where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public Concept load(String codeSystem, String code) {
    String sql = "select * from concept where sys_status = 'A' and code_system = ? and code = ?";
    return getBean(sql, bp, codeSystem, code);
  }

  public QueryResult<Concept> query(ConceptQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from concept c where c.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from concept c where c.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(ConceptQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and c.code ilike ? || '%'", params.getCode());
    sb.appendIfNotNull("and c.code_system = ?", params.getCodeSystem());
    if (params.getCodeSystemVersion() != null) {
      sb.append("and exists (select 1 from code_system_version csv " +
          "inner join entity_version_code_system_version_membership evcsvm on evcsvm.code_system_version_id = csv.id and evcsvm.sys_status = 'A' " +
          "inner join code_system_entity_version csev on csev.id = evcsvm.code_system_entity_version_id and csev.sys_status = 'A' " +
          "where csev.code_system_entity_id = c.id and csv.version = ? and csv.sys_status = 'A'", params.getCodeSystemVersion());
      sb.appendIfNotNull("and csv.code_system = ?", params.getCodeSystem());
      sb.append(")");
    }
    sb.appendIfNotNull("and exists( select 1 from value_set_version vsv where vsv.value_set = ? and vsv.sys_status = 'A' " +
        "inner join concept_value_set_version_membership cvsvm on cvsvm.value_set_version_id = vsv.id and cvsvm.sys_status = 'A' " +
        "where cvsvm.concept_id = d.id)", params.getValueSet());
    sb.appendIfNotNull("and exists( select 1 from value_set_version vsv where vsv.version = ? and vsv.sys_status = 'A' " +
        "inner join concept_value_set_version_membership cvsvm on cvsvm.value_set_version_id = vsv.id and cvsvm.sys_status = 'A' " +
        "where cvsvm.designation_id = d.id)", params.getValueSetVersion());
    return sb;
  }
}
