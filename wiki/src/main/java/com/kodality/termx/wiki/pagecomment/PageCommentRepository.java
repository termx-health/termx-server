package com.kodality.termx.wiki.pagecomment;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.wiki.page.PageComment;
import com.kodality.termx.wiki.page.PageCommentQueryParams;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class PageCommentRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(PageComment.class, p -> {
    p.addColumnProcessor("options", PgBeanProcessor.fromJson());
  });

  private final static String select = """
      select
          pc.*,
          (select count(*) from wiki.page_comment pcc where pcc.parent_id = pc.id) replies,
          (select date from sys.provenance where target ->> 'type' = 'PageComment' and target ->> 'id' = pc.id::text and activity ='created' order by id desc limit 1) created_at,
          (select author ->> 'id' from sys.provenance where target ->> 'type' = 'PageComment' and target ->> 'id' = pc.id::text and activity ='created' order by id desc limit 1) created_by,
          (select date from sys.provenance where target ->> 'type' = 'PageComment' and target ->> 'id' = pc.id::text and activity ='modified' order by id desc limit 1) modified_at,
          (select author ->> 'id' from sys.provenance where target ->> 'type' = 'PageComment' and target ->> 'id' = pc.id::text and activity ='modified' order by id desc limit 1) modified_by
      """;


  public PageComment load(Long id) {
    String sql = select + "from wiki.page_comment pc where pc.sys_status = 'A' and pc.id = ?";
    return getBean(sql, bp, id);
  }

  public QueryResult<PageComment> query(PageCommentQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from wiki.page_comment pc where pc.sys_status = 'A' ");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder(select + "from wiki.page_comment pc where pc.sys_status = 'A' ");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(PageCommentQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull(params.getIds(), (s, p) -> s.and().in("pc.id", p, Long::valueOf));
    sb.appendIfNotNull(params.getParentIds(), (s, p) -> s.and().in("pc.parent_id", p, Long::valueOf));
    sb.appendIfNotNull(params.getPageContentIds(), (s, p) -> s.and().in("pc.page_content_id", p, Long::valueOf));
    sb.appendIfNotNull(params.getStatuses(), (s, p) -> s.and().in("pc.status", p));
    sb.appendIfNotNull(params.getStatusesNe(), (s, p) -> s.and().notIn("pc.status", p));
    sb.appendIfNotNull(params.getContentContains(), (s, p) -> s.and("pc.content ~* ?", p));
    sb.appendIfNotNull(params.getReplies(), (s, p) -> s.and(Boolean.TRUE.equals(p) ? "pc.parent_id is not null" : "pc.parent_id is null"));
    return sb;
  }

  public Long save(PageComment comment) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", comment.getId());
    ssb.property("page_content_id", comment.getPageContentId());
    ssb.property("parent_id", comment.getParentId());
    ssb.property("text", comment.getText());
    ssb.property("comment", comment.getComment());
    ssb.jsonProperty("options", comment.getOptions());
    ssb.property("status", comment.getStatus());

    SqlBuilder sb = ssb.buildSave("wiki.page_comment", "id");
    return jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
  }

  public void delete(Long id) {
    String sql = "update wiki.page_comment set sys_status = 'C' where sys_status = 'A' and id = ?";
    jdbcTemplate.update(sql, id);
  }

  public void resolve(Long id) {
    String sql = "update wiki.page_comment set status = 'resolved' where sys_status = 'A' and id = ?";
    jdbcTemplate.update(sql, id);
  }

  public List<Long> loadReplyIds(Long parentId) {
    return jdbcTemplate.queryForList("select id from wiki.page_comment where sys_status = 'A' and parent_id = ?", Long.class, parentId);
  }
}
