package org.termx.modeler.structuredefinition;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Singleton;
import java.util.Objects;
import java.util.stream.Stream;
import org.termx.ts.ContactDetail;
import org.termx.ts.UseContext;

@Singleton
public class StructureDefinitionRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(StructureDefinition.class, bp -> {
    bp.addColumnProcessor("title", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("description", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("purpose", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("copyright", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("identifiers", PgBeanProcessor.fromJson(JsonUtil.getListType(Identifier.class)));
    bp.addColumnProcessor("contacts", PgBeanProcessor.fromJson(JsonUtil.getListType(ContactDetail.class)));
    bp.addColumnProcessor("use_context", PgBeanProcessor.fromJson(JsonUtil.getListType(UseContext.class)));
  });

  public void save(StructureDefinition structureDefinition) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", structureDefinition.getId());
    ssb.property("url", structureDefinition.getUrl());
    ssb.property("code", structureDefinition.getCode());
    ssb.property("name", structureDefinition.getName());
    ssb.property("parent", structureDefinition.getParent());
    ssb.property("publisher", structureDefinition.getPublisher());
    ssb.property("hierarchy_meaning", structureDefinition.getHierarchyMeaning());
    ssb.property("experimental", structureDefinition.getExperimental());
    ssb.jsonProperty("title", structureDefinition.getTitle());
    ssb.jsonProperty("description", structureDefinition.getDescription());
    ssb.jsonProperty("purpose", structureDefinition.getPurpose());
    ssb.jsonProperty("copyright", structureDefinition.getCopyright());
    ssb.jsonProperty("identifiers", structureDefinition.getIdentifiers());
    ssb.jsonProperty("contacts", structureDefinition.getContacts());
    ssb.jsonProperty("use_context", structureDefinition.getUseContext());
    SqlBuilder sb = ssb.buildSave("modeler.structure_definition", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    structureDefinition.setId(id);
  }

  public StructureDefinition loadByUrl(String url) {
    String sql = "select * from modeler.structure_definition where sys_status = 'A' and url = ?";
    return getBean(sql, bp, url);
  }

  public StructureDefinition loadByUrlIgnoreCase(String url) {
    String sql = "select * from modeler.structure_definition where sys_status = 'A' and lower(url) = ?";
    return getBean(sql, bp, url);
  }

  public StructureDefinition load(Long id) {
    String sql = "select * from modeler.structure_definition where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  private static final String CURRENT_VERSION_JOIN =
      " left join lateral (select v.version, v.status from modeler.structure_definition_version v where v.structure_definition_id = sd.id and v.sys_status = 'A' order by v.release_date desc nulls last, v.id desc limit 1) cv on true ";

  public QueryResult<StructureDefinition> query(StructureDefinitionQueryParams params) {
    final String join = getJoin(params);
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) "
          + "from modeler.structure_definition sd " + join
          + "where sd.sys_status = 'A' ");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select sd.*, cv.version, cv.status "
          + "from modeler.structure_definition sd " + CURRENT_VERSION_JOIN + join
          + "where sd.sys_status = 'A' ");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private String getJoin(StructureDefinitionQueryParams params) {
    String join = "";
    if (CollectionUtils.isNotEmpty(
        Stream.of(params.getPackageVersionId(),
                params.getPackageId(),
                params.getSpaceId())
            .filter(Objects::nonNull).toList())) {
      join += "left join sys.package_version_resource pvr on pvr.resource_type = 'structure-definition' and pvr.resource_id = sd.id::text and pvr.sys_status = 'A' " +
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

  private SqlBuilder filter(StructureDefinitionQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.and().in("sd.id", params.getPermittedIds());
    sb.and().in("sd.id", params.getIds(), Long::valueOf);
    sb.appendIfNotNull("and sd.code = ?", params.getCode());
    sb.appendIfNotNull("and sd.url = ?", params.getUrl());
    sb.appendIfNotNull(params.getName(), (s, n) -> s.append("and (sd.name ilike '%' || ? || '%' or sd.url ilike '%' || ? || '%' or sd.code ilike '%' || ? || '%')", n, n, n));
    sb.appendIfNotNull("and sd.publisher ilike '%' || ? || '%'", params.getPublisher());
    sb.appendIfNotNull("and terminology.text_search(sd.code, sd.url) like '%' || terminology.search_translate(?) || '%'", params.getTextContains());
    sb.appendIfNotNull(params.getUrls(), (s, p) -> s.and().in("sd.url", p));

    // status/version filter via join to current version
    if (params.getStatus() != null || params.getVersion() != null || params.getContentFormat() != null) {
      sb.append(" and exists (select 1 from modeler.structure_definition_version sdv where sdv.structure_definition_id = sd.id and sdv.sys_status = 'A' ");
      sb.appendIfNotNull("and sdv.status = ?", params.getStatus());
      sb.appendIfNotNull("and sdv.version = ?", params.getVersion());
      sb.appendIfNotNull("and sdv.content_format = ?", params.getContentFormat());
      sb.append(")");
    }

    // space
    sb.appendIfNotNull("and pv.id = ?", params.getPackageVersionId());
    sb.appendIfNotNull("and p.id = ?", params.getPackageId());
    sb.appendIfNotNull("and s.id = ?", params.getSpaceId());
    return sb;
  }

  public void cancel(Long id) {
    SqlBuilder sb = new SqlBuilder("update modeler.structure_definition set sys_status = 'C' where id = ? and sys_status = 'A'", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
