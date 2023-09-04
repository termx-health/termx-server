package com.kodality.termx.terminology.mapset.association;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.PipeUtil;
import com.kodality.termx.ts.mapset.MapSetAssociation;
import com.kodality.termx.ts.mapset.MapSetAssociation.MapSetAssociationEntity;
import com.kodality.termx.ts.mapset.MapSetAssociationQueryParams;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;

@Singleton
public class MapSetAssociationRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(MapSetAssociation.class, bp -> {
    bp.addColumnProcessor("map_set_version", PgBeanProcessor.fromJson());
    bp.addRowProcessor("target", (rs) -> new MapSetAssociationEntity()
        .setCodeSystem(rs.getString("target_code_system"))
        .setCodeSystemUri(rs.getString("target_code_system_uri"))
        .setDisplay(rs.getString("target_display"))
        .setCode(rs.getString("target_code")));
    bp.addRowProcessor("source", (rs) -> new MapSetAssociationEntity()
        .setCodeSystem(rs.getString("source_code_system"))
        .setCodeSystemUri(rs.getString("source_code_system_uri"))
        .setDisplay(rs.getString("source_display"))
        .setCode(rs.getString("source_code")));
  });

  private final Map<String, String> orderMapping = Map.of("source-code", "msa.source_code", "target-code", "msa.target_code");

  private final static String select = "select msa.*, msa.target_code is null as no_map, " +
      "(select cs.uri from terminology.code_system cs where cs.id = msa.source_code_system and cs.sys_status = 'A') as source_code_system_uri, " +
      "(select cs.uri from terminology.code_system cs where cs.id = msa.target_code_system and cs.sys_status = 'A') as target_code_system_uri, " +
      "(select json_build_object('id', msv.id, 'version', msv.version, 'status', msv.status) from terminology.map_set_version msv where msv.id = msa.map_set_version_id and msv.sys_status = 'A') as map_set_version ";


  public void save(MapSetAssociation association) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", association.getId());
    ssb.property("map_set", association.getMapSet());
    ssb.property("map_set_version_id", association.getMapSetVersion().getId());
    ssb.property("source_code", association.getSource().getCode());
    ssb.property("source_display", association.getSource().getDisplay());
    ssb.property("source_code_system", association.getSource().getCodeSystem());
    ssb.property("target_code", association.getTarget() == null ? null : association.getTarget().getCode());
    ssb.property("target_display", association.getTarget() == null ? null : association.getTarget().getDisplay());
    ssb.property("target_code_system", association.getTarget() == null ? null : association.getTarget().getCodeSystem());
    ssb.property("relationship", association.getRelationship());
    ssb.property("verified", association.isVerified());
    SqlBuilder sb = ssb.buildSave("terminology.map_set_association", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    association.setId(id);
  }

  public MapSetAssociation load(Long id) {
    String sql = select + "from terminology.map_set_association msa where msa.sys_status = 'A' and msa.id = ?";
    return getBean(sql, bp, id);
  }

  public MapSetAssociation load(String mapSet, Long id) {
    String sql = select + "from terminology.map_set_association msa where msa.sys_status = 'A' and msa.map_set = ? and msa.id = ?";
    return getBean(sql, bp, mapSet, id);
  }

  public QueryResult<MapSetAssociation> query(MapSetAssociationQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from terminology.map_set_association msa where msa.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder(select + "from terminology.map_set_association msa where msa.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(order(params, orderMapping));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(MapSetAssociationQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and msa.id = ?", params.getId());
    sb.appendIfNotNull("and msa.map_set = ?", params.getMapSet());
    sb.appendIfNotNull("and msa.map_set_version_id = ?", params.getMapSetVersionId());
    sb.appendIfNotNull(
        "and exists(select 1 from terminology.map_set_version msv where msv.id = msa.map_set_version_id and msv.sys_status = 'A' and msv.version = ?)",
        params.getMapSetVersion());
    if (CollectionUtils.isNotEmpty(params.getPermittedMapSets())) {
      sb.and().in("msa.map_set", params.getPermittedMapSets());
    }
    if (StringUtils.isNotEmpty(params.getRelationships())) {
      sb.and().in("msa.relationship", params.getRelationships());
    }
    if (StringUtils.isNotEmpty(params.getSourceCodes())) {
      sb.and().in("msa.source_code", params.getSourceCodes());
    }
    if (StringUtils.isNotEmpty(params.getSourceCodeAndSystem())) {
      String[] c = PipeUtil.parsePipe(params.getSourceCodeAndSystem());
      sb.and("msa.source_code = ? and msa.source_code_system = ?", c[0], c[1]);
    }
    if (StringUtils.isNotEmpty(params.getTargetCodeAndSystem())) {
      String[] c = PipeUtil.parsePipe(params.getTargetCodeAndSystem());
      sb.and("msa.target_code = ? and msa.target_code_system = ?", c[0], c[1]);
    }
    if (StringUtils.isNotEmpty(params.getSourceCodeAndSystemUri())) {
      String[] c = PipeUtil.parsePipe(params.getSourceCodeAndSystemUri());
      sb.and("msa.source_code = ? and exists(select 1 from terminology.code_system cs where cs.id = msa.source_code_system and cs.sys_status = 'A' and cs.uri = ?)", c[0], c[1]);
    }
    if (StringUtils.isNotEmpty(params.getTargetCodeAndSystemUri())) {
      String[] c = PipeUtil.parsePipe(params.getTargetCodeAndSystemUri());
      sb.and("msa.target_code = ? and exists(select 1 from terminology.code_system cs where cs.id = msa.target_code_system and cs.sys_status = 'A' and cs.uri = ?)", c[0], c[1]);
    }
    if (params.getNoMap() != null && params.getNoMap()) {
      sb.and("msa.target_code is null");
    }
    if (params.getNoMap() != null && !params.getNoMap()) {
      sb.and("msa.target_code is not null");
    }
    if (params.getVerified() != null && params.getVerified()) {
      sb.and("msa.verified = true");
    }
    if (params.getVerified() != null && !params.getVerified()) {
      sb.and("msa.verified = false");
    }
    return sb;
  }

  public void verify(List<Long> ids, boolean verified) {
    SqlBuilder sb = new SqlBuilder("update terminology.map_set_association set verified = ? where sys_status = 'A'", verified);
    sb.and().in("id", ids);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void cancel(List<Long> ids) {
    SqlBuilder sb = new SqlBuilder("update terminology.map_set_association set sys_status = 'C' where sys_status = 'A'");
    sb.and().in("id", ids);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void batchUpsert(List<MapSetAssociation> associations, String mapSet, Long mapSetVersionId) {
    List<MapSetAssociation> associationsToInsert = associations.stream().filter(a -> a.getId() == null).toList();
    List<MapSetAssociation> associationsToUpdate = associations.stream().filter(a -> a.getId() != null).toList();
    String query = "INSERT INTO terminology.map_set_association (map_set, map_set_version_id, " +
        "source_code, source_display, source_code_system, " +
        "target_code, target_display, target_code_system, " +
        "relationship, verified) VALUES (?,?,?,?,?,?,?,?,?,?)";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement ps, int i) throws SQLException {
        MapSetAssociationRepository.this.setValues(ps, associationsToInsert.get(i), mapSet, mapSetVersionId);
      }

      @Override
      public int getBatchSize() {return associationsToInsert.size();}
    });

    query = "UPDATE terminology.map_set_association SET map_set = ?, map_set_version_id = ?, " +
        "source_code = ?, source_display = ?, source_code_system = ?, " +
        "target_code = ?, target_display = ?, target_code_system = ?, " +
        "relationship = ?, verified = ? WHERE id = ?";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement ps, int i) throws SQLException {
        MapSetAssociationRepository.this.setValues(ps, associationsToUpdate.get(i), mapSet, mapSetVersionId);
        ps.setLong(11, associationsToUpdate.get(i).getId());
      }

      @Override
      public int getBatchSize() {return associationsToUpdate.size();}
    });
  }

  private void setValues(PreparedStatement ps, MapSetAssociation association, String ms, Long msv) throws SQLException {
    ps.setString(1, ms);
    ps.setLong(2, msv);
    ps.setString(3, association.getSource().getCode());
    ps.setString(4, association.getSource().getDisplay());
    ps.setString(5, association.getSource().getCodeSystem());
    ps.setString(6, association.getTarget() == null ? null : association.getTarget().getCode());
    ps.setString(7, association.getTarget() == null ? null : association.getTarget().getDisplay());
    ps.setString(8, association.getTarget() == null ? null : association.getTarget().getCodeSystem());
    ps.setString(9, association.getTarget() == null ? null : association.getRelationship());
    ps.setBoolean(10, association.isVerified());
  }

  public void retain(String ms, Long msv, List<Long> ids) {
    SqlBuilder sb = new SqlBuilder("update terminology.map_set_association set sys_status = 'C' where sys_status = 'A'");
    sb.and("map_set = ?", ms);
    sb.and("map_set_version_id = ?", msv);
    sb.and().notIn("id", ids);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
