package com.kodality.termx.terminology.terminology.codesystem.entity;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.commons.util.PipeUtil;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemVersionReference;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;

@Singleton
public class CodeSystemEntityVersionRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(CodeSystemEntityVersion.class, p -> {
    p.addColumnProcessor("versions", PgBeanProcessor.fromJson(JsonUtil.getListType(CodeSystemVersionReference.class)));
  });

  private final static String select =
      "select csev.*" +
          ", (select jsonb_agg(json_build_object('id', csv.id, 'version', csv.version, 'status', csv.status, 'preferredLanguage', csv.preferred_language, 'releaseDate', csv.release_date))" +
          "   from terminology.code_system_version csv where csv.sys_status = 'A' " +
          "       and exists (select 1 from terminology.entity_version_code_system_version_membership evcsvm " +
          "         where evcsvm.code_system_entity_version_id = csev.id and evcsvm.code_system_version_id = csv.id and evcsvm.sys_status = 'A')) versions ";

  public void save(CodeSystemEntityVersion version, Long codeSystemEntityId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", version.getId());
    ssb.property("code_system_entity_id", codeSystemEntityId);
    ssb.property("code_system", version.getCodeSystem());
    ssb.property("code", version.getCode());
    ssb.property("description", version.getDescription());
    ssb.property("status", version.getStatus());
    ssb.property("created", version.getCreated());

    SqlBuilder sb = ssb.buildSave("terminology.code_system_entity_version", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    version.setId(id);
  }

  public CodeSystemEntityVersion load(Long id) {
    String sql = select + "from terminology.code_system_entity_version csev where csev.sys_status = 'A' and csev.id = ?";
    return getBean(sql, bp, id);
  }

  public boolean exists(String codeSystem, Long id) {
    String sql = "select exists(select 1 from terminology.code_system_entity_version csev where csev.sys_status = 'A' and csev.code_system = ? and csev.id = ?)";
    return jdbcTemplate.queryForObject(sql, Boolean.class, codeSystem, id);
  }

  public QueryResult<CodeSystemEntityVersion> query(CodeSystemEntityVersionQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from terminology.code_system_entity_version csev ");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder(select + "from terminology.code_system_entity_version csev ");
      sb.append(filter(params));
      sb.append("order by created");
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(CodeSystemEntityVersionQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.append("where csev.sys_status = 'A'");
    sb.and().in("csev.code_system", params.getPermittedCodeSystems());
    sb.appendIfNotNull("and csev.code_system_entity_id = ?", params.getCodeSystemEntityId());
    if (StringUtils.isNotEmpty(params.getCodeSystemEntityIds())) {
      sb.and().in("csev.code_system_entity_id ", params.getCodeSystemEntityIds(), Long::valueOf);
    }
    if (StringUtils.isNotEmpty(params.getStatus())) {
      sb.and().in("csev.status ", params.getStatus());
    }
    sb.appendIfNotNull("and csev.code ~* ?", params.getCodeContains());
    sb.appendIfNotNull("and csev.description ~* ?", params.getDescriptionContains());
    if (StringUtils.isNotEmpty(params.getIds())) {
      sb.and().in("csev.id ", params.getIds(), Long::valueOf);
    }
    if (StringUtils.isNotEmpty(params.getCode())) {
      sb.and().in("csev.code ", params.getCode());
    }
    if (StringUtils.isNotEmpty(params.getCodesNe())) {
      sb.and().notIn("csev.code ", params.getCodesNe());
    }
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and (csev.code ~* ? or " +
              "csev.description ~* ? or " +
              "exists(select 1 from terminology.designation d where d.code_system_entity_version_id = csev.id and d.name ~* ? ))",
          params.getTextContains(), params.getTextContains(), params.getTextContains());
    }
    if (StringUtils.isNotEmpty(params.getCodeSystem())) {
      sb.and().in("csev.code_system ", params.getCodeSystem());
    }
    if (params.getUnlinked() != null) {
      sb.append("and");
      sb.appendIfTrue(params.getUnlinked(), "not");
      sb.append("exists (select 1 from terminology.entity_version_code_system_version_membership evcsvm " +
          "where evcsvm.code_system_entity_version_id = csev.id and evcsvm.sys_status = 'A')");
    }
    sb.appendIfNotNull("and exists (select 1 from terminology.code_system cs " +
        "where csev.code_system = cs.id and cs.uri = ? and cs.sys_status = 'A')", params.getCodeSystemUri());
    sb.appendIfNotNull("and exists (select 1 from terminology.entity_version_code_system_version_membership evcsvm " +
            "where evcsvm.code_system_entity_version_id = csev.id and evcsvm.sys_status = 'A' and evcsvm.code_system_version_id = ?)", params.getCodeSystemVersionId());
    if (params.getCodeSystemVersion() != null) {
      sb.append("and exists (select 1 from terminology.code_system_version csv " +
          "inner join terminology.entity_version_code_system_version_membership evcsvm on evcsvm.code_system_version_id = csv.id and evcsvm.sys_status = 'A' " +
          "where evcsvm.code_system_entity_version_id = csev.id and csv.version = ? and csv.sys_status = 'A'", params.getCodeSystemVersion());
      sb.and().in("csv.code_system", params.getPermittedCodeSystems());
      if (StringUtils.isNotEmpty(params.getCodeSystemVersions())) {
        sb.append(checkCodeSystemVersions(params.getCodeSystemVersions()));
      }
      sb.append(")");
    }
    return sb;
  }

  private String checkCodeSystemVersions(String codeSystemVersions) {
    SqlBuilder sb = new SqlBuilder();
    sb.append("and (1<>1");
    Arrays.stream(codeSystemVersions.split(",")).forEach(cs -> {
      String[] csv = PipeUtil.parsePipe(cs);
      sb.append("or").append("csv.code_system = ? and csv.version = ?", csv[0], csv[1]);
    });
    sb.append(")");
    return sb.toPrettyString();
  }

  public void activate(String codeSystem, List<Long> versionIds) {
    String query = "update terminology.code_system_entity_version set status = ? where id = ? and code_system = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement ps, int i) throws SQLException {
        ps.setString(1, PublicationStatus.active);
        ps.setLong(2, versionIds.get(i));
        ps.setString(3, codeSystem);
        ps.setString(4, PublicationStatus.active);
      }

      @Override
      public int getBatchSize() {
        return versionIds.size();
      }
    });
  }

  public void retire(String codeSystem, List<Long> versionIds) {
    String query = "update terminology.code_system_entity_version set status = ? where id = ? and code_system = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement ps, int i) throws SQLException {
        ps.setString(1, PublicationStatus.retired);
        ps.setLong(2, versionIds.get(i));
        ps.setString(3, codeSystem);
        ps.setString(4, PublicationStatus.retired);
      }

      @Override
      public int getBatchSize() {
        return versionIds.size();
      }
    });
  }

  public void saveAsDraft(Long versionId) {
    String sql = "update terminology.code_system_entity_version set status = ? where id = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.draft, versionId, PublicationStatus.draft);
  }

  public void cancel(Long id) {
    SqlBuilder sb = new SqlBuilder("update terminology.code_system_entity_version set sys_status = 'C' where id = ? and sys_status = 'A'", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void batchUpsert(Map<Long, List<CodeSystemEntityVersion>> versions) {
    List<Pair<Long, CodeSystemEntityVersion>> versionsToInsert =
        versions.entrySet().stream().flatMap(es -> es.getValue().stream().map(v -> Pair.of(es.getKey(), v))).filter(e -> e.getValue().getId() == null).toList();
    String query =
        "insert into terminology.code_system_entity_version (code_system_entity_id, code_system, code, description, status, created) values (?,?,?,?,?,?::timestamp)";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement ps, int i) throws SQLException {
        CodeSystemEntityVersionRepository.this.setValues(ps, i, versionsToInsert);
      }

      @Override
      public int getBatchSize() {return versionsToInsert.size();}
    });

    List<Pair<Long, CodeSystemEntityVersion>> versionsToUpdate =
        versions.entrySet().stream().flatMap(es -> es.getValue().stream().map(v -> Pair.of(es.getKey(), v))).filter(e -> e.getValue().getId() != null).toList();
    query =
        "update terminology.code_system_entity_version set code_system_entity_id = ?, code_system = ?, code = ?, description = ?, status = ?, created = ?::timestamp where id = ?";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement ps, int i) throws SQLException {
        CodeSystemEntityVersionRepository.this.setValues(ps, i, versionsToUpdate);
        ps.setLong(7, versionsToUpdate.get(i).getValue().getId());
      }

      @Override
      public int getBatchSize() {return versionsToUpdate.size();}
    });

    List<CodeSystemEntityVersion> newVersions = new ArrayList<>();
    List<Long> entityIds = versions.keySet().stream().toList();
    List<Long> existingIds = versionsToUpdate.stream().map(v -> v.getValue().getId()).filter(Objects::nonNull).toList();
    IntStream.range(0, (entityIds.size() + 1000 - 1) / 1000).mapToObj(i -> entityIds.subList(i * 1000, Math.min(entityIds.size(), (i + 1) * 1000)))
        .forEach(batch -> {
          SqlBuilder sb = new SqlBuilder("select * from terminology.code_system_entity_version where sys_status = 'A'");
          sb.and().in("code_system_entity_id", batch);
          sb.append("order by sys_created_at desc");
          List<CodeSystemEntityVersion> beans = getBeans(sb.getSql(), bp, sb.getParams());
          newVersions.addAll(beans.stream().filter(v -> !existingIds.contains(v.getId())).toList());
        });

    versions.forEach((key, value) -> value.forEach(val -> {
      if (val.getId() == null && CollectionUtils.isNotEmpty(newVersions)) {
        Optional<CodeSystemEntityVersion> version = newVersions.stream().filter(ver -> ver.getCodeSystemEntityId().equals(key)).findFirst();
        version.ifPresent(v -> {
          val.setId(version.get().getId());
          newVersions.remove(version.get());
        });
      }
    }));
  }

  private void setValues(PreparedStatement ps, int i, List<Pair<Long, CodeSystemEntityVersion>> versionsToInsert) throws SQLException {
    ps.setLong(1, versionsToInsert.get(i).getKey());
    ps.setString(2, versionsToInsert.get(i).getValue().getCodeSystem());
    ps.setString(3, versionsToInsert.get(i).getValue().getCode());
    ps.setString(4, versionsToInsert.get(i).getValue().getDescription());
    ps.setString(5, versionsToInsert.get(i).getValue().getStatus());
    ps.setString(6, versionsToInsert.get(i).getValue().getCreated().toString());
  }
}

