package com.kodality.termx.observationdefinition;

import com.kodality.termx.core.info.ModuleInfo;
import io.micronaut.context.annotation.Bean;

@Bean
public class ApiModuleInfo implements ModuleInfo {
  @Override
  public String getName() {
    return "observation-definition";
  }
}
