package org.termx.terminology.terminology.closure;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import jakarta.inject.Singleton;
import java.util.List;
import org.termx.ts.closure.Closure;
import org.termx.ts.closure.ClosureConcept;
import org.termx.ts.closure.ClosureRelationship;

@Singleton
public class ClosureRepository extends BaseRepository {
  private final PgBeanProcessor closureBp = new PgBeanProcessor(Closure.class);
  private final PgBeanProcessor conceptBp = new PgBeanProcessor(ClosureConcept.class);
  private final PgBeanProcessor relBp = new PgBeanProcessor(ClosureRelationship.class);

  public Closure load(Long id) {
    String sql = "select * from terminology.closure where sys_status = 'A' and id = ?";
    return getBean(sql, closureBp, id);
  }

  public Closure findByName(String name) {
    String sql = "select * from terminology.closure where sys_status = 'A' and name = ?";
    return getBean(sql, closureBp, name);
  }

  public Closure save(Closure closure) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", closure.getId());
    ssb.property("name", closure.getName());
    ssb.property("current_version", closure.getCurrentVersion());
    ssb.property("sys_status", "A");
    SqlBuilder sb = ssb.buildSave("terminology.closure", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    closure.setId(id);
    return closure;
  }

  public void bumpVersion(Long closureId, Integer newVersion) {
    String sql = "update terminology.closure set current_version = ?, sys_modified_at = now(), sys_modified_by = current_user, sys_version = sys_version + 1 where id = ? and sys_status = 'A'";
    jdbcTemplate.update(sql, newVersion, closureId);
  }

  public List<ClosureConcept> findConcepts(Long closureId) {
    String sql = "select * from terminology.closure_concept where sys_status = 'A' and closure_id = ?";
    return getBeans(sql, conceptBp, closureId);
  }

  /**
   * Insert if (closure_id, code_system, code) is not yet present. Returns true if a row was inserted,
   * false if the concept was already in the closure.
   */
  public boolean insertConcept(Long closureId, String codeSystem, String code, int version) {
    String sql = """
        insert into terminology.closure_concept (closure_id, code_system, code, version,
            sys_created_at, sys_created_by, sys_modified_at, sys_modified_by, sys_status, sys_version)
        select ?, ?, ?, ?, now(), current_user, now(), current_user, 'A', 1
        where not exists (
            select 1 from terminology.closure_concept
             where closure_id = ? and code_system = ? and code = ? and sys_status = 'A')
        """;
    return jdbcTemplate.update(sql, closureId, codeSystem, code, version, closureId, codeSystem, code) > 0;
  }

  /**
   * Insert if (closure_id, code_system, child_code, parent_code) is not yet present.
   */
  public boolean insertRelationship(Long closureId, String codeSystem, String childCode, String parentCode, int version) {
    String sql = """
        insert into terminology.closure_relationship (closure_id, code_system, child_code, parent_code, version,
            sys_created_at, sys_created_by, sys_modified_at, sys_modified_by, sys_status, sys_version)
        select ?, ?, ?, ?, ?, now(), current_user, now(), current_user, 'A', 1
        where not exists (
            select 1 from terminology.closure_relationship
             where closure_id = ? and code_system = ? and child_code = ? and parent_code = ? and sys_status = 'A')
        """;
    return jdbcTemplate.update(sql, closureId, codeSystem, childCode, parentCode, version,
        closureId, codeSystem, childCode, parentCode) > 0;
  }

  public List<ClosureRelationship> findRelationshipsAtVersion(Long closureId, int version) {
    String sql = "select * from terminology.closure_relationship where sys_status = 'A' and closure_id = ? and version = ?";
    return getBeans(sql, relBp, closureId, version);
  }

  public List<ClosureRelationship> findRelationshipsUpToVersion(Long closureId, int version) {
    String sql = "select * from terminology.closure_relationship where sys_status = 'A' and closure_id = ? and version <= ?";
    return getBeans(sql, relBp, closureId, version);
  }

  /** Soft-delete all concepts and relationships for a closure (used to reset on init). */
  public void clear(Long closureId) {
    jdbcTemplate.update("update terminology.closure_concept set sys_status = 'C' where closure_id = ? and sys_status = 'A'", closureId);
    jdbcTemplate.update("update terminology.closure_relationship set sys_status = 'C' where closure_id = ? and sys_status = 'A'", closureId);
  }

  /**
   * Look up subsumption ancestors for the given (codeSystem, code) using the existing
   * concept_closure materialized view. Returns the list of parent codes (more general) including
   * transitive ancestors. Excludes the input concept itself.
   */
  public List<String> findAncestors(String codeSystem, String code) {
    String sql = """
        select distinct parent_code
          from terminology.concept_closure
         where code_system = ?
           and child_code = ?
           and parent_code <> child_code
        """;
    return jdbcTemplate.queryForList(sql, String.class, codeSystem, code);
  }
}
