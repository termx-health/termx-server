package com.kodality.termx.filter;

import io.micronaut.context.propagation.slf4j.MdcPropagationContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import java.util.List;
import java.util.UUID;
import org.reactivestreams.Publisher;
import org.slf4j.MDC;
import reactor.core.publisher.Mono;

@Filter(Filter.MATCH_ALL_PATTERN)
public class HttpTracingFilter implements HttpServerFilter {
  private static final List<String> TRACE_HEADERS = List.of("x-trace-id", "trace-id");
  private static final String MDC_TRACE_ID = "trace-id";

  @Override
  public int getOrder() {
    return ServerFilterPhase.SECURITY.before();
  }

  @Override
  public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
    try {
      String traceId = TRACE_HEADERS.stream()
          .filter(h -> request.getHeaders().contains(h))
          .map(h -> request.getHeaders().get(h))
          .findFirst()
          .orElse(UUID.randomUUID().toString());
      MDC.put(MDC_TRACE_ID, traceId);
      PropagatedContext.get().plus(new MdcPropagationContext()).propagate();
      return Mono.from(chain.proceed(request));
    } finally {
      MDC.remove(MDC_TRACE_ID);
    }
  }
}
