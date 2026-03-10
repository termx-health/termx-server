package org.termx.terminology.terminology.codesystem.entity

import org.springframework.jdbc.core.JdbcTemplate
import org.termx.ts.codesystem.CodeSystemEntity
import org.termx.ts.codesystem.Concept
import spock.lang.Specification

class CodeSystemEntityRepositoryTest extends Specification {

  def "batchUpsert assigns returned ids to new entities in insert order"() {
    given:
    def repository = new CodeSystemEntityRepository()
    def jdbcTemplate = Mock(JdbcTemplate)
    repository.jdbcTemplate = jdbcTemplate;

    def existing = new Concept().setId(55L).setType("concept").setCodeSystem("cs")
    def newOne = new Concept().setType("concept").setCodeSystem("cs")
    def newTwo = new Concept().setType("association").setCodeSystem("cs")
    def entities = [existing, newOne, newTwo]

    when:
    repository.batchUpsert(entities, "cs")

    then:
    1 * jdbcTemplate.queryForList(
        { String sql ->
          sql.contains("insert into terminology.code_system_entity (type, code_system) values") &&
          sql.contains("(?, ?),(?, ?) ") &&
          sql.contains("returning id")
        },
        Long.class,
        { Object[] params ->
          params.toList() == ["concept", "cs", "association", "cs"]
        }
    ) >> [101L, 102L]

    and:
    existing.id == 55L
    newOne.id == 101L
    newTwo.id == 102L
  }
}
