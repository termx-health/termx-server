package com.kodality.termx.wiki.pagecontent;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.PipeUtil;
import com.kodality.termx.wiki.page.PageContent;
import com.kodality.termx.wiki.page.PageContentQueryParams;
import com.kodality.termx.wiki.page.PageContentQueryParams.Ordering;
import io.micronaut.core.util.StringUtils;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;

@Singleton
public class PageContentRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(PageContent.class);

  private final static String select = """
      select
          pc.*,
             (not exists(select 1 from wiki.page_link pl where pl.source_id != pl.target_id and pl.source_id = pc.id and pl.sys_status = 'A')) as leaf,
             (select date from sys.provenance where target ->> 'type' = 'PageContent' and target ->> 'id' = pc.id::text and activity ='created' order by id desc limit 1) created_at,
             (select author ->> 'id' from sys.provenance where target ->> 'type' = 'PageContent' and target ->> 'id' = pc.id::text and activity ='created' order by id desc limit 1) created_by,
             (select date from sys.provenance where target ->> 'type' = 'PageContent' and target ->> 'id' = pc.id::text and activity ='modified' order by id desc limit 1) modified_at,
             (select author ->> 'id' from sys.provenance where target ->> 'type' = 'PageContent' and target ->> 'id' = pc.id::text and activity ='modified' order by id desc limit 1) modified_by
      """;

  private final Map<String, String> orderMapping = Map.of(
      Ordering.modified, "(select date from sys.provenance where target ->> 'type' = 'PageContent' and target ->> 'id' = pc.id::text and activity in ('created', 'modified') order by id desc limit 1)"
  );

  public List<PageContent> loadAll(Long pageId) {
    String sql = select + "from wiki.page_content pc where pc.sys_status = 'A' and pc.page_id = ?";
    return getBeans(sql, bp, pageId);
  }

  public PageContent load(Long contentId) {
    String sql = select + "from wiki.page_content pc where pc.sys_status = 'A' and pc.id = ?";
    return getBean(sql, bp, contentId);
  }

  public QueryResult<PageContent> query(PageContentQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from wiki.page_content pc");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder(select + "from wiki.page_content pc");
      sb.append(filter(params));
      sb.append(order(params, orderMapping));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(PageContentQueryParams params) {
    SqlBuilder sb = new SqlBuilder("where pc.sys_status = 'A'");
    sb.and().in("pc.space_id", params.getPermittedSpaceIds());
    sb.appendIfNotNull(params.getIds(), (s, p) -> s.and().in("pc.id", p, Long::valueOf));
    sb.appendIfNotNull(params.getSlugs(), (s, p) -> s.and().in("pc.slug", p));
    sb.appendIfNotNull(params.getSpaceIds(), (s, p) -> s.and().in("pc.space_id", p, Long::valueOf));
    sb.appendIfNotNull(params.getTextContains(), (s, p) -> s.and("pc.name ~* ?", p));
    sb.appendIfNotNull(params.getLangs(), (s, p) -> s.and().in("pc.lang", p));

    if (StringUtils.isNotEmpty(params.getRelations())) {
      String[] relations = params.getRelations().split(",");
      sb.and("( 1<>1");
      for (String relation : relations) {
        String[] r = PipeUtil.parsePipe(relation);
        sb.or("exists (select 1 from wiki.page_relation pr where pr.type = ? and pr.target = ? and pr.sys_status = 'A' and pr.content_id = pc.id)", r[0], r[1]);
      }
      sb.append(")");
    }
    return sb;
  }

  public Long save(PageContent content, Long pageId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", content.getId());
    ssb.property("page_id", pageId);
    ssb.property("space_id", content.getSpaceId());
    ssb.property("name", content.getName());
    ssb.property("slug", content.getSlug());
    ssb.property("lang", content.getLang());
    ssb.property("content", content.getContent());
    ssb.property("content_type", content.getContentType());
    SqlBuilder sb = ssb.buildSave("wiki.page_content", "id");
    return jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
  }

  public void delete(Long id) {
    String sql = "update wiki.page_content set sys_status = 'C' where sys_status = 'A' and id = ?";
    jdbcTemplate.update(sql, id);
  }
}
