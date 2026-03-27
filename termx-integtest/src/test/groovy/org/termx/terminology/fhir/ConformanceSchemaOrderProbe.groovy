package org.termx.terminology.fhir

import jakarta.inject.Singleton

@Singleton
class ConformanceSchemaOrderProbe {
  private boolean terminologyCapabilityVerified

  void markTerminologyCapabilityVerified() {
    terminologyCapabilityVerified = true
  }

  boolean isTerminologyCapabilityVerified() {
    return terminologyCapabilityVerified
  }
}
