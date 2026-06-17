package org.termx.terminology.fhir.conformance;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import lombok.RequiredArgsConstructor;
import org.termx.core.Privilege;
import org.termx.core.auth.Authorized;
import org.termx.sys.lorque.LorqueProcess;
import org.termx.ts.conformance.TxConformanceRunRequest;

/**
 * Triggers a tx-ecosystem conformance run against this server. Returns the async {@link LorqueProcess}
 * immediately; poll {@code GET /lorque-processes/{id}} for status and the FHIR {@code TestReport}.
 *
 * <p>This is the "static, callable resource" — usable from CLI/CI ({@code curl}) and from the UI.
 * Custom test bundles are uploaded via the generic {@code POST /bob/objects} (container
 * {@code tx-conformance}) and referenced here by {@code archiveUuid}.
 */
@Controller("/tx-conformance")
@RequiredArgsConstructor
public class TxConformanceController {
  private final TxConformanceService txConformanceService;

  @Authorized(Privilege.S_WRITE)
  @Post("/runs")
  public LorqueProcess run(@Nullable @Body TxConformanceRunRequest request) {
    return txConformanceService.run(request == null ? new TxConformanceRunRequest() : request);
  }
}
