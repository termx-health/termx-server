package com.kodality.termserver.ts.codesystem.concept;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.ConceptQueryParams;
import io.micronaut.core.util.StringUtils;
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

    SqlBuilder sb = ssb.buildUpsert("terminology.concept", "id");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public Concept load(Long id) {
    String sql = "select * from terminology.concept where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public Concept load(String codeSystem, String code) {
    String sql = "select * from terminology.concept where sys_status = 'A' and code_system = ? and code = ?";
    return getBean(sql, bp, codeSystem, code);
  }

  public QueryResult<Concept> query(ConceptQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from terminology.concept c where c.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from terminology.concept c where c.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(ConceptQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and c.code_system = ?", params.getCodeSystem());
    sb.appendIfNotNull("and exists (select 1 from terminology.code_system cs where cs.id = c.code_system and cs.uri = ?)", params.getCodeSystemUri());
    sb.appendIfNotNull("and c.code ~* ?", params.getCodeContains());
    if (StringUtils.isNotEmpty(params.getCode())) {
      sb.and().in("c.code ", params.getCode());
    }
    if (params.getCodeSystemVersion() != null ||
        params.getCodeSystemVersionReleaseDateGe() != null ||
        params.getCodeSystemVersionReleaseDateLe() != null ||
        params.getCodeSystemVersionExpirationDateGe() != null ||
        params.getCodeSystemVersionExpirationDateLe() != null) {
      sb.append("and exists (select 1 from terminology.code_system_version csv " +
          "inner join terminology.entity_version_code_system_version_membership evcsvm on evcsvm.code_system_version_id = csv.id and evcsvm.sys_status = 'A' " +
          "inner join terminology.code_system_entity_version csev on csev.id = evcsvm.code_system_entity_version_id and csev.sys_status = 'A' " +
          "where csev.code_system_entity_id = c.id and csv.sys_status = 'A'");
      sb.appendIfNotNull("and csv.version = ?", params.getCodeSystemVersion());
      sb.appendIfNotNull("and csv.release_date >= ?", params.getCodeSystemVersionReleaseDateGe());
      sb.appendIfNotNull("and csv.release_date <= ?", params.getCodeSystemVersionReleaseDateLe());
      sb.appendIfNotNull("and (csv.expiration_date >= ? or csv.expiration_date is null)", params.getCodeSystemVersionExpirationDateGe());
      sb.appendIfNotNull("and (csv.expiration_date <= ? or csv.expiration_date is null)", params.getCodeSystemVersionExpirationDateLe());
      sb.appendIfNotNull("and csv.code_system = ?", params.getCodeSystem());
      sb.append(")");
    }
    if (params.getValueSet() != null) {
      sb.and("(");
      sb.append("exists( select 1 from terminology.value_set_version vsv " +
          "inner join terminology.concept_value_set_version_membership cvsvm on cvsvm.value_set_version_id = vsv.id and cvsvm.sys_status = 'A' " +
          "where vsv.value_set = ? and vsv.sys_status = 'A' and cvsvm.concept_id = c.id)", params.getValueSet());
      sb.or();
      sb.append("exists( select 1 from terminology.value_set_expand(?, null, null) vse where vse.concept_id = c.id)", params.getValueSet());
      sb.append(")");
    }
    if (params.getValueSetVersion() != null) {
      sb.and("(");
      sb.append("exists( select 1 from terminology.value_set_version vsv " +
          "inner join terminology.concept_value_set_version_membership cvsvm on cvsvm.value_set_version_id = vsv.id and cvsvm.sys_status = 'A' " +
          "where vsv.version = ? and vsv.sys_status = 'A' and cvsvm.concept_id = c.id)", params.getValueSetVersion());
      sb.or();
      sb.append("exists( select 1 from terminology.value_set_expand(?, ?, null) vse where vse.concept_id = c.id)", params.getValueSet(), params.getValueSetVersion());
      sb.append(")");
    }
    sb.appendIfNotNull("and exists (select 1 from terminology.code_system_entity_version csev " +
        "where csev.code_system_entity_id = c.id and csev.sys_status = 'A' and csev.status = ?)", params.getCodeSystemEntityStatus());
    return sb;
  }
}
