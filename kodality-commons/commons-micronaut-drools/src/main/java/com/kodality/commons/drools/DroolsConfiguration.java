package com.kodality.commons.drools;

import java.util.Map;

public interface DroolsConfiguration {
  Map<String, Class<?>> getClassMappings();
}
