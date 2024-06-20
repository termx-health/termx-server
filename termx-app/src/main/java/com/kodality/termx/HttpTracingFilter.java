package com.kodality.termx;

import io.micronaut.context.propagation.slf4j.MdcPropagationContext;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@ServerFilter(Filter.MATCH_ALL_PATTERN)
@Order(value = Ordered.HIGHEST_PRECEDENCE)
public class HttpTracingFilter {
  private static final List<String> TRACE_HEADERS = List.of("x-trace-id", "trace-id");
  private static final String MDC_TRACE_ID = "trace-id";

  @RequestFilter
  public void filter(HttpRequest<?> request, MutablePropagatedContext mutablePropagatedContext) {
    try {
      String traceId = TRACE_HEADERS.stream()
          .filter(h -> request.getHeaders().contains(h))
          .map(h -> request.getHeaders().get(h))
          .findFirst()
          .orElse(UUID.randomUUID().toString());
      MDC.put(MDC_TRACE_ID, traceId);
      mutablePropagatedContext.add(new MdcPropagationContext());
    } finally {
      MDC.remove(MDC_TRACE_ID);
    }
  }
}
