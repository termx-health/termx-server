package com.kodality.termserver.fhir;

import com.kodality.commons.model.Issue;
import com.kodality.commons.util.JsonUtil;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.resource.other.OperationOutcome;
import com.kodality.zmei.fhir.resource.other.OperationOutcome.OperationOutcomeIssue;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.reactivex.Flowable;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.reactivestreams.Publisher;

@Filter("/fhir/**")
@RequiredArgsConstructor
public class FhirExceptionConverter implements HttpServerFilter {


  @Override
  public int getOrder() {
    return 2;
  }

  @Override
  public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
    return Flowable.fromPublisher(chain.proceed(request)).flatMap(response -> {
      if (response.getStatus().getCode() < 400) {
        return Flowable.just(response);
      }
      List<Issue> issues = JsonUtil.fromJson((String) response.getBody().orElseThrow(), JsonUtil.getListType(Issue.class));
      OperationOutcome oo = new OperationOutcome();
      oo.setIssue(issues.stream().map(this::toFhir).toList());
      return Flowable.just(HttpResponse.status(response.getStatus()).body(FhirMapper.toJson(oo)));
    });
  }

  private OperationOutcomeIssue toFhir(Issue issue) {
    OperationOutcomeIssue fhir = new OperationOutcomeIssue();
    fhir.setCode(issue.getCode());
    fhir.setSeverity(issue.getSeverity().name());
    fhir.setDetails(new CodeableConcept().setText(issue.getMessage()));
    return fhir;
  }

}
