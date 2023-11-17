package com.kodality.termx.terminology.terminology.mapset.version;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.mapset.MapSetVersion;
import com.kodality.termx.ts.mapset.MapSetVersionQueryParams;
import javax.inject.Singleton;

@Singleton
public class MapSetVersionRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(MapSetVersion.class, bp -> {
    bp.addColumnProcessor("description", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("scope", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("statistics", PgBeanProcessor.fromJson());

  });

  private final static String select = "select msv.*, " +
      "(select json_build_object(" +
      "   'id', mss.id, " +
      "   'createdAt', mss.created_at, " +
      "   'createdBy', mss.created_by, " +
      "   'sourceConcepts', mss.source_concepts, " +
      "   'equivalent', mss.equivalent, " +
      "   'noMap', mss.no_map, " +
      "   'narrower', mss.narrower, " +
      "   'broader', mss.broader, " +
      "   'unmapped', mss.unmapped, " +
      "   'inactiveSources', mss.inactive_sources, " +
      "   'inactiveTargets', mss.inactive_targets " +
      ") from terminology.map_set_statistics mss where msv.id = mss.map_set_version_id and mss.sys_status = 'A') as statistics ";

  public void save(MapSetVersion version) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", version.getId());
    ssb.property("map_set", version.getMapSet());
    ssb.property("version", version.getVersion());
    ssb.property("status", version.getStatus());
    ssb.property("preferred_language", version.getPreferredLanguage());
    ssb.jsonProperty("description", version.getDescription());
    ssb.property("algorithm", version.getAlgorithm());
    ssb.property("release_date", version.getReleaseDate());
    ssb.property("expiration_date", version.getExpirationDate());
    ssb.property("created", version.getCreated());
    ssb.jsonProperty("scope", version.getScope());
    SqlBuilder sb = ssb.buildSave("terminology.map_set_version", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    version.setId(id);
  }

  public MapSetVersion load(String mapSet, String version) {
    String sql = select + "from terminology.map_set_version msv where msv.sys_status = 'A' and msv.map_set = ? and msv.version = ?";
    return getBean(sql, bp, mapSet, version);
  }

  public MapSetVersion load(Long id) {
    String sql = select + "from terminology.map_set_version msv where msv.sys_status = 'A' and msv.id = ?";
    return getBean(sql, bp, id);
  }

  public QueryResult<MapSetVersion> query(MapSetVersionQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from terminology.map_set_version msv where msv.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder(select + "from terminology.map_set_version msv where msv.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(MapSetVersionQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and msv.map_set = ?", params.getMapSet());
    sb.and().in("msv.map_set", params.getPermittedMapSets());
    sb.and().in("msv.id", params.getIds(), Long::valueOf);
    sb.appendIfNotNull("and msv.version = ?", params.getVersion());
    sb.appendIfNotNull("and msv.status = ?", params.getStatus());
    sb.appendIfNotNull("and msv.release_date <= ?", params.getReleaseDateLe());
    sb.appendIfNotNull("and msv.expiration_date >= ?", params.getExpirationDateGe());
    sb.appendIfNotNull("and exists (select 1 from jsonb_array_elements(msv.scope -> 'sourceCodeSystems') as cs where cs ->> 'id' = ?)", params.getScopeSourceCodeSystem());
    sb.appendIfNotNull("and exists (select 1 from jsonb_array_elements(msv.scope -> 'targetCodeSystems') as cs where cs ->> 'id' = ?)", params.getScopeTargetCodeSystem());
    return sb;
  }

  public void activate(String mapSet, String version) {
    String sql = "update terminology.map_set_version set status = ? where map_set = ? and version = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.active, mapSet, version, PublicationStatus.active);
  }

  public void retire(String mapSet, String version) {
    String sql = "update terminology.map_set_version set status = ? where map_set = ? and version = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.retired, mapSet, version, PublicationStatus.retired);
  }

  public void saveAsDraft(String mapSet, String version) {
    String sql = "update terminology.map_set_version set status = ? where map_set = ? and version = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.draft, mapSet, version, PublicationStatus.draft);
  }

  public void saveExpirationDate(MapSetVersion version) {
    String sql = "update terminology.map_set_version set expiration_date = ? where id = ?";
    jdbcTemplate.update(sql, version.getExpirationDate(), version.getId());
  }

  public MapSetVersion loadLastVersion(String mapSet) {
    String sql = select + "from terminology.map_set_version msv where msv.sys_status = 'A' and msv.map_set = ? and (msv.status = 'active' or msv.status = 'draft') order by msv.release_date desc";
    return getBean(sql, bp, mapSet);
  }

  public void cancel(Long id) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.cancel_map_set_version(?)", id);
    jdbcTemplate.queryForObject(sb.getSql(), sb.getParams(), Void.class);
  }
}
