package org.termx.terminology.fhir

import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import org.hl7.fhir.r5.model.Enumerations
import org.hl7.fhir.r5.model.Resource
import org.hl7.fhir.r5.model.TerminologyCapabilities
import org.termx.core.fhir.ConformanceInitializer.TermxGeneratedConformanceProvider

import java.sql.DriverManager
import java.util.Date

@Singleton
@Replaces(TerminologyCapabilityInitializer)
class SchemaAssertingTerminologyCapabilityInitializer implements TermxGeneratedConformanceProvider {
  private final ConformanceSchemaOrderProbe probe
  private final String url
  private final String username
  private final String password
  private final String apiUrl

  SchemaAssertingTerminologyCapabilityInitializer(@Value('${datasources.default.url}') String url,
                                                  @Value('${datasources.default.username}') String username,
                                                  @Value('${datasources.default.password}') String password,
                                                  @Value('${termx.api-url}') String apiUrl,
                                                  ConformanceSchemaOrderProbe probe) {
    this.url = url
    this.username = username
    this.password = password
    this.apiUrl = apiUrl
    this.probe = probe
  }

  @Override
  Resource generate(String name) {
    if (name != "TerminologyCapabilities") {
      return null
    }
    assertMigratedColumnsExist()
    probe.markTerminologyCapabilityVerified()
    return new TerminologyCapabilities()
        .setUrl("${apiUrl}/fhir/metadata")
        .setVersion("test")
        .setName("Test Terminology Statement")
        .setTitle("Test Terminology Statement")
        .setStatus(Enumerations.PublicationStatus.ACTIVE)
        .setDate(new Date())
        .setKind(Enumerations.CapabilityStatementKind.INSTANCE)
  }

  private void assertMigratedColumnsExist() {
    def sql = """
      select count(*)
      from information_schema.columns
      where table_schema = 'terminology'
        and table_name = 'code_system_version'
        and column_name in ('uri', 'base_code_system_version_id')
    """
    DriverManager.getConnection(url, username, password).withCloseable { connection ->
      connection.prepareStatement(sql).withCloseable { statement ->
        statement.executeQuery().withCloseable { rs ->
          rs.next()
          if (rs.getInt(1) != 2) {
            throw new IllegalStateException("Conformance loaded before Liquibase created terminology.code_system_version.uri and base_code_system_version_id")
          }
        }
      }
    }
  }
}
