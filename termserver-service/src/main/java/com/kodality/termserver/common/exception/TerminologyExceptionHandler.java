package com.kodality.termserver.common.exception;

import com.kodality.commons.CommonApiError;
import com.kodality.commons.client.HttpClientError;
import com.kodality.commons.exception.ApiException;
import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.micronaut.exception.DefaultExceptionHandler;
import com.kodality.commons.model.Issue;
import com.kodality.commons.util.JsonUtil;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;
import javax.inject.Singleton;
import javax.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;

import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;

@Slf4j
@Singleton
@Replaces(DefaultExceptionHandler.class)
public class TerminologyExceptionHandler implements ExceptionHandler<Throwable, HttpResponse<?>> {

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

  protected HttpResponse<?> handleClientError(HttpRequest request, Throwable exception) {
    if (exception instanceof ApiException) {
      return handleApiException(request, (ApiException) exception);
    }
    if (exception instanceof ConstraintViolationException) {
      return handleValidationException(request, (ConstraintViolationException) exception);
    }
    if (exception instanceof HttpClientError) {
      return handleHttpClientError(((HttpClientError) exception));
    }
    return null;
  }

  protected HttpResponse<?> handleValidationException(HttpRequest request, ConstraintViolationException exception) {
    log.error("Got validation exception while processing {} {}. Error: {}", request.getMethod().name(), request.getPath(), exception.getMessage());
    HttpStatus status = HttpStatus.BAD_REQUEST;
    CommonApiError error = CommonApiError.XX102;
    List<Issue> issues = exception.getConstraintViolations().stream().map(v -> {
      String details = v.getPropertyPath().toString() + " " + v.getMessage();
      return Issue.error(error.getCode(), error.getMessage()).setParams(singletonMap("details", details));
    }).collect(toList());
    return HttpResponse.status(status).body(JsonUtil.toJson(issues));
  }

  protected HttpResponse<?> handleApiException(HttpRequest request, ApiException exception) {
    HttpStatus status = HttpStatus.valueOf(exception.getHttpStatus());
    if (exception instanceof NotFoundException) {
      log.error("Got 'Not Found' exception while processing {} {}", request.getMethod(), request.getUri().getPath(), exception);
    } else if (status.getCode() >= 402) {
      log.error("Got API exception while processing {} {}", request.getMethod(), request.getUri().getPath(), exception);
    } else {
      log.debug("Got API exception while processing {} {}. Error: {}", request.getMethod(), request.getUri().getPath(), exception.getMessage());
    }
    if (exception.getIssues() != null) {
      exception.getIssues().forEach(issue -> issue.setMessage(substituteParams(issue)));
    }
    return HttpResponse.status(status).body(JsonUtil.toJson(exception.getIssues()));
  }

  protected HttpResponse<?> handleHttpClientError(HttpClientError exception) {
    HttpStatus status = HttpStatus.valueOf(exception.getResponse().statusCode());
    java.net.http.HttpRequest request = exception.getRequest();
    if (status.getCode() >= 402) {
      log.error("Got client error while processing {} {}", request.method(), request.uri().getPath(), exception);
    } else {
      log.debug("Got client error while processing {} {}. Error: {}", request.method(), request.uri().getPath(), exception.getMessage());
    }
    if (exception.getIssues() != null) {
      exception.getIssues().forEach(issue -> issue.setMessage(substituteParams(issue)));
    }
    return HttpResponse.status(status).body(JsonUtil.toJson(exception.getIssues()));
  }

  protected String substituteParams(Issue issue) {
    return StringSubstitutor.replace(issue.getMessage(), issue.getParams(), "{{", "}}");
  }

}
