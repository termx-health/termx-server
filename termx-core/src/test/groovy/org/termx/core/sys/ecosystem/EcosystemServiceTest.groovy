package org.termx.core.sys.ecosystem

import org.termx.sys.ecosystem.Ecosystem
import org.termx.sys.ecosystem.EcosystemQueryParams
import com.kodality.commons.exception.ApiClientException
import com.kodality.commons.model.QueryResult
import spock.lang.Specification

class EcosystemServiceTest extends Specification {
  def repository = Mock(EcosystemRepository)
  def service = new EcosystemService(repository)

  def 'should save new ecosystem with servers'() {
    given:
    def ecosystem = new Ecosystem(code: 'test', active: true, serverIds: [1L, 2L])

    when:
    service.save(ecosystem)

    then:
    1 * repository.load('test') >> null
    1 * repository.save(ecosystem)
    1 * repository.saveServers(_, [1L, 2L])
  }

  def 'should save ecosystem with null serverIds defaults to empty list'() {
    given:
    def ecosystem = new Ecosystem(code: 'test', active: true, serverIds: null)

    when:
    service.save(ecosystem)

    then:
    1 * repository.load('test') >> null
    1 * repository.save(ecosystem)
    1 * repository.saveServers(_, [])
    ecosystem.serverIds == []
  }

  def 'should set default formatVersion if null'() {
    given:
    def ecosystem = new Ecosystem(code: 'test', active: true, formatVersion: null, serverIds: [])

    when:
    service.save(ecosystem)

    then:
    1 * repository.load('test') >> null
    1 * repository.save(ecosystem)
    ecosystem.formatVersion == '1'
  }

  def 'should update existing ecosystem'() {
    given:
    def ecosystem = new Ecosystem(id: 1L, code: 'test', active: true, serverIds: [3L])
    def existing = new Ecosystem(id: 1L, code: 'test')

    when:
    service.save(ecosystem)

    then:
    1 * repository.load('test') >> existing
    1 * repository.save(ecosystem)
    1 * repository.saveServers(1L, [3L])
  }

  def 'should reject duplicate code'() {
    given:
    def ecosystem = new Ecosystem(id: 2L, code: 'test', active: true, serverIds: [])
    def existing = new Ecosystem(id: 1L, code: 'test')

    when:
    service.save(ecosystem)

    then:
    1 * repository.load('test') >> existing
    0 * repository.save(_)
    thrown(ApiClientException)
  }

  def 'should load by id'() {
    given:
    def ecosystem = new Ecosystem(id: 1L, code: 'test', serverIds: [1L, 2L])

    when:
    def result = service.load(1L)

    then:
    1 * repository.load(1L) >> ecosystem
    result.code == 'test'
    result.serverIds == [1L, 2L]
  }

  def 'should load by code'() {
    given:
    def ecosystem = new Ecosystem(id: 1L, code: 'test')

    when:
    def result = service.load('test')

    then:
    1 * repository.load('test') >> ecosystem
    result.id == 1L
  }

  def 'should query ecosystems'() {
    given:
    def params = new EcosystemQueryParams()
    params.textContains = 'test'

    when:
    def result = service.query(params)

    then:
    1 * repository.query(params) >> new QueryResult<>([], 0)
    result.meta.total == 0
  }
}
