package com.kodality.termx.core.info;

import com.kodality.termx.core.auth.Authorized;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import java.util.List;
import lombok.RequiredArgsConstructor;

@Controller("/info")
@RequiredArgsConstructor
public class InfoController {
  private final List<ModuleInfo> modules;

  @Authorized
  @Get("/modules")
  public List<String> modules() {
    return modules.stream().map(ModuleInfo::getName).sorted().toList();
  }
}
