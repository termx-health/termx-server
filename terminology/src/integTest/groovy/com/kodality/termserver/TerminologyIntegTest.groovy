package com.kodality.termserver

import com.kodality.commons.client.HttpClient
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

abstract class TerminologyIntegTest extends Specification {
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
