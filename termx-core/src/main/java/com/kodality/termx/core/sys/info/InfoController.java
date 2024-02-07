package com.kodality.termx.core.sys.info;

import com.kodality.termx.core.auth.Authorized;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Controller("/info")
@RequiredArgsConstructor
public class InfoController {
  private final Info info;

  @Authorized
  @Get
  public Object loadIfo() {
    return info;
  }

  @Getter
  @Setter
  @ConfigurationProperties("info")
  public static class Info {
    private String version;
  }
}
