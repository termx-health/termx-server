package com.kodality.termserver.codesystem

import com.kodality.commons.model.LocalizedName
import com.kodality.commons.model.QueryResult
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification


@MicronautTest(transactional = false)
class CodeSystemControllerSpec extends Specification {

  @Shared
  @AutoCleanup
  EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
  @Shared
  @AutoCleanup
  HttpClient client = HttpClient.create(embeddedServer.URL)

  @Shared
  CodeSystemFactory codeSystemFactory

  def setup() {
    codeSystemFactory = new CodeSystemFactory(client)
  }

  def "should create new code system"() {
    def system = new CodeSystem(
        id: 'test-cs',
        uri: 'http://test.ee',
        names: new LocalizedName(['en': 'Test code system']),
        description: 'CS created for test'
    )
    when:
    def response = client.toBlocking().exchange(HttpRequest.POST('/code-systems/', system))
    def createdCodeSystem = client.toBlocking().exchange(HttpRequest.GET('/code-systems/test-cs'), CodeSystem.class).body()
    then:
    response.status().code == 200
    createdCodeSystem.id == system.id
    createdCodeSystem.uri == system.uri
    createdCodeSystem.description == system.description
  }

  def "should create new concept"() {
    def concept = new Concept(
        code: 'acura',
        codeSystem: 'car-brands',
        displayName: ['en': 'Acura']
    )
    when:
    codeSystemFactory.createCodeSystem('car-brands')
    codeSystemFactory.createCodeSystemVersion('car-brands', '1.0')
    def response = client.POST("/code-systems/car-brands/versions/1.0/concepts", concept).join()
    then:
    response.statusCode() == 201
  }

  def "should get concepts"() {
    setup:
    codeSystemFactory.createConcept('car-brands', '1.0', 'honda')
    codeSystemFactory.createConcept('car-brands', '1.0', 'bentley')
    when:
    def response = client.GET("/code-systems/car-brands/versions/1.0/concepts").join()
    QueryResult<Concept> result = JsonUtil.fromJson(response.body(), JsonUtil.getParametricType(QueryResult.class, Concept.class));
    then:
    response.statusCode() == 200
    result.meta.total == 3
    result.data.size() == 3
  }

}
