package com.kodality.termserver.ts.valueset;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.valueset.ValueSet;
import com.kodality.termserver.valueset.ValueSetQueryParams;
import javax.inject.Singleton;

@Singleton
public class ValueSetRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(ValueSet.class, bp -> {
    bp.addColumnProcessor("names", PgBeanProcessor.fromJson());
  });

  public void create(ValueSet valueSet) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", valueSet.getId());
    ssb.property("uri", valueSet.getUri());
    ssb.jsonProperty("names", valueSet.getNames());
    ssb.property("description", valueSet.getDescription());

    SqlBuilder sb = ssb.buildUpsert("value_set", "id");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public ValueSet load(String id) {
    String sql = "select * from value_set where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public QueryResult<ValueSet> query(ValueSetQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from value_set vs where vs.sys_status = 'A' ");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select vs.* from value_set vs where vs.sys_status = 'A' ");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(ValueSetQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and exists (select 1 from jsonb_each_text(vs.names) where value ~* ?)", params.getName());
    return sb;
  }

}
