package com.kodality.termserver.terminology.codesystem.concept;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.PipeUtil;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.codesystem.ConceptQueryParams;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;

@Singleton
public class ConceptRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(Concept.class);

  private final Map<String, String> orderMapping = Map.of("code", "c.code");

  public void save(Concept concept) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", concept.getId());
    ssb.property("code", concept.getCode());
    ssb.property("code_system", concept.getCodeSystem());
    ssb.property("description", concept.getDescription());
    ssb.property("sys_status", "A");

    SqlBuilder sb = ssb.buildUpsert("terminology.concept", "id");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public Concept load(Long id) {
    String sql = "select * from terminology.concept where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public Concept load(List<String> codeSystems, String code) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.concept where sys_status = 'A'");
    sb.and("code = ?", code);
    if (CollectionUtils.isNotEmpty(codeSystems)) {
      sb.and().in("code_system", codeSystems);
    }
    return getBean(sb.getSql(), bp, sb.getParams());
  }

  public QueryResult<Concept> query(ConceptQueryParams params) {
    String join = getJoin(params);
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(distinct(c.code)) from terminology.concept c " + join);
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select distinct on (c.code) c.*, " +
          isLeaf(params) + "as leaf, " +
          childCount(params) + "as child_count " +
          "from terminology.concept c " + join);
      sb.append(filter(params));
      sb.append(order(params, orderMapping));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private String isLeaf(ConceptQueryParams params) {
    if (params.getPropertyRoot() != null || params.getPropertySource() != null) {
      Long propertyId = params.getPropertyRoot() != null ? params.getPropertyRoot() : Long.valueOf(PipeUtil.parsePipe(params.getPropertySource())[0]);
      return new SqlBuilder(" (not exists(select 1 from terminology.entity_property_value epv " +
          "where epv.sys_status = 'A' and epv.entity_property_id = ? and epv.value = to_jsonb(c.code::text))) ", propertyId).toPrettyString();
    }
    if (params.getAssociationRoot() != null || params.getAssociationSource() != null) {
      String association = params.getAssociationRoot() != null ? params.getAssociationRoot() : PipeUtil.parsePipe(params.getAssociationSource())[0];
      return new SqlBuilder(" (not exists(select 1 from terminology.code_system_association csa " +
          "inner join terminology.code_system_entity_version csev on csev.id = csa.target_code_system_entity_version_id and csev.sys_status = 'A' " +
          "where csa.sys_status = 'A' and csev.code_system_entity_id = c.id and csa.association_type = ?)) ", association).toPrettyString();
    }
    return " false ";
  }

  private String childCount(ConceptQueryParams params) {
    if (params.getPropertyRoot() != null || params.getPropertySource() != null) {
      Long propertyId = params.getPropertyRoot() != null ? params.getPropertyRoot() : Long.valueOf(PipeUtil.parsePipe(params.getPropertySource())[0]);
      return new SqlBuilder(" (select count(1) from terminology.entity_property_value epv " +
          "where epv.sys_status = 'A' and epv.entity_property_id = ? and epv.value = to_jsonb(c.code::text)) ", propertyId).toPrettyString();
    }
    if (params.getAssociationRoot() != null || params.getAssociationSource() != null) {
      String association = params.getAssociationRoot() != null ? params.getAssociationRoot() : PipeUtil.parsePipe(params.getAssociationSource())[0];
      return new SqlBuilder(" (select count(1) from terminology.code_system_association csa " +
          "inner join terminology.code_system_entity_version csev on csev.id = csa.target_code_system_entity_version_id and csev.sys_status = 'A' " +
          "where csa.sys_status = 'A' and csev.code_system_entity_id = c.id and csa.association_type = ?) ", association).toPrettyString();
    }
    return " 0 ";
  }

  private SqlBuilder filter(ConceptQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.append("where c.sys_status = 'A'");
    if (StringUtils.isNotEmpty(params.getId())) {
      sb.and().in("c.id ", params.getId(), Long::valueOf);
    }
    if (StringUtils.isNotEmpty(params.getCodeSystem())) {
      sb.and().in("c.code_system ", params.getCodeSystem());
    }
    if (CollectionUtils.isNotEmpty(params.getPermittedCodeSystems())) {
      sb.and().in("c.code_system", params.getPermittedCodeSystems());
    }
    sb.appendIfNotNull("and cs.uri = ?", params.getCodeSystemUri());
    sb.appendIfNotNull("and c.code ~* ?", params.getCodeContains());
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and (c.code ~* ? or d.name ~* ?)", params.getTextContains(), params.getTextContains());
    }
    if (StringUtils.isNotEmpty(params.getCode())) {
      sb.and().in("c.code ", params.getCode());
    }
    sb.appendIfNotNull("and csv.id = ?", params.getCodeSystemVersionId());
    sb.appendIfNotNull("and csv.version = ?", params.getCodeSystemVersion());
    sb.appendIfNotNull("and csv.release_date >= ?", params.getCodeSystemVersionReleaseDateGe());
    sb.appendIfNotNull("and csv.release_date <= ?", params.getCodeSystemVersionReleaseDateLe());
    sb.appendIfNotNull("and (csv.expiration_date >= ? or csv.expiration_date is null)", params.getCodeSystemVersionExpirationDateGe());
    sb.appendIfNotNull("and (csv.expiration_date <= ? or csv.expiration_date is null)", params.getCodeSystemVersionExpirationDateLe());
    if (StringUtils.isNotEmpty(params.getValueSetExpandResultIds())) {
      sb.and().in("c.id ", params.getValueSetExpandResultIds(), Long::valueOf);
    }
    sb.appendIfNotNull("and csev.status = ?", params.getCodeSystemEntityStatus());
    sb.appendIfNotNull("and csev.id = ?", params.getCodeSystemEntityVersionId());
    if (StringUtils.isNotEmpty(params.getPropertyValues()) || StringUtils.isNotEmpty(params.getPropertyValuesPartial())) {
      sb.append("and (1<>1");
      sb.append("or").append(checkProperty(params.getPropertyValues(), params.getPropertyValuesPartial()));
      sb.append("or").append(checkDesignation(params.getPropertyValues(), params.getPropertyValuesPartial()));
      sb.append("or").append(checkAssociation(params.getPropertyValues(), params.getPropertyValuesPartial()));
      sb.append("or").append(checkCode(params.getPropertyValues(), params.getPropertyValuesPartial()));
      sb.append(")");
    }
    sb.appendIfNotNull("and (epv.entity_property_id <> ? or epv.entity_property_id is null)", params.getPropertyRoot());
    sb.appendIfNotNull("and (csa_s.association_type <> ? or csa_s.association_type is null)", params.getAssociationRoot());
    sb.appendIfNotNull("and (csa_t.association_type <> ? or csa_t.association_type is null)", params.getAssociationLeaf());
    if (StringUtils.isNotEmpty(params.getPropertySource())) {
      String[] pipe = PipeUtil.parsePipe(params.getPropertySource());
      sb.append("and epv.entity_property_id = ? and epv.value = to_jsonb(?::text)", Long.valueOf(pipe[0]), pipe[1]);
    }
    if (StringUtils.isNotEmpty(params.getAssociationSource())) {
      String[] pipe = PipeUtil.parsePipe(params.getAssociationSource());
      sb.append("and csa_s.association_type = ? and c_t.code = ?", pipe[0], pipe[1]);
    }
    if (StringUtils.isNotEmpty(params.getAssociationTarget())) {
      String[] pipe = PipeUtil.parsePipe(params.getAssociationTarget());
      sb.append("and csa_t.association_type = ? and c_s.code = ?", pipe[0], pipe[1]);
    }
    sb.appendIfNotNull("and csa_s.association_type = ?", params.getAssociationType());
    if (StringUtils.isNotEmpty(params.getAssociationSourceRecursive()) || StringUtils.isNotEmpty(params.getAssociationTargetRecursive())) {
      String from = "from terminology.concept as cc " +
          "left join terminology.code_system_entity_version csev on csev.code_system_entity_id = cc.id and csev.sys_status = 'A' " +
          "left join terminology.code_system_association csa_s on csa_s.source_code_system_entity_version_id = csev.id and csa_s.sys_status = 'A' " +
          "left join terminology.code_system_association csa_t on csa_t.target_code_system_entity_version_id = csev.id and csa_t.sys_status = 'A' " +
          "left join terminology.code_system_entity_version c_s on c_s.id = csa_t.source_code_system_entity_version_id and c_s.sys_status = 'A' " +
          "left join terminology.code_system_entity_version c_t on c_t.id = csa_s.target_code_system_entity_version_id and c_t.sys_status = 'A'";
      String from1 = from.replaceAll("cc", "c1").replaceAll("csev", "csev1").replaceAll("csa_s", "csa_s1").replaceAll("csa_t", "csa_t1").replaceAll("c_s", "c_s1").replaceAll("c_t", "c_t1");
      String from2 = from.replaceAll("cc", "c2").replaceAll("csev", "csev2").replaceAll("csa_s", "csa_s2").replaceAll("csa_t", "csa_t2").replaceAll("c_s", "c_s2").replaceAll("c_t", "c_t2");
      if (StringUtils.isNotEmpty(params.getAssociationSourceRecursive())) {
        String[] pipe = PipeUtil.parsePipe(params.getAssociationSourceRecursive());
        sb.append("and c.code in (with recursive concept_codes as ( select c1.code, c_s1.code parent " + from1 + " where")
            .append("csa_s1.association_type = ? and c_t1.code = ?", pipe[0], pipe[1])
            .append("union select c2.code, c_s2.code parent " + from2 + " inner join concept_codes rec on c_t2.code = rec.code) select code from concept_codes)");
      }
      if (StringUtils.isNotEmpty(params.getAssociationTargetRecursive())) {
        String[] pipe = PipeUtil.parsePipe(params.getAssociationTargetRecursive());
        sb.append("and c.code in (with recursive concept_codes as ( select c1.code, c_t1.code parent " + from1 + " where")
            .append("csa_t1.association_type = ? and c_s1.code = ?", pipe[0], pipe[1])
            .append("union select c2.code, c_t2.code parent " + from2 + " inner join concept_codes rec on c_s2.code = rec.code) select code from concept_codes)");
      }
    }
    return sb;
  }

  private String checkCode(String propertyValues, String propertyValuesPartial) {
    SqlBuilder sb = new SqlBuilder();
    if (StringUtils.isNotEmpty(propertyValues) && "code".equals(PipeUtil.parsePipe(propertyValues)[0])) {
      sb.append("c.code = ?", PipeUtil.parsePipe(propertyValues)[1]);
    } else if (StringUtils.isNotEmpty(propertyValuesPartial) && "code".equals(PipeUtil.parsePipe(propertyValuesPartial)[0])) {
      sb.append("c.code ~* ?", PipeUtil.parsePipe(propertyValuesPartial)[1]);
    } else {
      sb.append("1 <> 1");
    }
    return sb.toPrettyString();
  }

  private String checkProperty(String propertyValues, String propertyValuesPartial) {
    SqlBuilder sb = new SqlBuilder();
    if (StringUtils.isNotEmpty(propertyValues)) {
      sb.append(checkPropertyValue(propertyValues, "ep.name = ?", "coalesce(to_jsonb((epv.value ->> 'code')::text), epv.value) @> to_jsonb(?::text)", true));
    }
    if (StringUtils.isNotEmpty(propertyValuesPartial)) {
      sb.append(checkPropertyValue(propertyValuesPartial, "ep.name = ?", "coalesce(to_jsonb((epv.value ->> 'code')::text), epv.value) @> to_jsonb(?::text)", false));
    }
    return sb.toPrettyString();
  }

  private String checkDesignation(String propertyValues, String propertyValuesPartial) {
    SqlBuilder sb = new SqlBuilder();
    if (StringUtils.isNotEmpty(propertyValues)) {
      sb.append(checkPropertyValue(propertyValues, "dp.name = ?", "d.name = ?", true));
    }
    if (StringUtils.isNotEmpty(propertyValuesPartial)) {
      sb.append(checkPropertyValue(propertyValuesPartial, "dp.name = ?", "d.name ~* ?", false));
    }
    return sb.toPrettyString();
  }

  private String checkAssociation(String propertyValues, String propertyValuesPartial) {
    SqlBuilder sb = new SqlBuilder();
    if (StringUtils.isNotEmpty(propertyValues)) {
      sb.append(checkPropertyValue(propertyValues, "at.code = ?", "c_t.code = ?", true));
    }
    if (StringUtils.isNotEmpty(propertyValuesPartial)) {
      sb.append(checkPropertyValue(propertyValuesPartial, "at.code = ?", "c_t.code = ?", false));
    }
    return sb.toPrettyString();
  }

  private String checkPropertyValue(String values, String nameField, String valueField, boolean exact) {
    SqlBuilder sb = new SqlBuilder();
    String[] propertyValues = values.split(",");
    sb.append("(").append(Arrays.stream(propertyValues).map(pv -> {
      String[] pipe = PipeUtil.parsePipe(pv);
      return new SqlBuilder().append(nameField, pipe[0]).appendIfTrue(pipe.length == 2, "and " + valueField, pipe[1]).toPrettyString();
    }).collect(Collectors.joining(exact ? " and " : " or "))).append(")");
    return sb.toPrettyString();
  }

  public void batchUpsert(List<Concept> concepts) {
    String query = "insert into terminology.concept (id, code, code_system, description) values (?,?,?,?) " +
        "on conflict (code_system, code) where sys_status = 'A' do update " +
        "set code = excluded.code, description = excluded.description ";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement ps, int i) throws SQLException {
        ps.setLong(1, concepts.get(i).getId());
        ps.setString(2, concepts.get(i).getCode());
        ps.setString(3, concepts.get(i).getCodeSystem());
        ps.setString(4, concepts.get(i).getDescription());
      }

      @Override
      public int getBatchSize() {
        return concepts.size();
      }
    });
  }

  public void cancel(Long id) {
    SqlBuilder sb = new SqlBuilder("update terminology.concept set sys_status = 'C' where id = ? and sys_status = 'A'", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void refreshClosureView() {
    String sql = "select terminology.refresh_concept_closure()";
    jdbcTemplate.queryForObject(sql, String.class);
  }

  private String getJoin(ConceptQueryParams params) {
    String join = "";
    if (params.getCodeSystemUri() != null) {
      join += "left join terminology.code_system cs on cs.id = c.code_system and cs.sys_status = 'A' ";
    }
    if (CollectionUtils.isNotEmpty(Stream.of(
            StringUtils.isEmpty(params.getTextContains()) ? null : params.getTextContains(),
            params.getCodeSystemVersionId(), params.getCodeSystemVersion(),
            params.getCodeSystemVersionReleaseDateGe(), params.getCodeSystemVersionReleaseDateLe(), params.getCodeSystemVersionExpirationDateGe(), params.getCodeSystemVersionExpirationDateLe(),
            params.getCodeSystemEntityStatus(), params.getCodeSystemEntityVersionId(),
            params.getPropertyValues(), params.getPropertyValuesPartial(),
            params.getPropertyRoot(), params.getAssociationRoot(), params.getAssociationLeaf(),
            params.getPropertySource(), params.getAssociationSource(), params.getAssociationTarget(), params.getAssociationType(), params.getAssociationSourceRecursive(), params.getAssociationTargetRecursive())
        .filter(Objects::nonNull).toList())) {
      join += "left join terminology.code_system_entity_version csev on csev.code_system_entity_id = c.id and csev.sys_status = 'A' " +
          "left join terminology.designation d on d.code_system_entity_version_id = csev.id and d.sys_status = 'A' " +
          "left join terminology.entity_property_value epv on epv.code_system_entity_version_id = csev.id and epv.sys_status = 'A' " +
          "left join terminology.entity_property ep on ep.id = epv.entity_property_id and ep.sys_status = 'A' " +
          "left join terminology.entity_property dp on dp.id = d.designation_type_id and dp.sys_status = 'A' " +
          "left join terminology.code_system_association csa_s on csa_s.source_code_system_entity_version_id = csev.id and csa_s.sys_status = 'A' " +
          "left join terminology.code_system_association csa_t on csa_t.target_code_system_entity_version_id = csev.id and csa_t.sys_status = 'A' " +
          "left join terminology.code_system_entity_version c_t on c_t.id = csa_s.target_code_system_entity_version_id and c_t.sys_status = 'A' " +
          "left join terminology.code_system_entity_version c_s on c_s.id = csa_t.source_code_system_entity_version_id and c_s.sys_status = 'A' " +
          "left join terminology.association_type at on (at.code = csa_s.association_type or at.code = csa_t.association_type) and at.sys_status = 'A' " +
          "left join terminology.entity_version_code_system_version_membership evcsvm on evcsvm.code_system_entity_version_id = csev.id  and evcsvm.sys_status = 'A' " +
          "left join terminology.code_system_version csv on csv.id = evcsvm.code_system_version_id and csv.sys_status = 'A' ";
    }
    return join;
  }

}
