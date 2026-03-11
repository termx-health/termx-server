package com.kodality.commons.db.audit;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseBeanRepository;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import jakarta.inject.Singleton;

@Singleton
public class AuditLogRepository extends BaseBeanRepository {
  private PgBeanProcessor bp = new PgBeanProcessor(AditLog.class, p -> {
    p.addColumnProcessor("changed_fields_json", "changedFields", PgBeanProcessor.fromJson());
    p.overrideColumnMapping("action_tstamp_tx", "transactionDateTime");
  });

  public QueryResult<AditLog> query(AuditLogQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("SELECT count(1)");
      sb.append(from());
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("SELECT la.*, public.hstore_to_json(changed_fields) AS changed_fields_json");
      sb.append(from());
      sb.append(filter(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder from() {
    return new SqlBuilder("FROM audit.logged_actions la" +
        " INNER JOIN (SELECT id, audit.key(logged_actions) AS key FROM audit.logged_actions) keys ON keys.id = la.id");
  }

  private SqlBuilder filter(AuditLogQueryParams params) {
    SqlBuilder sb = new SqlBuilder("WHERE 1=1");
    if (params.getKeys() != null) {
      sb.and().in("keys.key", params.getKeys());
    }
    return sb;
  }
}
