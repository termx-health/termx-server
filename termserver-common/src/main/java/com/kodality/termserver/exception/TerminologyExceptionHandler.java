package com.kodality.termserver.exception;

import com.kodality.commons.CommonApiError;
import com.kodality.commons.micronaut.exception.DefaultExceptionHandler;
import com.kodality.commons.model.Issue;
import com.kodality.commons.util.JsonUtil;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import java.util.Collections;
import java.util.concurrent.CompletionException;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class TerminologyExceptionHandler extends DefaultExceptionHandler {

  @Override
  public HttpResponse<?> handle(HttpRequest request, Throwable exception) {
    Throwable ex = exception instanceof CompletionException ? exception.getCause() : exception;
    HttpResponse<?> response = handleClientError(request, ex);
    if (response != null) {
      return response;
    }
    log.error("Got exception while processing {} {}", request.getMethod().name(), request.getPath(), exception);


    HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
    CommonApiError error = CommonApiError.XX100;

    Issue issue = Issue.error(error.getCode(), error.getMessage());
    return HttpResponse.status(status).body(JsonUtil.toJson(Collections.singletonList(issue)));
  }
}
