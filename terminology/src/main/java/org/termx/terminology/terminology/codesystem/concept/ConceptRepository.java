package org.termx.terminology.terminology.codesystem.concept;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.PipeUtil;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.ConceptQueryParams;
import org.termx.ts.codesystem.ConceptTreeItem;
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

  private final Map<String, String> orderMapping = Map.of("code", "c.code", "codeSystem", "c.code_system");

  private final String select = "select distinct on (c.code) c.*, " +
      "(exists (select 1 from terminology.code_system_entity_version csev where csev.code_system_entity_id = c.id and csev.sys_status = 'A' and csev.status in ('active', 'retired'))) as immutable ";

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

  public List<Concept> batchLoad(Map<String, List<String>> codeSystemToCodes) {
    if (codeSystemToCodes == null || codeSystemToCodes.isEmpty()) {
      return List.of();
    }
    
    SqlBuilder sb = new SqlBuilder(select + "from terminology.concept c where c.sys_status = 'A' and (");
    boolean first = true;
    for (Map.Entry<String, List<String>> entry : codeSystemToCodes.entrySet()) {
      if (!first) {
        sb.append(" or ");
      }
      sb.append("(c.code_system = ?", entry.getKey());
      sb.and().in("c.code", entry.getValue());
      sb.append(")");
      first = false;
    }
    sb.append(")");
    return getBeans(sb.getSql(), bp, sb.getParams());
  }

  public QueryResult<Concept> query(ConceptQueryParams params) {
    String join = getJoin(params);
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(distinct(c.code)) from terminology.concept c " + join);
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from ("+ select + ", " +
          isLeaf(params) + "as leaf, " +
          childCount(params) + "as child_count " +
          "from terminology.concept c " + join);
      sb.append(filter(params));
      sb.append(") c ");
      sb.append(order(params, orderMapping));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  public List<ConceptTreeItem> queryTree(ConceptTreeQueryParams params, List<String> matchedCodes) {
    if (CollectionUtils.isEmpty(matchedCodes)) {
      return List.of();
    }

    SqlBuilder sb = new SqlBuilder();
    sb.append("with recursive tree as (");
    sb.append(" select c.id, c.code_system, c.code, c.description, parent.parent_code, true as matched, array[c.code]::text[] as visited, 0 as depth ");
    sb.append(" from terminology.concept c ");
    sb.append(" left join lateral (").append(buildDirectParentSelect("c", params)).append(") parent on true ");
    sb.append(" where c.sys_status = 'A' ");
    sb.and().in("c.code_system", splitCsv(params.getMatchParams().getCodeSystem()));
    sb.and().in("c.code", matchedCodes);
    sb.append(" union all ");
    sb.append(" select p.id, p.code_system, p.code, p.description, parent.parent_code, false as matched, tree.visited || p.code, tree.depth + 1 ");
    sb.append(" from tree ");
    sb.append(" join terminology.concept p on p.code = tree.parent_code and p.code_system = tree.code_system and p.sys_status = 'A' ");
    sb.append(" left join lateral (").append(buildDirectParentSelect("p", params)).append(") parent on true ");
    sb.append(" where tree.parent_code is not null and not p.code = any(tree.visited) ");
    sb.append(") ");
    sb.append("select distinct on (code_system, code, parent_code) id, code_system, code, description, parent_code, matched ");
    sb.append("from tree ");
    sb.append("order by code_system, code, parent_code nulls first, matched desc, depth asc");
    return getBeans(sb.getSql(), new PgBeanProcessor(ConceptTreeItem.class), sb.getParams());
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
    sb.and().in("c.code_system", params.getPermittedCodeSystems());
    if (StringUtils.isNotEmpty(params.getId())) {
      sb.and().in("c.id ", params.getId(), Long::valueOf);
    }
    if (StringUtils.isNotEmpty(params.getCodeSystem())) {
      sb.and().in("c.code_system ", params.getCodeSystem());
    }
    if (StringUtils.isNotEmpty(params.getCodeSystemVersionCodeSystem()) && CollectionUtils.isNotEmpty(Stream.of(params.getCodeSystemVersion(), params.getCodeSystemVersionId(), params.getCodeSystemVersions(),
        params.getCodeSystemVersionReleaseDateLe(), params.getCodeSystemVersionReleaseDateGe(),
        params.getCodeSystemVersionExpirationDateLe(), params.getCodeSystemVersionExpirationDateLe()).filter(Objects::nonNull).toList())) {
      sb.and().in("csv.code_system ", params.getCodeSystemVersionCodeSystem());
    }
    sb.appendIfNotNull("and cs.uri = ?", params.getCodeSystemUri());
    sb.appendIfNotNull("and c.code ~* ?", params.getCodeContains());
    if (StringUtils.isNotEmpty(params.getTextContains()) || StringUtils.isNotEmpty(params.getTextEq()) || StringUtils.isNotEmpty(params.getDesignationCiEq())) {
      sb.append("and (");
      if (StringUtils.isNotEmpty(params.getTextContains())) {
        sb.append("terminology.text_search(c.code, c.description) like '%'");
        split(params.getTextContains(), params.getTextContainsSep()).forEach(t -> sb.append("|| terminology.search_translate(?) || '%'", t));
        sb.append("or");
      }
      if (StringUtils.isNotEmpty(params.getTextEq())) {
        sb.append("terminology.text_search(c.code, c.description) like '`' || terminology.search_translate(?) || '`%'", params.getTextEq());
        sb.append("or");
      }
      sb.append("exists (select 1 from terminology.designation d where d.code_system_entity_version_id = csev.id and d.sys_status = 'A'");
      if (StringUtils.isNotEmpty(params.getTextContains())) {
        sb.append("and (terminology.text_search(d.name) like '%'");
        split(params.getTextContains(), params.getTextContainsSep()).forEach(t -> sb.append("|| terminology.search_translate(?) || '%'", t));
        sb.append(")");
      }
      if (StringUtils.isNotEmpty(params.getTextEq())) {
        sb.append("and (terminology.text_search(d.name) = '`' || terminology.search_translate(?) || '`')", params.getTextEq());
      }
      if (StringUtils.isNotEmpty(params.getDesignationCiEq())) {
        sb.and().in("lower(d.name)", params.getDesignationCiEq().toLowerCase());
      }
      sb.append("))");
      appendUcumSupplementTextFilter(sb, params);
    }
    if (StringUtils.isNotEmpty(params.getCodeEq())) {
      sb.and("c.code = ?", params.getCodeEq());
    }
    if (StringUtils.isNotEmpty(params.getCode())) {
      sb.and().in("c.code ", params.getCode());
    }
    if (CollectionUtils.isNotEmpty(params.getCodes())) {
      sb.and().in("c.code ", params.getCodes());
    }
    sb.appendIfNotNull("and csv.id = ?", params.getCodeSystemVersionId());
    sb.appendIfNotNull("and csv.version = ?", params.getCodeSystemVersion());
    if (StringUtils.isNotEmpty(params.getCodeSystemVersions())) {
      sb.append(checkCodeSystemVersions(params.getCodeSystemVersions()));
    }
    sb.appendIfNotNull("and csv.release_date >= ?", params.getCodeSystemVersionReleaseDateGe());
    sb.appendIfNotNull("and csv.release_date <= ?", params.getCodeSystemVersionReleaseDateLe());
    sb.appendIfNotNull("and (csv.expiration_date >= ? or csv.expiration_date is null)", params.getCodeSystemVersionExpirationDateGe());
    sb.appendIfNotNull("and (csv.expiration_date <= ? or csv.expiration_date is null)", params.getCodeSystemVersionExpirationDateLe());
    sb.appendIfNotNull("and csev.status = ?", params.getCodeSystemEntityStatus());
    if (StringUtils.isNotEmpty(params.getCodeSystemEntityVersionId())) {
      sb.and().in("csev.id", params.getCodeSystemEntityVersionId(), Long::valueOf);
    }
    if (StringUtils.isNotEmpty(params.getProperties())) {
      sb.and().in("epv.entity_property_id", params.getProperties(), Long::valueOf);
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
    String msaBase = "select 1 from terminology.map_set_association msa where msa.sys_status = 'A' and msa.source_code_system = c.code_system and msa.source_code = c.code ";
    sb.appendIfNotNull("and not exists(" + msaBase + "and msa.map_set_version_id = ?)", params.getUnmapedInMapSetVersionId());
    sb.appendIfNotNull("and exists(" + msaBase + "and msa.map_set_version_id = ? and msa.verified = true)", params.getVerifiedInMapSetVersionId());
    sb.appendIfNotNull("and exists(" + msaBase + "and msa.map_set_version_id = ? and msa.verified = false)", params.getUnverifiedInMapSetVersionId());
    return sb;
  }

  private void appendUcumSupplementTextFilter(SqlBuilder sb, ConceptQueryParams params) {
    if (!Boolean.TRUE.equals(params.getIncludeSupplement())) {
      return;
    }
    if (StringUtils.isNotEmpty(params.getCodeSystem()) && Arrays.stream(params.getCodeSystem().split(",")).noneMatch("ucum"::equals)) {
      return;
    }

    sb.append("or exists (");
    sb.append("select 1 from terminology.code_system cs_sup ");
    sb.append("inner join (");
    sb.append("  select distinct on (csv.code_system) csv.id, csv.code_system ");
    sb.append("  from terminology.code_system_version csv ");
    sb.append("  where csv.sys_status = 'A' and csv.status = 'active' ");
    sb.append("  order by csv.code_system, coalesce(csv.release_date, now()) desc, csv.created desc nulls last, csv.version desc");
    sb.append(") latest_sup on latest_sup.code_system = cs_sup.id ");
    sb.append("inner join terminology.concept c_sup on c_sup.code_system = cs_sup.id and c_sup.code = c.code and c_sup.sys_status = 'A' ");
    sb.append("inner join terminology.code_system_entity_version csev_sup on csev_sup.code_system_entity_id = c_sup.id and csev_sup.sys_status = 'A' ");
    sb.append("inner join terminology.entity_version_code_system_version_membership evcsvm_sup on evcsvm_sup.code_system_entity_version_id = csev_sup.id and evcsvm_sup.code_system_version_id = latest_sup.id and evcsvm_sup.sys_status = 'A' ");
    sb.append("inner join terminology.designation d_sup on d_sup.code_system_entity_version_id = csev_sup.id and d_sup.sys_status = 'A' ");
    sb.append("where cs_sup.sys_status = 'A' and cs_sup.content = 'supplement' and cs_sup.base_code_system = c.code_system and c.code_system = 'ucum' ");
    if (StringUtils.isNotEmpty(params.getDisplayLanguage())) {
      sb.append("and (d_sup.language = ? or d_sup.language like ?)", params.getDisplayLanguage(), params.getDisplayLanguage() + "-%");
    }
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and (terminology.text_search(d_sup.name) like '%'");
      split(params.getTextContains(), params.getTextContainsSep()).forEach(t -> sb.append("|| terminology.search_translate(?) || '%'", t));
      sb.append(")");
    }
    if (StringUtils.isNotEmpty(params.getTextEq())) {
      sb.append("and (terminology.text_search(d_sup.name) = '`' || terminology.search_translate(?) || '`')", params.getTextEq());
    }
    if (StringUtils.isNotEmpty(params.getDesignationCiEq())) {
      sb.and().in("lower(d_sup.name)", params.getDesignationCiEq().toLowerCase());
    }
    sb.append(")");
  }

  private String checkCodeSystemVersions(String codeSystemVersions) {
    SqlBuilder sb = new SqlBuilder();
    sb.append("and (1<>1");
    Arrays.stream(codeSystemVersions.split(",")).forEach(cs -> {
      String[] csv = PipeUtil.parsePipe(cs);
      sb.append("or").append("c.code_system = ? and csv.version = ?", csv[0], csv[1]);
    });
    sb.append(")");
    return sb.toPrettyString();
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

  public void cancel(Long id, String codeSystem) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.cancel_entity(?,?)", id, codeSystem);
    jdbcTemplate.queryForObject(sb.getSql(), sb.getParams(), Void.class);
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
            params.getCodeSystemVersion(), params.getCodeSystemVersionId(), params.getCodeSystemVersions(),
            params.getCodeSystemVersionReleaseDateLe(), params.getCodeSystemVersionReleaseDateGe(),
            params.getCodeSystemVersionExpirationDateLe(), params.getCodeSystemVersionExpirationDateLe(),
            params.getProperties(), params.getPropertyValues(), params.getPropertyValuesPartial())
        .filter(Objects::nonNull).toList())) {
      join += "left join terminology.code_system_entity_version csev on csev.code_system_entity_id = c.id and csev.sys_status = 'A'";
    }

    if (CollectionUtils.isNotEmpty(Stream.of(params.getProperties(), params.getPropertyRoot(), params.getPropertySource()).filter(Objects::nonNull).toList())) {
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

    if (CollectionUtils.isNotEmpty(Stream.of(params.getCodeSystemVersion(), params.getCodeSystemVersionId(), params.getCodeSystemVersions(),
        params.getCodeSystemVersionReleaseDateLe(), params.getCodeSystemVersionReleaseDateGe(),
        params.getCodeSystemVersionExpirationDateLe(), params.getCodeSystemVersionExpirationDateLe()).filter(Objects::nonNull).toList())) {
      join +=
          "left join terminology.entity_version_code_system_version_membership evcsvm on evcsvm.code_system_entity_version_id = csev.id  and evcsvm.sys_status = 'A' " +
              "left join terminology.code_system_version csv on csv.id = evcsvm.code_system_version_id and csv.sys_status = 'A' ";
    }
    return join;
  }

  private static List<String> split(String str, String sep) {
    return StringUtils.isNotEmpty(sep)
        ? List.of(org.apache.commons.lang3.StringUtils.split(str, sep))
        : List.of(str);
  }

  private String buildDirectParentSelect(String conceptAlias, ConceptTreeQueryParams params) {
    String csevAlias = conceptAlias + "_csev";
    String csaAlias = conceptAlias + "_csa";
    String parentAlias = conceptAlias + "_parent";
    String evcsvmAlias = conceptAlias + "_evcsvm";
    String csvAlias = conceptAlias + "_csv";

    SqlBuilder sb = new SqlBuilder();
    sb.append("select ").append(parentAlias).append(".code as parent_code ");
    sb.append("from terminology.code_system_entity_version ").append(csevAlias).append(" ");
    if (hasVersionFilter(params.getMatchParams())) {
      sb.append("left join terminology.entity_version_code_system_version_membership ").append(evcsvmAlias)
          .append(" on ").append(evcsvmAlias).append(".code_system_entity_version_id = ").append(csevAlias).append(".id and ").append(evcsvmAlias).append(".sys_status = 'A' ");
      sb.append("left join terminology.code_system_version ").append(csvAlias)
          .append(" on ").append(csvAlias).append(".id = ").append(evcsvmAlias).append(".code_system_version_id and ").append(csvAlias).append(".sys_status = 'A' ");
    }
    sb.append("join terminology.code_system_association ").append(csaAlias)
        .append(" on ").append(csaAlias).append(".source_code_system_entity_version_id = ").append(csevAlias).append(".id ")
        .append("and ").append(csaAlias).append(".sys_status = 'A' and ").append(csaAlias).append(".status <> 'retired' ")
        .append("and ").append(csaAlias).append(".association_type = ? ", params.getHierarchyType());
    sb.append("join terminology.code_system_entity_version ").append(parentAlias)
        .append(" on ").append(parentAlias).append(".id = ").append(csaAlias).append(".target_code_system_entity_version_id ")
        .append("and ").append(parentAlias).append(".sys_status = 'A' ");
    sb.append("where ").append(csevAlias).append(".code_system_entity_id = ").append(conceptAlias).append(".id ")
        .append("and ").append(csevAlias).append(".sys_status = 'A' and ").append(csevAlias).append(".status <> 'retired' ");
    appendVersionFilter(sb, params.getMatchParams(), csvAlias);
    sb.append("order by ").append(parentAlias).append(".code limit 1");
    return sb.toPrettyString();
  }

  private void appendVersionFilter(SqlBuilder sb, ConceptQueryParams params, String csvAlias) {
    if (StringUtils.isNotEmpty(params.getCodeSystemVersionCodeSystem())) {
      sb.and().in(csvAlias + ".code_system", splitCsv(params.getCodeSystemVersionCodeSystem()));
    }
    sb.appendIfNotNull("and " + csvAlias + ".id = ?", params.getCodeSystemVersionId());
    sb.appendIfNotNull("and " + csvAlias + ".version = ?", params.getCodeSystemVersion());
    if (StringUtils.isNotEmpty(params.getCodeSystemVersions())) {
      sb.append(checkCodeSystemVersions(params.getCodeSystemVersions()).replace("csv.", csvAlias + "."));
    }
    sb.appendIfNotNull("and " + csvAlias + ".release_date >= ?", params.getCodeSystemVersionReleaseDateGe());
    sb.appendIfNotNull("and " + csvAlias + ".release_date <= ?", params.getCodeSystemVersionReleaseDateLe());
    sb.appendIfNotNull("and (" + csvAlias + ".expiration_date >= ? or " + csvAlias + ".expiration_date is null)", params.getCodeSystemVersionExpirationDateGe());
    sb.appendIfNotNull("and (" + csvAlias + ".expiration_date <= ? or " + csvAlias + ".expiration_date is null)", params.getCodeSystemVersionExpirationDateLe());
  }

  private boolean hasVersionFilter(ConceptQueryParams params) {
    return CollectionUtils.isNotEmpty(Stream.of(
            params.getCodeSystemVersion(), params.getCodeSystemVersionId(), params.getCodeSystemVersions(),
            params.getCodeSystemVersionReleaseDateLe(), params.getCodeSystemVersionReleaseDateGe(),
            params.getCodeSystemVersionExpirationDateLe(), params.getCodeSystemVersionExpirationDateGe())
        .filter(Objects::nonNull).toList());
  }

  private List<String> splitCsv(String value) {
    return StringUtils.isEmpty(value) ? List.of() : Arrays.stream(value.split(",")).toList();
  }
}
