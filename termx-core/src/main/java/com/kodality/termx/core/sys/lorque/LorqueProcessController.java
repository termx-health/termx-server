package com.kodality.termx.core.sys.lorque;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Controller("/lorque-processes")
public class LorqueProcessController {

  private final LorqueProcessService service;

  @Get("{id}")
  public HttpResponse<?> load(Long id) {
    return HttpResponse.ok(service.load(id));
  }

  @Get("{id}/status")
  public HttpResponse<?> getStatus(Long id) {
    return HttpResponse.ok(Map.of("status", service.getStatus(id)));
  }

}
