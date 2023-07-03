package com.kodality.termx

import com.kodality.commons.client.HttpClient
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification


abstract class TermxIntegTest extends Specification {
  @Inject
  EmbeddedServer server
  @Inject
  ApplicationContext context
  @Shared
  HttpClient client


  void setup() {
    client = new HttpClient(server.URL.toString())
  }
}
