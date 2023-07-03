package com.kodality.termx.sequence;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.sequence.SysSequence.SysSequenceLuv;
import com.kodality.termx.sequence.SysSequenceQueryParams.Ordering;
import java.util.Map;
import javax.inject.Singleton;

@Singleton
public class SysSequenceRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(SysSequence.class, p -> {
    p.addColumnProcessor("luvs", PgBeanProcessor.fromJson(JsonUtil.getListType(SysSequenceLuv.class)));
  });
  private final Map<String, String> orderMapping = Map.of(Ordering.code, "seq.code");

  public QueryResult<SysSequence> query(SysSequenceQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from core.sys_sequence seq where sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      String sql = """
          select
            *, (select json_agg(json_build_object('luv', ssl.luv, 'period', ssl.period) order by ssl.sys_modify_time desc) from core.sys_sequence_luv ssl where ssl.sequence_id = seq.id) luvs
          from core.sys_sequence seq
          where seq.sys_status = 'A'
          """;
      SqlBuilder sb = new SqlBuilder(sql);
      sb.append(filter(params));
      sb.append(order(params, orderMapping));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(SysSequenceQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull(params.getCodes(), (sql, p) -> sql.and().in("seq.code", p));
    sb.appendIfNotNull("and seq.code ilike '%' || ? || '%'", params.getTextContains());
    return sb;
  }

  public SysSequence load(Long id) {
    return getBean("""
        select
          *, (select json_agg(json_build_object('luv', ssl.luv, 'period', ssl.period) order by ssl.sys_modify_time desc) from core.sys_sequence_luv ssl where ssl.sequence_id = seq.id) luvs
        from core.sys_sequence seq where seq.id = ? and seq.sys_status = 'A'
        """, bp, id);
  }

  public Long save(SysSequence sequence) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", sequence.getId());
    ssb.property("code", sequence.getCode());
    ssb.property("description", sequence.getDescription());
    ssb.property("restart", sequence.getRestart());
    ssb.property("pattern", sequence.getPattern());
    ssb.property("start_from", sequence.getStartFrom());

    SqlBuilder sb = ssb.buildSave("core.sys_sequence", "id");
    return jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
  }

  public boolean hasDuplicate(SysSequence sequence) {
    SqlBuilder sb = new SqlBuilder("select 1 from core.sys_sequence where sys_status = 'A'");
    sb.append("and code = ?", sequence.getCode());
    sb.appendIfNotNull("and id <> ?", sequence.getId());

    SqlBuilder sql = new SqlBuilder("select exists(").append(sb).append(")");
    return jdbcTemplate.queryForObject(sql.getSql(), Boolean.class, sql.getParams());
  }
}
