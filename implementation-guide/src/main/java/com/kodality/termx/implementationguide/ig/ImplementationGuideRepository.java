package com.kodality.termx.implementationguide.ig;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.implementationguide.ig.ImplementationGuideQueryParams.Ordering;
import com.kodality.termx.ts.ContactDetail;
import io.micronaut.core.util.StringUtils;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;

@Singleton
public class ImplementationGuideRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(ImplementationGuide.class, bp -> {
    bp.addColumnProcessor("title", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("description", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("purpose", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("identifiers", PgBeanProcessor.fromJson(JsonUtil.getListType(Identifier.class)));
    bp.addColumnProcessor("contacts", PgBeanProcessor.fromJson(JsonUtil.getListType(ContactDetail.class)));
    bp.addColumnProcessor("copyright", PgBeanProcessor.fromJson());
  });

  public void save(ImplementationGuide ig) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", ig.getId());
    ssb.property("uri", ig.getUri());
    ssb.property("publisher", ig.getPublisher());
    ssb.property("name", ig.getName());
    ssb.jsonProperty("title", ig.getTitle());
    ssb.jsonProperty("description", ig.getDescription());
    ssb.jsonProperty("purpose", ig.getPurpose());
    ssb.property("experimental", ig.getExperimental());
    ssb.jsonProperty("identifiers", ig.getIdentifiers());
    ssb.jsonProperty("contacts", ig.getContacts());
    ssb.jsonProperty("copyright", ig.getCopyright());
    ssb.property("sys_status", "A");

    SqlBuilder sb = ssb.buildUpsert("sys.implementation_guide", "id");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public ImplementationGuide load(String id) {
    String sql = "select * from sys.implementation_guide ig where ig.sys_status = 'A' and ig.id = ?";
    return getBean(sql, bp, id);
  }

  public QueryResult<ImplementationGuide> query(ImplementationGuideQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from sys.implementation_guide ig where ig.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from sys.implementation_guide ig where ig.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(order(params, sortMap()));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(ImplementationGuideQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull(params.getPermittedIds(), (s, p) -> s.and().in("ig.id", p));
    sb.and().in("ig.id", params.getIds());
    sb.and().in("ig.uri", params.getUris());
    sb.appendIfNotNull("and ig.publisher = ?", params.getPublisher());
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and (ig.id like '%' || ? || '%' or ig.uri like '%' || ? || '%' or ig.name like '%' || ? || '%')",
          params.getTextContains(), params.getTextContains(), params.getTextContains());
    }
    return sb;
  }

  private Map<String, String> sortMap() {
    Map<String, String> sortMap = new HashMap<>(Map.of(
        Ordering.id, "ig.id",
        Ordering.uri, "ig.uri",
        Ordering.name, "ig.name"
    ));
    return sortMap;
  }

  public void changeId(String currentId, String newId) {
    SqlBuilder sb = new SqlBuilder("select * from sys.change_implementation_guide_id(?,?)", currentId, newId);
    jdbcTemplate.queryForObject(sb.getSql(), sb.getParams(), Void.class);
  }

  public void cancel(String ig) {
    SqlBuilder sb = new SqlBuilder("select * from sys.cancel_implementation_guide(?)", ig);
    jdbcTemplate.queryForObject(sb.getSql(), sb.getParams(), Void.class);
  }

}
