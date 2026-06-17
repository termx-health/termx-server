package org.termx.terminology.fhir.conformance;

import jakarta.inject.Singleton;
import org.termx.bob.BobContainerAuthorizer;
import org.termx.bob.BobObject;
import org.termx.core.auth.SessionInfo;

/**
 * Authorizes the {@code tx-conformance} Bob container, where custom tx-ecosystem test bundles are
 * uploaded (via the generic {@code POST /bob/objects}) before being run with {@code -input}.
 * Without this bean the generic Bob endpoints treat the container as forbidden.
 *
 * <p>Conformance testing is a server-level operation, so it is gated on the system (Space) privilege,
 * matching {@code TxConformanceController}.
 */
@Singleton
public class TxConformanceBobContainerAuthorizer implements BobContainerAuthorizer {
  public static final String CONTAINER = "tx-conformance";

  @Override
  public String getContainer() {
    return CONTAINER;
  }

  @Override
  public void checkRead(SessionInfo session, BobObject object) {
    session.checkPermitted("*", "Space", "read");
  }

  @Override
  public void checkWrite(SessionInfo session, BobObject object) {
    session.checkPermitted("*", "Space", "write");
  }
}
