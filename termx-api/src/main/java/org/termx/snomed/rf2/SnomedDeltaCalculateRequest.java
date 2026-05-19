package org.termx.snomed.rf2;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Body for {@code POST /snomed/archives/{uuid}/delta}. The path uuid is the "new" / current
 * archive; {@link #baselineUuid} is the older state the delta should be calculated against.
 *
 * <p>{@link #latestState} maps to the {@code --latest-state} flag on the IHTSDO
 * delta-generator-tool. Defaults to {@code true} because Snowstorm chokes on multi-state
 * deltas (see the tool's README "Snowstorm Warning") — admins who want a richer delta for
 * inspection-only purposes can flip it off explicitly.</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class SnomedDeltaCalculateRequest {
  private String baselineUuid;
  private boolean latestState = true;
}
