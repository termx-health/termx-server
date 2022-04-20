package com.kodality.termserver.codesystem.association;

import com.kodality.termserver.association.AssociationType;
import com.kodality.termserver.commons.db.bean.PgBeanProcessor;
import com.kodality.termserver.commons.db.repo.BaseRepository;
import com.kodality.termserver.commons.db.sql.SaveSqlBuilder;
import com.kodality.termserver.commons.db.sql.SqlBuilder;
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

    SqlBuilder sb = ssb.buildUpsert("association_type", "code");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public AssociationType load(String code) {
    String sql = "select * from association_type where code = ? and sys_status = 'A'";
    return getBean(sql, bp, code);
  }
}
