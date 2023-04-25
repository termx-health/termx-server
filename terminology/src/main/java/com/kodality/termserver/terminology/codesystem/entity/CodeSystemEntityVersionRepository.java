package com.kodality.termserver.terminology.codesystem.entity;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersionQueryParams;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.inject.Singleton;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;

@Singleton
public class CodeSystemEntityVersionRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(CodeSystemEntityVersion.class);

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
    String sql = "select * from terminology.code_system_entity_version csev where csev.sys_status = 'A' and csev.id = ?";
    return getBean(sql, bp, id);
  }

  public QueryResult<CodeSystemEntityVersion> query(CodeSystemEntityVersionQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from terminology.code_system_entity_version csev where csev.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from terminology.code_system_entity_version csev where csev.sys_status = 'A'");
      sb.append(filter(params));
      sb.append("order by created");
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(CodeSystemEntityVersionQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
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
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and (csev.code ~* ? or csev.description ~* ? or " +
              "exists(select 1 from terminology.designation d where d.code_system_entity_version_id = csev.id and d.name ~* ? ))", params.getTextContains(),
          params.getTextContains(), params.getTextContains());
    }
    if (StringUtils.isNotEmpty(params.getCodeSystem())) {
      sb.and().in("csev.code_system ", params.getCodeSystem());
    }
    if (CollectionUtils.isNotEmpty(params.getPermittedCodeSystems())) {
      sb.and().in("csev.code_system", params.getPermittedCodeSystems());
    }
    sb.appendIfNotNull("and exists (select 1 from terminology.code_system cs " +
        "where csev.code_system = cs.id and cs.uri = ? and cs.sys_status = 'A')", params.getCodeSystemUri());
    sb.appendIfNotNull("and exists (select 1 from terminology.entity_version_code_system_version_membership evcsvm " +
        "where evcsvm.code_system_entity_version_id = csev.id and evcsvm.sys_status = 'A' and evcsvm.code_system_version_id = ?)", params.getCodeSystemVersionId());
    if (params.getCodeSystemVersion() != null) {
      sb.append("and exists (select 1 from terminology.code_system_version csv " +
          "inner join terminology.entity_version_code_system_version_membership evcsvm on evcsvm.code_system_version_id = csv.id and evcsvm.sys_status = 'A' " +
          "where evcsvm.code_system_entity_version_id = csev.id and csv.version = ? and csv.sys_status = 'A'", params.getCodeSystemVersion());
      if (CollectionUtils.isNotEmpty(params.getPermittedCodeSystems())) {
        sb.and().in("csv.code_system", params.getPermittedCodeSystems());
      }
      sb.append(")");
    }
    return sb;
  }

  public void activate(Long versionId) {
    String sql = "update terminology.code_system_entity_version set status = ? where id = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.active, versionId, PublicationStatus.active);
  }

  public void activate(List<Long> versionIds) {
    String query = "update terminology.code_system_entity_version set status = ? where id = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement ps, int i) throws SQLException {
        ps.setString(1, PublicationStatus.active);
        ps.setLong(2, versionIds.get(i));
        ps.setString(3, PublicationStatus.active);
      }
      @Override
      public int getBatchSize() {
        return versionIds.size();
      }
    });
  }

  public void retire(Long versionId) {
    String sql = "update terminology.code_system_entity_version set status = ? where id = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.retired, versionId, PublicationStatus.retired);
  }

  public void saveAsDraft(Long versionId) {
    String sql = "update terminology.code_system_entity_version set status = ? where id = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.draft, versionId, PublicationStatus.draft);
  }

  public void cancel(Long id) {
    SqlBuilder sb = new SqlBuilder("update terminology.code_system_entity_version set sys_status = 'C' where id = ? and sys_status = 'A'", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void batchUpsert(Map<Long, CodeSystemEntityVersion> versions) {
    List<Entry<Long, CodeSystemEntityVersion>> versionsToInsert = versions.entrySet().stream().filter(e -> e.getValue().getId() == null).toList();
    String query = "insert into terminology.code_system_entity_version (code_system_entity_id, code_system, code, description, status, created) values (?,?,?,?,?,?::timestamp)";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
        CodeSystemEntityVersionRepository.this.setValues(ps, i, versionsToInsert);
      }
      @Override public int getBatchSize() {return versionsToInsert.size();}
    });

    List<Entry<Long, CodeSystemEntityVersion>> versionsToUpdate = versions.entrySet().stream().filter(e -> e.getValue().getId() != null).toList();
    query = "update terminology.code_system_entity_version set code_system_entity_id = ?, code_system = ?, code = ?, description = ?, status = ?, created = ?::timestamp where id = ?";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
        CodeSystemEntityVersionRepository.this.setValues(ps, i, versionsToUpdate);
        ps.setLong(7, versionsToUpdate.get(i).getValue().getId());
      }
      @Override public int getBatchSize() {return versionsToUpdate.size();}
    });

    List<CodeSystemEntityVersion> newVersions = new ArrayList<>();
    List<Long> entityIds = versions.keySet().stream().toList();
    List<Long> existingIds = versionsToUpdate.stream().map(v -> v.getValue().getId()).filter(Objects::nonNull).toList();
    IntStream.range(0,(entityIds.size()+1000-1)/1000).mapToObj(i -> entityIds.subList(i*1000, Math.min(entityIds.size(), (i+1)*1000))).forEach(batch -> {
      SqlBuilder sb = new SqlBuilder("select * from terminology.code_system_entity_version where sys_status = 'A'");
      sb.and().in("code_system_entity_id", batch);
      sb.append("order by sys_created_at desc");
      List<CodeSystemEntityVersion> beans = getBeans(sb.getSql(), bp, sb.getParams());
      newVersions.addAll(beans.stream().filter(v -> !existingIds.contains(v.getId())).toList());
    });

    versions.forEach((key, value) -> {
      if (value.getId() == null && CollectionUtils.isNotEmpty(newVersions)) {
        Optional<CodeSystemEntityVersion> version = newVersions.stream().filter(ver -> ver.getCodeSystemEntityId().equals(key)).findFirst();
        version.ifPresent(v -> {
          value.setId(version.get().getId());
          newVersions.remove(version.get());
        });
      }
    });
  }

  private void setValues(PreparedStatement ps, int i, List<Entry<Long, CodeSystemEntityVersion>> versionsToInsert) throws SQLException {
    ps.setLong(1, versionsToInsert.get(i).getKey());
    ps.setString(2, versionsToInsert.get(i).getValue().getCodeSystem());
    ps.setString(3, versionsToInsert.get(i).getValue().getCode());
    ps.setString(4, versionsToInsert.get(i).getValue().getDescription());
    ps.setString(5, versionsToInsert.get(i).getValue().getStatus());
    ps.setString(6, versionsToInsert.get(i).getValue().getCreated().toString());
  }
}

