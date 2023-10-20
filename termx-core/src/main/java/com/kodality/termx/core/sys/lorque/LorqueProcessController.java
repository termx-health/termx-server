package com.kodality.termx.core.sys.lorque;

import com.kodality.termx.core.auth.Authorized;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Controller("/lorque-processes")
public class LorqueProcessController {

  private final LorqueProcessService service;

  //TODO: auth
  @Authorized
  @Get("{id}")
  public HttpResponse<?> load(Long id) {
    return HttpResponse.ok(service.load(id));
  }

  @Authorized
  @Get("{id}/status")
  public HttpResponse<?> getStatus(Long id) {
    return HttpResponse.ok(Map.of("status", service.getStatus(id)));
  }

}
