package org.termx.terminology.fhir

import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import org.termx.snomed.client.SnowstormClient

@Factory
class TestSnowstormClientFactory {
  @Singleton
  SnowstormClient snowstormClient() {
    return new SnowstormClient()
  }
}
