package com.kodality.termserver.ts.association;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import javax.inject.Singleton;

@Singleton
public class AssociationTypeRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(AssociationType.class);

  public void save(AssociationType associationType) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("code", associationType.getCode());
    ssb.property("association_kind", associationType.getAssociationKind());
    ssb.property("forward_name", associationType.getForwardName());
    ssb.property("reverse_name", associationType.getReverseName());
    ssb.property("directed", associationType.isDirected());
    ssb.property("description", associationType.getDescription());
    ssb.property("sys_status", "A");

    SqlBuilder sb = ssb.buildUpsert("terminology.association_type", "code");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public AssociationType load(String code) {
    String sql = "select * from terminology.association_type where code = ? and sys_status = 'A'";
    return getBean(sql, bp, code);
  }

  public QueryResult<AssociationType> query(AssociationTypeQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from terminology.association_type a where a.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from terminology.association_type a where a.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(AssociationTypeQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and code = ?", params.getCode());
    sb.appendIfNotNull("and code ~* ?", params.getCodeContains());
    return sb;
  }

  public void cancel(String code) {
    SqlBuilder sb = new SqlBuilder("update terminology.association_type set sys_status = 'C' where code = ? and sys_status = 'A'", code);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
