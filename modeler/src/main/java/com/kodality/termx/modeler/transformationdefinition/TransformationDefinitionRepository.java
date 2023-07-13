package com.kodality.termx.modeler.transformationdefinition;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource;
import java.util.Map;
import javax.inject.Singleton;

@Singleton
public class TransformationDefinitionRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(TransformationDefinition.class, p -> {
    p.addColumnProcessor("resources", PgBeanProcessor.fromJson(JsonUtil.getListType(TransformationDefinitionResource.class)));
    p.addColumnProcessor("mapping", PgBeanProcessor.fromJson());
  });

  private final Map<String, String> orderMapping = Map.of("name", "td.name");

  public void save(TransformationDefinition td) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", td.getId());
    ssb.property("name", td.getName());
    ssb.jsonProperty("resources", td.getResources());
    ssb.jsonProperty("mapping", td.getMapping());
    ssb.property("test_source", td.getTestSource());
    SqlBuilder sb = ssb.buildSave("modeler.transformation_definition", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    td.setId(id);
  }

  public TransformationDefinition load(Long id) {
    SqlBuilder sb = new SqlBuilder("select * from modeler.transformation_definition where id = ? and sys_status = 'A'", id);
    return getBean(sb.getSql(), bp, sb.getParams());
  }

  public QueryResult<TransformationDefinition> search(TransformationDefinitionQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from modeler.transformation_definition td");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from modeler.transformation_definition td");
      sb.append(filter(params));
      sb.append(order(params, orderMapping));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(TransformationDefinitionQueryParams params) {
    SqlBuilder sb = new SqlBuilder("where td.sys_status = 'A'");
    sb.appendIfNotNull("and td.name ilike '%' || ? || '%'", params.getNameContains());
    return sb;
  }

  public void delete(Long id) {
    String sql = "update modeler.transformation_definition set sys_status = 'C' where id = ? and sys_status = 'A'";
    jdbcTemplate.update(sql, id);
  }

}
