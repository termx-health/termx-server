package com.kodality.termserver.codesystem.entityproperty;

import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.codesystem.EntityPropertyQueryParams;
import com.kodality.termserver.commons.db.bean.PgBeanProcessor;
import com.kodality.termserver.commons.db.repo.BaseRepository;
import com.kodality.termserver.commons.db.sql.SaveSqlBuilder;
import com.kodality.termserver.commons.db.sql.SqlBuilder;
import com.kodality.termserver.commons.model.model.QueryResult;
import jakarta.inject.Singleton;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

@Singleton
public class EntityPropertyRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(EntityProperty.class);

  public void save(EntityProperty entityProperty, String codeSystem) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", entityProperty.getId());
    ssb.property("code_system", codeSystem);
    ssb.property("name", entityProperty.getName());
    ssb.property("description", entityProperty.getDescription());
    ssb.property("status", entityProperty.getStatus());
    ssb.property("created", entityProperty.getCreated());

    SqlBuilder sb = ssb.buildSave("entity_property", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    entityProperty.setId(id);
  }

  public List<EntityProperty> getProperties(String codeSystem) {
    String sql = "select * from entity_property where sys_status = 'A' and code_system = ?";
    return getBeans(sql, bp, codeSystem);
  }

  public QueryResult<EntityProperty> query(EntityPropertyQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from entity_property where sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from entity_property where sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(EntityPropertyQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    if (StringUtils.isNotEmpty(params.getNames())) {
      sb.and().in("name", params.getNames());
    }
    sb.appendIfNotNull("and code_system = ?", params.getCodeSystem());
    return sb;
  }
}
