package com.kodality.termserver.ts.codesystem.concept;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.PipeUtil;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.ConceptQueryParams;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.util.Arrays;
import java.util.stream.Collectors;

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
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and (c.code ~* ? or exists(select 1 from terminology.designation d " +
              "inner join terminology.code_system_entity_version csev on d.code_system_entity_version_id = csev.id where csev.code_system_entity_id = c.id and d.name ~* ?))",
          params.getTextContains(), params.getTextContains());
    }
    if (StringUtils.isNotEmpty(params.getCode())) {
      sb.and().in("c.code ", params.getCode());
    }
    if (params.getCodeSystemVersion() != null ||
        params.getCodeSystemVersionId() != null ||
        params.getCodeSystemVersionReleaseDateGe() != null ||
        params.getCodeSystemVersionReleaseDateLe() != null ||
        params.getCodeSystemVersionExpirationDateGe() != null ||
        params.getCodeSystemVersionExpirationDateLe() != null) {
      sb.append("and exists (select 1 from terminology.code_system_version csv " +
          "inner join terminology.entity_version_code_system_version_membership evcsvm on evcsvm.code_system_version_id = csv.id and evcsvm.sys_status = 'A' " +
          "inner join terminology.code_system_entity_version csev on csev.id = evcsvm.code_system_entity_version_id and csev.sys_status = 'A' " +
          "where csev.code_system_entity_id = c.id and csv.sys_status = 'A'");
      sb.appendIfNotNull("and csv.id = ?", params.getCodeSystemVersionId());
      sb.appendIfNotNull("and csv.version = ?", params.getCodeSystemVersion());
      sb.appendIfNotNull("and csv.release_date >= ?", params.getCodeSystemVersionReleaseDateGe());
      sb.appendIfNotNull("and csv.release_date <= ?", params.getCodeSystemVersionReleaseDateLe());
      sb.appendIfNotNull("and (csv.expiration_date >= ? or csv.expiration_date is null)", params.getCodeSystemVersionExpirationDateGe());
      sb.appendIfNotNull("and (csv.expiration_date <= ? or csv.expiration_date is null)", params.getCodeSystemVersionExpirationDateLe());
      sb.appendIfNotNull("and csv.code_system = ?", params.getCodeSystem());
      sb.append(")");
    }
    sb.appendIfNotNull("and exists( select 1 from terminology.value_set_expand(?) vse where (vse.concept ->> 'id')::bigint = c.id)",
        params.getValueSetVersionId());
    sb.appendIfNotNull("and exists (select 1 from terminology.code_system_entity_version csev " +
        "where csev.code_system_entity_id = c.id and csev.sys_status = 'A' and csev.status = ?)", params.getCodeSystemEntityStatus());
    sb.appendIfNotNull("and exists (select 1 from terminology.code_system_entity_version csev " +
        "where csev.code_system_entity_id = c.id and csev.sys_status = 'A' and csev.id = ?)", params.getCodeSystemEntityVersionId());
    if (StringUtils.isNotEmpty(params.getPropertyValues()) || StringUtils.isNotEmpty(params.getPropertyValuesPartial())) {
      sb.append("and exists (select 1 from terminology.entity_property_value epv " +
          "inner join terminology.entity_property ep on ep.id = epv.entity_property_id and ep.sys_status = 'A' " +
          "inner join terminology.code_system_entity_version csev on csev.id = epv.code_system_entity_version_id and csev.sys_status = 'A' " +
          "where csev.code_system_entity_id = c.id and epv.sys_status = 'A'");
      if (StringUtils.isNotEmpty(params.getPropertyValues())) {
        String[] propertyValues = params.getPropertyValues().split(",");
        sb.append("and (").append(Arrays.stream(propertyValues).map(pv -> {
              String[] pipe = PipeUtil.parsePipe(pv);
              return new SqlBuilder().append("ep.name = ?", pipe[0]).appendIfTrue(pipe.length == 2, "and epv.value @> to_jsonb(?::text)", pipe[1]).toPrettyString();
            }).collect(Collectors.joining(" and "))).append(")");
      }
      if (StringUtils.isNotEmpty(params.getPropertyValuesPartial())) {
        String[] propertyValues = params.getPropertyValuesPartial().split(",");
        sb.append("and (").append(Arrays.stream(propertyValues).map(pv -> {
          String[] pipe = PipeUtil.parsePipe(pv);
          return new SqlBuilder().append("ep.name = ?", pipe[0]).appendIfTrue(pipe.length == 2, "and epv.value @> to_jsonb(?::text)", pipe[1]).toPrettyString();
        }).collect(Collectors.joining(" or "))).append(")");
      }
      sb.append(")");
    }
    return sb;
  }
}
