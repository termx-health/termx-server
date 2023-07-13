package com.kodality.termx.wiki.template;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import javax.inject.Singleton;

@Singleton
public class TemplateRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(Template.class, bp -> {
    bp.addColumnProcessor("names", PgBeanProcessor.fromJson());
  });

  public void save(Template template) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", template.getId());
    ssb.property("code", template.getCode());
    ssb.jsonProperty("names", template.getNames());
    ssb.property("content_type", template.getContentType());
    SqlBuilder sb = ssb.buildSave("wiki.template", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    template.setId(id);
  }

  public Template load(Long id) {
    String sql = "select * from wiki.template where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public QueryResult<Template> query(TemplateQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from wiki.template t where t.sys_status = 'A' ");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from wiki.template t where t.sys_status = 'A' ");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(TemplateQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and t.code = ?", params.getCode());
    sb.appendIfNotNull(params.getTextContains(), (sql, p) -> sql.append("and (t.code ~* ? or exists (select 1 from jsonb_each_text(t.names) where value ~* ?))", p, p));
    return sb;
  }

  public void cancel(Long id) {
    SqlBuilder sb = new SqlBuilder("update wiki.template set sys_status = 'C' where id = ? and sys_status = 'A'", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}

