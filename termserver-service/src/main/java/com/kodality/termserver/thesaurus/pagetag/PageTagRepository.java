package com.kodality.termserver.thesaurus.pagetag;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termserver.thesaurus.page.PageTag;
import com.kodality.termserver.thesaurus.tag.Tag;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class PageTagRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(PageTag.class, bp -> {
    bp.addColumnProcessor("tag_id", "tag", (rs, index, propType) -> new Tag().setId(rs.getLong("tag_id")));
   });


  public void save(PageTag tag, Long pageId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", tag.getId());
    ssb.property("page_id", pageId);
    ssb.property("tag_id", tag.getTag().getId());
    SqlBuilder sb = ssb.buildSave("thesaurus.page_tag", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    tag.setId(id);
  }

  public List<PageTag> loadAll(Long pageId) {
    String sql = "select * from thesaurus.page_tag where sys_status = 'A' and page_id = ?";
    return getBeans(sql, bp, pageId);
  }

  public void retain(List<PageTag> tags, Long pageId) {
    SqlBuilder sb = new SqlBuilder("update thesaurus.page_tag set sys_status = 'C'");
    sb.append(" where page_id = ? and sys_status = 'A'", pageId);
    sb.andNotIn("id", tags, PageTag::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}

