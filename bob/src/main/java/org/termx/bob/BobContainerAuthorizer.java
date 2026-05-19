package org.termx.bob;

import org.termx.core.auth.SessionInfo;

/**
 * Plugin contract that lets each owning module declare which Bob container it owns
 * and what authorisation the generic {@code /bob/objects} REST endpoints must enforce
 * for operations on that container.
 *
 * <p>One bean per container value (e.g. {@code "wiki"}, {@code "snomed"}, {@code "loinc"}).
 * The {@link BobObjectController} collects every {@code BobContainerAuthorizer} bean
 * via Micronaut multi-bean injection and dispatches by {@link #getContainer()}.</p>
 *
 * <p>If no authorizer is registered for a container value, the controller treats it as
 * forbidden. This is intentional — a new bucket never becomes reachable through the
 * generic REST API without an explicit authz declaration.</p>
 */
public interface BobContainerAuthorizer {

  /** The Bob container value this authorizer governs (matches {@code BobStorage.container}). */
  String getContainer();

  /**
   * Verify the session may read an object in this container, throwing
   * {@link com.kodality.commons.exception.ForbiddenException} otherwise.
   *
   * @param session the authenticated session
   * @param object  the object being read; {@code null} on list/query operations (so authorizers
   *                that need to scope by {@link BobObject#getMeta() meta} should require the
   *                broadest privilege when {@code object} is {@code null})
   */
  void checkRead(SessionInfo session, BobObject object);

  /**
   * Verify the session may create / update / delete an object in this container, throwing
   * {@link com.kodality.commons.exception.ForbiddenException} otherwise.
   *
   * @param session the authenticated session
   * @param object  the object being mutated; on create the id and uuid are not yet populated
   */
  void checkWrite(SessionInfo session, BobObject object);
}
