package com.kodality.termx.modeler.structuredefinition;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Singleton;

import java.util.Objects;
import java.util.stream.Stream;

@Singleton
public class StructureDefinitionRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(StructureDefinition.class);

  public void save(StructureDefinition structureDefinition) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", structureDefinition.getId());
    ssb.property("url", structureDefinition.getUrl());
    ssb.property("code", structureDefinition.getCode());
    ssb.property("content", structureDefinition.getContent());
    ssb.property("content_type", structureDefinition.getContentType());
    ssb.property("content_format", structureDefinition.getContentFormat());
    ssb.property("parent", structureDefinition.getParent());
    ssb.property("version", structureDefinition.getVersion());
    SqlBuilder sb = ssb.buildSave("modeler.structure_definition", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    structureDefinition.setId(id);
  }

  public StructureDefinition load(Long id) {
    String sql = "select * from modeler.structure_definition where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public QueryResult<StructureDefinition> query(StructureDefinitionQueryParams params) {
    final String join = getJoin(params);
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) "
          + "from modeler.structure_definition sd " + join
          + "where sd.sys_status = 'A' ");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select sd.* "
          + "from modeler.structure_definition sd " + join
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
    sb.appendIfNotNull("and sd.content_format = ?", params.getContentFormat());
    sb.appendIfNotNull("and sd.version = ?", params.getVersion());
    sb.appendIfNotNull("and terminology.text_search(sd.code, sd.url) like '%' || terminology.search_translate(?) || '%'", params.getTextContains());
    sb.appendIfNotNull(params.getUrls(), (s, p) -> s.and().in("sd.url", p));

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
