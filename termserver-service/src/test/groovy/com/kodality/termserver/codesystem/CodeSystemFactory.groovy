package com.kodality.termserver.codesystem

import com.kodality.commons.model.LocalizedName
import io.micronaut.http.client.HttpClient


class CodeSystemFactory {

  HttpClient client

  CodeSystemFactory(HttpClient client) {
    this.client = client
  }

  def createCodeSystem(String codesystem) {
    CodeSystem system = makeCodeSystem(codesystem)
    client.POST("/code-systems", system).join();
  }

  def createCodeSystem(CodeSystem codesystem) {
    client.POST("/code-systems", codesystem).join();
  }

  CodeSystem makeCodeSystem(String codesystem) {
    new CodeSystem(
        id: codesystem,
        names: new LocalizedName(['en': codesystem + ' codesystem']),
        content: 'example',
        caseSensitive: false
    )
  }

  def createCodeSystemVersion(String codesystem, String version) {
    createCodeSystemVersion(codesystem, version, null)
  }

  def createCodeSystemVersion(String codesystem, String version, String parentVersion) {
    def ver = new CodeSystemVersion(
        codeSystem: codesystem,
        version: version,
        parentVersion: parentVersion,
        status: PublicationStatus.draft
    )
    client.POST("/code-systems/${codesystem}/versions/", ver).join();
  }

  def activateVersion(String codesystem, String version) {
    client.POST("/code-systems/${codesystem}/versions/${version}/activate", []).join()
  }

  def retireVersion(String codesystem, String version) {
    client.POST("/code-systems/${codesystem}/versions/${version}/retire", []).join()
  }

  Concept createConcept(String codesystem, String version, String code) {
    def concept = new Concept(
        code: code,
        codeSystem: codesystem
    )
    when:
    client.POST("/code-systems/${codesystem}/versions/${version}/concepts", concept, Concept.class).join()
  }

  def createConcept(String codesystem, String version, Concept concept) {
    client.POST("/code-systems/${codesystem}/versions/${version}/concepts", concept).join()
  }

}
