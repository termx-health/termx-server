package com.kodality.termserver.ts.association;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termserver.association.AssociationType;
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

    SqlBuilder sb = ssb.buildUpsert("terminology.association_type", "code");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public AssociationType load(String code) {
    String sql = "select * from terminology.association_type where code = ? and sys_status = 'A'";
    return getBean(sql, bp, code);
  }
}
