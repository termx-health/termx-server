package com.kodality.termserver.thesaurus.structuredefinition;

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
    SqlBuilder sb = ssb.buildSave("thesaurus.structure_definition", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    structureDefinition.setId(id);
  }

  public StructureDefinition load(Long id) {
    String sql = "select * from thesaurus.structure_definition where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public QueryResult<StructureDefinition> query(StructureDefinitionQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from thesaurus.structure_definition sd where sd.sys_status = 'A' ");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from thesaurus.structure_definition sd where sd.sys_status = 'A' ");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(StructureDefinitionQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and sd.code ~* ?", params.getTextContains());
    return sb;
  }

  public void cancel(Long id) {
    SqlBuilder sb = new SqlBuilder("update thesaurus.structure_definition set sys_status = 'C' where id = ? and sys_status = 'A'", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
