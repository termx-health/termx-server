package com.kodality.termx.wiki.tag;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import java.util.List;
import jakarta.inject.Singleton;

@Singleton
public class TagRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(Tag.class);

  public void save(Tag tag) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", tag.getId());
    ssb.property("text", tag.getText());
    SqlBuilder sb = ssb.buildSave("wiki.tag", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    tag.setId(id);
  }

  public List<Tag> loadAll() {
    String sql = "select * from wiki.tag where sys_status = 'A'";
    return getBeans(sql, bp);
  }

  public Tag load(Long id) {
    String sql = "select * from wiki.tag where id = ? and sys_status = 'A'";
    return getBean(sql, bp, id);
  }
}
