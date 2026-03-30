package org.termx.core.fhir;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import javax.sql.DataSource;

interface ConformanceInitializerDependency {
}

@Singleton
@Requires(property = "liquibase.datasources.liquibase.enabled", notEquals = StringUtils.FALSE, defaultValue = StringUtils.TRUE)
final class LiquibaseConformanceInitializerDependency implements ConformanceInitializerDependency {
  LiquibaseConformanceInitializerDependency(@Named("liquibase") DataSource liquibaseDataSource) {
    // Force the Liquibase datasource to initialize first so migrations complete
    // before generated conformance providers touch the application schema.
  }
}

@Singleton
@Requires(property = "liquibase.datasources.liquibase.enabled", value = StringUtils.FALSE)
final class NoopConformanceInitializerDependency implements ConformanceInitializerDependency {
}
