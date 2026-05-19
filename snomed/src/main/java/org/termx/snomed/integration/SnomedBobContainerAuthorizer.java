package org.termx.snomed.integration;

import org.termx.bob.BobContainerAuthorizer;
import org.termx.bob.BobObject;
import org.termx.core.auth.SessionInfo;
import org.termx.snomed.Privilege;
import jakarta.inject.Singleton;

/**
 * Authorizes access to SNOMED RF2 archives stored in the {@code "snomed"} Bob container.
 *
 * <p>Read requires {@link Privilege#SNOMED_READ}, write / delete requires {@link
 * Privilege#SNOMED_WRITE} — matching the existing {@code SnomedController} endpoints. Without
 * this bean, {@code /bob/objects?container=snomed} would be forbidden by the controller's
 * default-deny dispatch.</p>
 */
@Singleton
public class SnomedBobContainerAuthorizer implements BobContainerAuthorizer {
  public static final String CONTAINER = "snomed";

  @Override
  public String getContainer() {
    return CONTAINER;
  }

  @Override
  public void checkRead(SessionInfo session, BobObject object) {
    session.checkPermitted("snomed-ct", "CodeSystem", "read");
  }

  @Override
  public void checkWrite(SessionInfo session, BobObject object) {
    session.checkPermitted("snomed-ct", "CodeSystem", "write");
  }
}
