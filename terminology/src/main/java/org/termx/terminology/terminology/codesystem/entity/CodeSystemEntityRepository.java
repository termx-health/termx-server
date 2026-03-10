package org.termx.terminology.terminology.codesystem.entity;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import org.apache.commons.collections4.ListUtils;
import org.termx.ts.codesystem.CodeSystemEntity;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public class CodeSystemEntityRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(CodeSystemEntity.class);

  public void save(CodeSystemEntity entity) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", entity.getId());
    ssb.property("type", entity.getType());
    ssb.property("code_system", entity.getCodeSystem());

    SqlBuilder sb = ssb.buildSave("terminology.code_system_entity", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    entity.setId(id);
  }

  public CodeSystemEntity load(Long id) {
    String sql = "select * from terminology.code_system_entity where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public void batchUpsert(List<CodeSystemEntity> entities, String codeSystem) {
    // Note: this and previous version of this deliberately did not update existing values here. Is it as needed?
    List<CodeSystemEntity> newEntities = entities.stream().filter(e -> e.getId() == null).toList();
    if (newEntities.isEmpty()) {
      return;
    }
    SqlBuilder sql = new SqlBuilder("insert into terminology.code_system_entity (type, code_system) values ");
    sql.append(newEntities.stream().map(e -> "(?, ?)").collect(Collectors.joining(",")));
    sql.add(newEntities.stream().flatMap(e -> Stream.of(e.getType(), codeSystem)).toList());
    sql.append(" returning id");
    List<Long> insertedIds = jdbcTemplate.queryForList(sql.getSql(), Long.class, sql.getParams());
    Streams.forEachPair(newEntities.stream(), insertedIds.stream(), CodeSystemEntity::setId);
  }

  public void cancel(Long id) {
    SqlBuilder sb = new SqlBuilder("update terminology.code_system_entity set sys_status = 'C' where id = ? and sys_status = 'A'", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
