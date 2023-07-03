package com.kodality.termx.thesaurus.template;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TemplateContentRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(TemplateContent.class);

  public void save(TemplateContent content, Long templateId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", content.getId());
    ssb.property("template_id", templateId);
    ssb.property("lang", content.getLang());
    ssb.property("content", content.getContent());
    SqlBuilder sb = ssb.buildSave("thesaurus.template_content", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    content.setId(id);
  }

  public TemplateContent load(Long id) {
    String sql = "select * from thesaurus.template_content where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public List<TemplateContent> loadAll(Long templateId) {
    String sql = "select * from thesaurus.template_content where sys_status = 'A' and template_id = ?";
    return getBeans(sql, bp, templateId);
  }

  public void retain(List<TemplateContent> contents, Long templateId) {
    SqlBuilder sb = new SqlBuilder("update thesaurus.template_content set sys_status = 'C'");
    sb.append(" where template_id = ? and sys_status = 'A'", templateId);
    sb.andNotIn("id", contents, TemplateContent::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public List<String> findContent(Long templateId, String lang) {
    String sql = "select tc.content from thesaurus.template_content tc where tc.sys_status = 'A' and tc.template_id = ? and tc.lang = ?";
    return jdbcTemplate.queryForList(sql, String.class, templateId, lang);
  }
}
