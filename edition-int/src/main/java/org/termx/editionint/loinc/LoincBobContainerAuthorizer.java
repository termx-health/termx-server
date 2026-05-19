package org.termx.editionint.loinc;

import org.termx.bob.BobContainerAuthorizer;
import org.termx.bob.BobObject;
import org.termx.core.auth.SessionInfo;
import jakarta.inject.Singleton;

/**
 * Authorizes access to LOINC release archives stored in the {@code "loinc"} Bob container.
 *
 * <p>The LOINC import is gated by {@code *.CodeSystem.write} (same as the existing
 * {@code /loinc/import} endpoint), so we use the same privilege string here. Read goes against
 * a {@code loinc.CodeSystem.read} convention to allow admins listing archives in the UI without
 * needing global write.</p>
 */
@Singleton
public class LoincBobContainerAuthorizer implements BobContainerAuthorizer {
  public static final String CONTAINER = "loinc";

  @Override
  public String getContainer() {
    return CONTAINER;
  }

  @Override
  public void checkRead(SessionInfo session, BobObject object) {
    session.checkPermitted("loinc", "CodeSystem", "read");
  }

  @Override
  public void checkWrite(SessionInfo session, BobObject object) {
    session.checkPermitted("loinc", "CodeSystem", "write");
  }
}
