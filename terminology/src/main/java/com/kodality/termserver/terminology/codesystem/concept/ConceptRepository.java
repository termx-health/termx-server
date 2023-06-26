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

  private final String select = "select distinct on (c.code) c.*, " +
      "(exists (select 1 from terminology.code_system_entity_version csev where csev.code_system_entity_id = c.id and csev.sys_status = 'A' and csev.status = 'active')) as immutable ";

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
    String sql = select + "from terminology.concept c where c.sys_status = 'A' and c.id = ?";
    return getBean(sql, bp, id);
  }

  public Concept load(List<String> codeSystems, String code) {
    SqlBuilder sb = new SqlBuilder(select + "from terminology.concept c where c.sys_status = 'A'");
    sb.and("c.code = ?", code);
    if (CollectionUtils.isNotEmpty(codeSystems)) {
      sb.and().in("c.code_system", codeSystems);
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
      SqlBuilder sb = new SqlBuilder(select + ", " +
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
    if (StringUtils.isNotEmpty(params.getTextContains()) || StringUtils.isNotEmpty(params.getTextEq()) || StringUtils.isNotEmpty(params.getDesignationCiEq())) {
      sb.append("and (");
      if (StringUtils.isNotEmpty(params.getTextContains())) {
        sb.append("terminology.text_search(c.code, c.description) like '%' || terminology.search_translate(?) || '%'", params.getTextContains());
        sb.append("or");
      }
      if (StringUtils.isNotEmpty(params.getTextEq())) {
        sb.append("terminology.search_translate(c.code) = terminology.search_translate(?)", params.getTextEq());
        sb.append("or terminology.search_translate(c.code) = terminology.search_translate(?)", params.getTextEq());
        sb.append("or");
      }
      sb.append("exists (select 1 from terminology.designation d where d.code_system_entity_version_id = csev.id and d.sys_status = 'A'");
      if (StringUtils.isNotEmpty(params.getTextContains())) {
        sb.append("and (terminology.text_search(d.name) like '%' || terminology.search_translate(?) || '%')", params.getTextContains());
      }
      if (StringUtils.isNotEmpty(params.getTextEq())) {
        sb.append("and (terminology.search_translate(d.name) = terminology.search_translate(?))", params.getTextEq());
      }
      if (StringUtils.isNotEmpty(params.getDesignationCiEq())) {
        sb.and().in("lower(d.name)", params.getDesignationCiEq().toLowerCase());
      }
      sb.append("))");
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
    sb.appendIfNotNull(params.getValueSetExpandResultIds(), (sql, s) -> sql.and().in("c.id", s, Long::valueOf));
    sb.appendIfNotNull("and csev.status = ?", params.getCodeSystemEntityStatus());
    if (StringUtils.isNotEmpty(params.getCodeSystemEntityVersionId())) {
      sb.and().in("csev.id", params.getCodeSystemEntityVersionId(), Long::valueOf);
    }
    if (StringUtils.isNotEmpty(params.getPropertyValues()) || StringUtils.isNotEmpty(params.getPropertyValuesPartial())) {
      sb.append(checkPropertyValue(params.getPropertyValues(), params.getPropertyValuesPartial()));
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
      String from1 =
          from.replaceAll("cc", "c1").replaceAll("csev", "csev1").replaceAll("csa_s", "csa_s1").replaceAll("csa_t", "csa_t1").replaceAll("c_s", "c_s1")
              .replaceAll("c_t", "c_t1");
      String from2 =
          from.replaceAll("cc", "c2").replaceAll("csev", "csev2").replaceAll("csa_s", "csa_s2").replaceAll("csa_t", "csa_t2").replaceAll("c_s", "c_s2")
              .replaceAll("c_t", "c_t2");
      if (StringUtils.isNotEmpty(params.getAssociationSourceRecursive())) {
        String[] pipe = PipeUtil.parsePipe(params.getAssociationSourceRecursive());
        sb.append("and c.code in (with recursive concept_codes as ( select c1.code, c_s1.code parent " + from1 + " where")
            .append("csa_s1.association_type = ? and c_t1.code = ?", pipe[0], pipe[1])
            .append(
                "union select c2.code, c_s2.code parent " + from2 + " inner join concept_codes rec on c_t2.code = rec.code) select code from concept_codes)");
      }
      if (StringUtils.isNotEmpty(params.getAssociationTargetRecursive())) {
        String[] pipe = PipeUtil.parsePipe(params.getAssociationTargetRecursive());
        sb.append("and c.code in (with recursive concept_codes as ( select c1.code, c_t1.code parent " + from1 + " where")
            .append("csa_t1.association_type = ? and c_s1.code = ?", pipe[0], pipe[1])
            .append(
                "union select c2.code, c_t2.code parent " + from2 + " inner join concept_codes rec on c_s2.code = rec.code) select code from concept_codes)");
      }
    }
    return sb;
  }

  private String checkPropertyValue(String propertyValues, String propertyValuesPartial) {
    SqlBuilder sb = new SqlBuilder();

    String values = StringUtils.isNotEmpty(propertyValuesPartial) ? propertyValuesPartial : propertyValues;
    boolean partial = StringUtils.isNotEmpty(propertyValuesPartial);

    sb.append("and (");
    sb.append(Arrays.stream(values.split(";")).map(pv -> {
      SqlBuilder sb1 = new SqlBuilder();
      sb1.append("(1<>1");
      sb1.append("or").append(checkProperty(pv));
      sb1.append("or").append(checkDesignation(pv));
      sb1.append("or").append(checkAssociation(pv));
      sb1.append("or").append(checkCode(pv, partial));
      sb1.append(")");
      return sb1.toPrettyString();
    }).collect(Collectors.joining(partial ? " or " : " and ")));
    sb.append(")");
    return sb.toPrettyString();
  }

  private String checkProperty(String propertyValue) {
    SqlBuilder sb = new SqlBuilder();
    String select = "select 1 from terminology.entity_property_value epv_1 " +
        "left join terminology.entity_property ep_1 on ep_1.id = epv_1.entity_property_id and ep_1.sys_status = 'A' " +
        "where epv_1.code_system_entity_version_id = csev.id and epv_1.sys_status = 'A'";
    sb.append(checkPropertyValue(propertyValue, "ep_1.name = ?", "coalesce((epv_1.value ->> 'code')::text, epv_1.value #>> '{}')", select));
    return sb.toPrettyString();
  }

  private String checkDesignation(String propertyValue) {
    SqlBuilder sb = new SqlBuilder();
    String select = "select 1 from terminology.designation d_1 " +
        "left join terminology.entity_property dp_1 on dp_1.id = d_1.designation_type_id and dp_1.sys_status = 'A' " +
        "where d_1.code_system_entity_version_id = csev.id and d_1.sys_status = 'A'";
    sb.append(checkPropertyValue(propertyValue, "dp_1.name = ?", "d_1.name", select));
    return sb.toPrettyString();
  }

  private String checkAssociation(String propertyValue) {
    SqlBuilder sb = new SqlBuilder();
    String select = "select 1 from terminology.code_system_association csa_1 " +
        "left join terminology.code_system_entity_version csev_1 on csev_1.id = csa_1.target_code_system_entity_version_id and csev_1.sys_status = 'A' " +
        "where csa_1.source_code_system_entity_version_id = csev.id and csa_1.sys_status = 'A'";
    sb.append(checkPropertyValue(propertyValue, "csa_1.association_type = ?", "csev_1.code", select));
    return sb.toPrettyString();
  }

  private String checkCode(String propertyValues, boolean partial) {
    SqlBuilder sb = new SqlBuilder();
    if ("code".equals(PipeUtil.parsePipe(propertyValues)[0])) {
      if (partial) {
        sb.append("c.code ~* ?", PipeUtil.parsePipe(propertyValues)[1]);
      } else {
        sb.append("c.code = ?", PipeUtil.parsePipe(propertyValues)[1]);
      }
    } else {
      sb.append("1 <> 1");
    }
    return sb.toPrettyString();
  }

  private String checkPropertyValue(String pv, String nameField, String valueField, String select) {
    String[] pipe = PipeUtil.parsePipe(pv);
    return new SqlBuilder()
        .append("exists (" + select)
        .and(nameField, pipe[0])
        .and().in(valueField, pipe[1])
        .append(")").toPrettyString();
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
            params.getCodeSystemEntityStatus(), params.getCodeSystemEntityVersionId(),
            params.getTextContains(), params.getTextEq(), params.getDesignationCiEq(),
            params.getPropertySource(), params.getPropertyRoot(),
            params.getAssociationRoot(), params.getAssociationSource(), params.getAssociationType(),
            params.getAssociationLeaf(), params.getAssociationTarget(),
            params.getCodeSystemVersion(), params.getCodeSystemVersionId(),
            params.getCodeSystemVersionReleaseDateLe(), params.getCodeSystemVersionReleaseDateGe(),
            params.getCodeSystemVersionExpirationDateLe(), params.getCodeSystemVersionExpirationDateLe(),
            params.getPropertyValues(), params.getPropertyValuesPartial())
        .filter(Objects::nonNull).toList())) {
      join += "left join terminology.code_system_entity_version csev on csev.code_system_entity_id = c.id and csev.sys_status = 'A'";
    }

    if (CollectionUtils.isNotEmpty(Stream.of(params.getPropertyRoot(), params.getPropertySource()).filter(Objects::nonNull).toList())) {
      join += "left join terminology.entity_property_value epv on epv.code_system_entity_version_id = csev.id and epv.sys_status = 'A' ";
    }

    if (CollectionUtils.isNotEmpty(
        Stream.of(params.getAssociationRoot(), params.getAssociationSource(), params.getAssociationType()).filter(Objects::nonNull).toList())) {
      join += "left join terminology.code_system_association csa_s on csa_s.source_code_system_entity_version_id = csev.id and csa_s.sys_status = 'A' ";
    }

    if (CollectionUtils.isNotEmpty(Stream.of(params.getAssociationLeaf(), params.getAssociationTarget()).filter(Objects::nonNull).toList())) {
      join += "left join terminology.code_system_association csa_t on csa_t.target_code_system_entity_version_id = csev.id and csa_t.sys_status = 'A' ";
    }

    if (CollectionUtils.isNotEmpty(Stream.of(params.getAssociationSource()).filter(Objects::nonNull).toList())) {
      join += "left join terminology.code_system_entity_version c_t on c_t.id = csa_s.target_code_system_entity_version_id and c_t.sys_status = 'A' ";
    }

    if (CollectionUtils.isNotEmpty(Stream.of(params.getAssociationTarget()).filter(Objects::nonNull).toList())) {
      join += "left join terminology.code_system_entity_version c_s on c_s.id = csa_t.source_code_system_entity_version_id and c_s.sys_status = 'A' ";
    }

    if (CollectionUtils.isNotEmpty(Stream.of(params.getCodeSystemVersion(), params.getCodeSystemVersionId(),
        params.getCodeSystemVersionReleaseDateLe(), params.getCodeSystemVersionReleaseDateGe(),
        params.getCodeSystemVersionExpirationDateLe(), params.getCodeSystemVersionExpirationDateLe()).filter(Objects::nonNull).toList())) {
      join +=
          "left join terminology.entity_version_code_system_version_membership evcsvm on evcsvm.code_system_entity_version_id = csev.id  and evcsvm.sys_status = 'A' " +
              "left join terminology.code_system_version csv on csv.id = evcsvm.code_system_version_id and csv.sys_status = 'A' ";
    }
    return join;
  }

}
