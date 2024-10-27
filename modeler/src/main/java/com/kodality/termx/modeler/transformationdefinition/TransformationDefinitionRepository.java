package com.kodality.termx.modeler.transformationdefinition;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinitionQueryParams.Ordering;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Singleton;

@Singleton
public class TransformationDefinitionRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(TransformationDefinition.class, p -> {
    p.addColumnProcessor("resources", PgBeanProcessor.fromJson(JsonUtil.getListType(TransformationDefinitionResource.class)));
    p.addColumnProcessor("mapping", PgBeanProcessor.fromJson());
    p.addColumnProcessor("fhir_resource", PgBeanProcessor.fromJson());
  });

  private final Map<String, String> orderMapping = Map.of(
      Ordering.id, "td.id",
      Ordering.name, "td.name",
      Ordering.modified,
      "(select date from sys.provenance where target ->> 'type' = 'TransformationDefinition' and target ->> 'id' = td.id::text and activity in ('created', 'modified') order by id desc limit 1)"
  );

  public void save(TransformationDefinition td) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", td.getId());
    ssb.property("name", td.getName());
    ssb.jsonProperty("resources", td.getResources());
    ssb.jsonProperty("mapping", td.getMapping());
    ssb.jsonProperty("fhir_resource", td.getFhirResource());
    ssb.property("test_source", td.getTestSource());
    SqlBuilder sb = ssb.buildSave("modeler.transformation_definition", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    td.setId(id);
  }


  private SqlBuilder select() {
    return select(false);
  }

  private SqlBuilder select(boolean isSummary) {
    return new SqlBuilder("select td.id, td.name").appendIfTrue(!isSummary, ", td.resources, td.mapping, td.fhir_resource, td.test_source")
        .append(", (select date from sys.provenance where target ->> 'type' = 'TransformationDefinition' and target ->> 'id' = td.id::text and activity ='created' order by id desc limit 1) created_at")
        .append(", (select author ->> 'id' from sys.provenance where target ->> 'type' = 'TransformationDefinition' and target ->> 'id' = td.id::text and activity ='created' order by id desc limit 1) created_by")
        .append(", (select date from sys.provenance where target ->> 'type' = 'TransformationDefinition' and target ->> 'id' = td.id::text and activity ='modified' order by id desc limit 1) modified_at")
        .append(", (select author ->> 'id' from sys.provenance where target ->> 'type' = 'TransformationDefinition' and target ->> 'id' = td.id::text and activity ='modified' order by id desc limit 1) modified_by");
  }

  public TransformationDefinition load(Long id) {
    SqlBuilder sb = select().append("from modeler.transformation_definition td where id = ? and sys_status = 'A'", id);
    return getBean(sb.getSql(), bp, sb.getParams());
  }

  public QueryResult<TransformationDefinition> search(TransformationDefinitionQueryParams params) {
    final String join = getJoin(params);
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) " +
          "from modeler.transformation_definition td " + join);
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = select(params.isSummary()).append(" from modeler.transformation_definition td " + join);
      sb.append(filter(params));
      sb.append(order(params, orderMapping));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private String getJoin(TransformationDefinitionQueryParams params) {
    String join = "";
    if (CollectionUtils.isNotEmpty(
        Stream.of(params.getPackageVersionId(),
                params.getPackageId(),
                params.getSpaceId())
            .filter(Objects::nonNull).toList())) {
      join += "left join sys.package_version_resource pvr on pvr.resource_type = 'transformation-definition' and pvr.resource_id = td.id::text and pvr.sys_status = 'A' " +
          "left join sys.package_version pv on pv.id = pvr.version_id and pv.sys_status = 'A' ";
    }
    if (CollectionUtils.isNotEmpty(
        Stream.of(params.getPackageId(),
                params.getSpaceId())
            .filter(Objects::nonNull).toList())) {
      join += "left join sys.package p on p.id = pv.package_id and p.sys_status = 'A' ";
    }
    if (CollectionUtils.isNotEmpty(Stream.of(params.getSpaceId()).filter(Objects::nonNull).toList())) {
      join += "left join sys.space s on s.id = p.space_id and s.sys_status = 'A' ";
    }
    return join;
  }

  private SqlBuilder filter(TransformationDefinitionQueryParams params) {
    SqlBuilder sb = new SqlBuilder("where td.sys_status = 'A'");
    sb.and().in("td.id", params.getPermittedIds());
    sb.appendIfNotNull(params.getIds(), (s, p) -> s.and().in("td.id", p, Long::valueOf));
    sb.appendIfNotNull("and td.name ilike '%' || ? || '%'", params.getNameContains());
    sb.appendIfNotNull("and td.name = ? ", params.getName());

    sb.appendIfNotNull(params.getFhirExists(), (s, p) -> s.and(p ? "td.fhir_resource is not null" : "td.fhir_resource is null"));
    sb.appendIfNotNull(params.getFhirIds(), (s, p) -> s.and().in("td.fhir_resource ->> 'id'", p));
    sb.appendIfNotNull(params.getFhirUrls(), (s, p) -> s.and().in("td.fhir_resource ->> 'url'", p));
    sb.appendIfNotNull(params.getFhirDescriptionContains(), (s, p) -> s.and("td.fhir_resource ->> 'description' ilike '%' || ? || '%'", p));
    sb.appendIfNotNull(params.getFhirTitleContains(), (s, p) -> s.and("td.fhir_resource ->> 'title' ilike '%' || ? || '%'", p));
    sb.appendIfNotNull(params.getFhirStatuses(), (s, p) -> s.and().in("td.fhir_resource ->> 'status'", p));

    // space
    sb.appendIfNotNull("and pv.id = ?", params.getPackageVersionId());
    sb.appendIfNotNull("and p.id = ?", params.getPackageId());
    sb.appendIfNotNull("and s.id = ?", params.getSpaceId());
    return sb;
  }

  public void delete(Long id) {
    String sql = "update modeler.transformation_definition set sys_status = 'C' where id = ? and sys_status = 'A'";
    jdbcTemplate.update(sql, id);
  }


  public void updateFhirResource(Long id, Object fhirResource) {
    String sql = "update modeler.transformation_definition set fhir_resource = core.jsonb_trunc(?::jsonb) where id = ? and sys_status = 'A'";
    jdbcTemplate.update(sql, JsonUtil.toJson(fhirResource), id);
  }
}
