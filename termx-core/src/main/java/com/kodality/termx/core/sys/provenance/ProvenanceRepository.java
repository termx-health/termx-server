package com.kodality.termx.core.sys.provenance;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.util.JsonUtil;
import com.kodality.commons.util.PipeUtil;
import com.kodality.termx.core.sys.provenance.Provenance.ProvenanceContext;
import java.util.List;
import jakarta.inject.Singleton;

@Singleton
public class ProvenanceRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(Provenance.class, p -> {
    p.addColumnProcessor("target", PgBeanProcessor.fromJson());
    p.addColumnProcessor("author", PgBeanProcessor.fromJson());
    p.addColumnProcessor("detail", PgBeanProcessor.fromJson());
    p.addColumnProcessor("context", PgBeanProcessor.fromJson(JsonUtil.getListType(ProvenanceContext.class)));
  });

  public Long create(Provenance p) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", p.getId());
    ssb.jsonProperty("target", p.getTarget());
    ssb.property("date", p.getDate());
    ssb.property("activity", p.getActivity());
    ssb.jsonProperty("author", p.getAuthor());
    ssb.jsonProperty("context", p.getContext());
    ssb.jsonProperty("detail", p.getDetail());
    SqlBuilder sb = ssb.buildInsert("sys.provenance", "id");
    return jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
  }

  public List<Provenance> find(String targetPipe) {
    String[] pipe = PipeUtil.parsePipe(targetPipe);
    String sql = "select * from sys.provenance where sys_status = 'A' and" +
                 " ((target ->> 'type' = ? and target ->> 'id' = ?)" +
                 " or context @?? ('$[*] ? (@.role == \"part-of\" && @.entity.type == \"' || ? || '\" && @.entity.id == \"' || ? || '\")')::jsonpath)" +
                 " order by date desc";
    return getBeans(sql, bp, pipe[0], pipe[1], pipe[0], pipe[1]);
  }
}
