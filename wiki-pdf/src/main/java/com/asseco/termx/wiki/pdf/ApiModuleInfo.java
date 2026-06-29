package com.asseco.termx.wiki.pdf;

import org.termx.core.info.ModuleInfo;
import io.micronaut.context.annotation.Bean;

@Bean
public class ApiModuleInfo implements ModuleInfo {
  @Override
  public String getName() {
    return "wiki-pdf";
  }
}
