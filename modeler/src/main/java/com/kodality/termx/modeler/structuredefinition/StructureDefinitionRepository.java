package com.kodality.termx.modeler.structuredefinition;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import javax.inject.Singleton;

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
    return BaseRepository.query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from modeler.structure_definition sd where sd.sys_status = 'A' ");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from modeler.structure_definition sd where sd.sys_status = 'A' ");
      sb.append(filter(params));
      sb.append(BaseRepository.limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(StructureDefinitionQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.and().in("sd.id", params.getPermittedIds());
    sb.appendIfNotNull("and sd.code = ?", params.getCode());
    sb.appendIfNotNull("and terminology.text_search(sd.code, sd.url) like '%' || terminology.search_translate(?) || '%'", params.getTextContains());
    return sb;
  }

  public void cancel(Long id) {
    SqlBuilder sb = new SqlBuilder("update modeler.structure_definition set sys_status = 'C' where id = ? and sys_status = 'A'", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
