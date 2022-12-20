package com.kodality.termserver.ts.codesystem.concept;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.PipeUtil;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.ConceptQueryParams;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;

@Singleton
public class ConceptRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(Concept.class);

  private final Map<String, String> orderMapping = Map.of("code", "code");

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
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from terminology.concept c where c.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select c.*, " + isLeaf(params) + "as leaf from terminology.concept c where c.sys_status = 'A'");
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

  private SqlBuilder filter(ConceptQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    if (StringUtils.isNotEmpty(params.getId())) {
      sb.and().in("c.id ", params.getId(), Long::valueOf);
    }
    if (StringUtils.isNotEmpty(params.getCodeSystem())) {
      sb.and().in("c.code_system ", params.getCodeSystem());
    }
    if (CollectionUtils.isNotEmpty(params.getPermittedCodeSystems())) {
      sb.and().in("c.code_system", params.getPermittedCodeSystems());
    }
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
      if (StringUtils.isNotEmpty(params.getCodeSystem())) {
        sb.and().in("c.code_system ", params.getCodeSystem());
      }
      sb.append(")");
    }
    if (StringUtils.isNotEmpty(params.getValueSetExpandResultIds())) {
      sb.and().in("c.id ", params.getValueSetExpandResultIds(), Long::valueOf);
    }
    sb.appendIfNotNull("and exists (select 1 from terminology.code_system_entity_version csev " +
        "where csev.code_system_entity_id = c.id and csev.sys_status = 'A' and csev.status = ?)", params.getCodeSystemEntityStatus());
    sb.appendIfNotNull("and exists (select 1 from terminology.code_system_entity_version csev " +
        "where csev.code_system_entity_id = c.id and csev.sys_status = 'A' and csev.id = ?)", params.getCodeSystemEntityVersionId());
    if (StringUtils.isNotEmpty(params.getPropertyValues()) || StringUtils.isNotEmpty(params.getPropertyValuesPartial())) {
      sb.append("and (1<>1");
      sb.append("or").append(checkProperty(params.getPropertyValues(), params.getPropertyValuesPartial()));
      sb.append("or").append(checkDesignation(params.getPropertyValues(), params.getPropertyValuesPartial()));
      sb.append("or").append(checkAssociation(params.getPropertyValues(), params.getPropertyValuesPartial()));
      sb.append("or").append(checkCode(params.getPropertyValues(), params.getPropertyValuesPartial()));
      sb.append(")");
    }
    sb.appendIfNotNull("and not exists(select 1 from terminology.entity_property_value epv " +
        "inner join terminology.code_system_entity_version csev on csev.id = epv.code_system_entity_version_id and csev.sys_status = 'A' " +
        "where epv.sys_status = 'A' and csev.code_system_entity_id = c.id and epv.entity_property_id = ?)", params.getPropertyRoot());
    sb.appendIfNotNull("and not exists(select 1 from terminology.code_system_association csa " +
        "inner join terminology.code_system_entity_version csev on csev.id = csa.source_code_system_entity_version_id and csev.sys_status = 'A' " +
        "where csa.sys_status = 'A' and csev.code_system_entity_id = c.id and csa.association_type = ?)", params.getAssociationRoot());
    sb.appendIfNotNull("and not exists(select 1 from terminology.code_system_association csa " +
        "inner join terminology.code_system_entity_version csev on csev.id = csa.target_code_system_entity_version_id and csev.sys_status = 'A' " +
        "where csa.sys_status = 'A' and csev.code_system_entity_id = c.id and csa.association_type = ?)", params.getAssociationLeaf());
    if (StringUtils.isNotEmpty(params.getPropertySource())) {
      String[] pipe = PipeUtil.parsePipe(params.getPropertySource());
      sb.append("and exists (select 1 from terminology.entity_property_value epv " +
              "inner join terminology.code_system_entity_version csev on csev.id = epv.code_system_entity_version_id and csev.sys_status = 'A' " +
              "where epv.sys_status = 'A' and csev.code_system_entity_id = c.id and epv.entity_property_id = ? and epv.value = to_jsonb(?::text))",
          Long.valueOf(pipe[0]), pipe[1]);
    }
    if (StringUtils.isNotEmpty(params.getAssociationSource())) {
      String[] pipe = PipeUtil.parsePipe(params.getAssociationSource());
      sb.append("and exists (select 1 from terminology.code_system_association csa " +
          "inner join terminology.code_system_entity_version csev on csev.id = csa.source_code_system_entity_version_id and csev.sys_status = 'A' " +
          "left join terminology.code_system_entity_version csevt on csevt.id = csa.target_code_system_entity_version_id and csevt.sys_status = 'A' " +
          "left join terminology.concept ct on ct.id = csevt.code_system_entity_id and ct.sys_status = 'A' " +
          "where csa.sys_status = 'A' and csev.code_system_entity_id = c.id and csa.association_type = ? and ct.code = ?)", pipe[0], pipe[1]);
    }
    sb.appendIfNotNull("and exists (select 1 from terminology.code_system_association csa " +
        "inner join terminology.code_system_entity_version csev on csev.id = csa.source_code_system_entity_version_id and csev.sys_status = 'A' " +
        "where csa.sys_status = 'A' and csev.code_system_entity_id = c.id and csa.association_type = ?)", params.getAssociationType());
    return sb;
  }

  private String checkCode(String propertyValues, String propertyValuesPartial) {
    SqlBuilder sb = new SqlBuilder();
    if (StringUtils.isNotEmpty(propertyValues) && "code".equals(PipeUtil.parsePipe(propertyValues)[0])) {
      sb.append("code = ?", PipeUtil.parsePipe(propertyValues)[1]);
    } else if (StringUtils.isNotEmpty(propertyValuesPartial) && "code".equals(PipeUtil.parsePipe(propertyValuesPartial)[0])) {
      sb.append("code ~* ?", PipeUtil.parsePipe(propertyValuesPartial)[1]);
    } else {
      sb.append("1 <> 1");
    }
    return sb.toPrettyString();
  }

  private String checkProperty(String propertyValues, String propertyValuesPartial) {
    SqlBuilder sb = new SqlBuilder();
    sb.append("exists (select 1 from terminology.entity_property_value epv " +
        "inner join terminology.entity_property ep on ep.id = epv.entity_property_id and ep.sys_status = 'A' " +
        "inner join terminology.code_system_entity_version csev on csev.id = epv.code_system_entity_version_id and csev.sys_status = 'A' " +
        "where csev.code_system_entity_id = c.id and epv.sys_status = 'A'");
    if (StringUtils.isNotEmpty(propertyValues)) {
      sb.append(checkPropertyValue(propertyValues, "ep.name = ?", "epv.value @> to_jsonb(?::text)", true));
    }
    if (StringUtils.isNotEmpty(propertyValuesPartial)) {
      sb.append(checkPropertyValue(propertyValuesPartial,"ep.name = ?", "epv.value @> to_jsonb(?::text)", false));
    }
    sb.append(")");
    return sb.toPrettyString();
  }

  private String checkDesignation(String propertyValues, String propertyValuesPartial) {
    SqlBuilder sb = new SqlBuilder();
    sb.append("exists (select 1 from terminology.designation d " +
        "inner join terminology.entity_property ep on ep.id = d.designation_type_id and ep.sys_status = 'A' " +
        "inner join terminology.code_system_entity_version csev on csev.id = d.code_system_entity_version_id and csev.sys_status = 'A' " +
        "where csev.code_system_entity_id = c.id and d.sys_status = 'A'");
    if (StringUtils.isNotEmpty(propertyValues)) {
      sb.append(checkPropertyValue(propertyValues,"ep.name = ?", "d.name = ?", true));
    }
    if (StringUtils.isNotEmpty(propertyValuesPartial)) {
      sb.append(checkPropertyValue(propertyValuesPartial, "ep.name = ?", "d.name ~* ?", false));
    }
    sb.append(")");
    return sb.toPrettyString();
  }

  private String checkAssociation(String propertyValues, String propertyValuesPartial) {
    SqlBuilder sb = new SqlBuilder();
    sb.append("exists (select 1 from terminology.code_system_association csa " +
        "inner join terminology.association_type at on at.code = csa.association_type and at.sys_status = 'A' " +
        "inner join terminology.code_system_entity_version target on target.id = csa.target_code_system_entity_version_id and target.sys_status = 'A'  " +
        "inner join terminology.code_system_entity_version source on source.id = csa.source_code_system_entity_version_id and source.sys_status = 'A' " +
        "where source.code_system_entity_id = c.id and csa.sys_status = 'A'");
    if (StringUtils.isNotEmpty(propertyValues)) {
      sb.append(checkPropertyValue(propertyValues,"at.code = ?", "target.code = ?", true));
    }
    if (StringUtils.isNotEmpty(propertyValuesPartial)) {
      sb.append(checkPropertyValue(propertyValuesPartial,"at.code = ?", "target.code = ?", false));
    }
    sb.append(")");
    return sb.toPrettyString();
  }

  private String checkPropertyValue(String values, String nameField, String valueField, boolean exact) {
    SqlBuilder sb = new SqlBuilder();
    String[] propertyValues = values.split(",");
    sb.append("and (").append(Arrays.stream(propertyValues).map(pv -> {
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
}
