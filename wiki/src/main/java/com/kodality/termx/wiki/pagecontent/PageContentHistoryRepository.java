package com.kodality.termx.wiki.pagecontent;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.wiki.page.PageContent;
import com.kodality.termx.wiki.page.PageContentHistoryItem;
import com.kodality.termx.wiki.page.PageContentHistoryQueryParams;
import java.util.Map;
import javax.inject.Singleton;

@Singleton
public class PageContentHistoryRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(PageContentHistoryItem.class);

  public QueryResult<PageContentHistoryItem> query(PageContentHistoryQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from wiki.page_content_history pch left join wiki.page_content pc on pc.id = pch.page_content_id");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      String sql = """
          select
            pch.id,
            pch.page_content_id,
            pc.page_id,
            pc.space_id,
            pch.name,
            pch.slug,
            pch.lang,
            pch.content_type,
            %s
            pch.created_at,
            pch.created_by,
            pch.modified_at,
            pch.modified_by
          from wiki.page_content_history pch
            left join wiki.page_content pc on pc.id = pch.page_content_id
          """.formatted(params.isSummary() ? "" : "pch.content,");

      SqlBuilder sb = new SqlBuilder(sql);
      sb.append(filter(params));
      sb.append(order(params, Map.of(PageContentHistoryQueryParams.Ordering.modified, "pch.id")));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(PageContentHistoryQueryParams params) {
    SqlBuilder sb = new SqlBuilder("where true");
    sb.and().in("pc.page_id", params.getPermittedPageIds());
    sb.and().in("pch.page_content_id", params.getPermittedPageContentIds());

    sb.appendIfNotNull(params.getIds(), (s, p) -> s.and().in("pch.id", p, Long::valueOf));
    return sb;
  }

  public void persist(PageContent content) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("page_content_id", content.getId());
    ssb.property("slug", content.getSlug());
    ssb.property("name", content.getName());
    ssb.property("lang", content.getLang());
    ssb.property("content_type", content.getContentType());
    ssb.property("content", content.getContent());
    ssb.property("created_at", content.getCreatedAt());
    ssb.property("created_by", content.getCreatedBy());
    ssb.property("modified_at", content.getModifiedAt());
    ssb.property("modified_by", content.getModifiedBy());
    SqlBuilder sb = ssb.buildInsert("wiki.page_content_history", "id");

    jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
  }
}
