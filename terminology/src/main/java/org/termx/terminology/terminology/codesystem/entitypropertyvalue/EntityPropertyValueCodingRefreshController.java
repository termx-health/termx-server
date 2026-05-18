package org.termx.terminology.terminology.codesystem.entitypropertyvalue;

import com.kodality.commons.exception.NotFoundException;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.HttpStatus;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.termx.core.auth.Authorized;
import org.termx.core.auth.SessionStore;
import org.termx.terminology.Privilege;
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService;
import org.termx.ts.PublicationStatus;
import org.termx.ts.codesystem.CodeSystemEntityVersion;
import org.termx.ts.codesystem.CodingValueUpdateCandidate;

@Controller("/ts")
@RequiredArgsConstructor
public class EntityPropertyValueCodingRefreshController {
  private static final Set<String> VALID_STATUSES = Set.of(PublicationStatus.active, PublicationStatus.draft, PublicationStatus.retired);
  private static final List<String> DEFAULT_STATUSES = List.of(PublicationStatus.active, PublicationStatus.draft);

  private final EntityPropertyValueCodingEnrichmentService enrichmentService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  @Authorized(privilege = Privilege.CS_WRITE)
  @Get(uri = "/code-system-entity-versions/{id}/coding-value-update-candidates")
  public List<CodingValueUpdateCandidate> getEntityVersionCandidates(@PathVariable Long id, @Nullable @QueryValue String allowedStatuses) {
    checkEntityVersionWrite(id);
    return enrichmentService.findOutdatedCodingValuesForEntityVersion(id, parseAndValidate(allowedStatuses));
  }

  @Authorized(privilege = Privilege.CS_WRITE)
  @Post(uri = "/code-system-entity-versions/{id}/refresh-coding-values")
  public Map<String, Object> refreshEntityVersion(@PathVariable Long id, @Nullable @Body RefreshRequest body) {
    checkEntityVersionWrite(id);
    int applied = enrichmentService.recalculateForEntityVersion(id, parseAndValidate(body == null ? null : body.getAllowedStatuses()));
    return Map.of("applied", applied);
  }

  @Authorized(privilege = Privilege.CS_WRITE)
  @Get(uri = "/code-systems/{codeSystem}/versions/{version}/coding-value-update-candidates")
  public List<CodingValueUpdateCandidate> getCodeSystemVersionCandidates(@PathVariable String codeSystem,
                                                                        @PathVariable String version,
                                                                        @Nullable @QueryValue String allowedStatuses) {
    SessionStore.require().checkPermitted(codeSystem, Privilege.CS_WRITE);
    return enrichmentService.findOutdatedCodingValuesForCodeSystemVersion(codeSystem, version, parseAndValidate(allowedStatuses));
  }

  @Authorized(privilege = Privilege.CS_WRITE)
  @Post(uri = "/code-systems/{codeSystem}/versions/{version}/refresh-coding-values")
  public Map<String, Object> refreshCodeSystemVersion(@PathVariable String codeSystem,
                                                     @PathVariable String version,
                                                     @Nullable @Body RefreshRequest body) {
    SessionStore.require().checkPermitted(codeSystem, Privilege.CS_WRITE);
    return enrichmentService.recalculateForCodeSystemVersion(codeSystem, version, parseAndValidate(body == null ? null : body.getAllowedStatuses()));
  }

  private void checkEntityVersionWrite(Long id) {
    CodeSystemEntityVersion csev = codeSystemEntityVersionService.load(id);
    if (csev == null) {
      throw new NotFoundException("CodeSystemEntityVersion not found: " + id);
    }
    SessionStore.require().checkPermitted(csev.getCodeSystem(), Privilege.CS_WRITE);
  }

  private List<String> parseAndValidate(String raw) {
    List<String> statuses = parse(raw);
    for (String s : statuses) {
      if (!VALID_STATUSES.contains(s)) {
        throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + s);
      }
    }
    return statuses;
  }

  private List<String> parseAndValidate(List<String> raw) {
    if (raw == null) {
      return DEFAULT_STATUSES;
    }
    for (String s : raw) {
      if (!VALID_STATUSES.contains(s)) {
        throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + s);
      }
    }
    return raw.isEmpty() ? DEFAULT_STATUSES : raw;
  }

  private List<String> parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return DEFAULT_STATUSES;
    }
    List<String> parsed = Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    return parsed.isEmpty() ? DEFAULT_STATUSES : parsed;
  }

  @Getter
  @Setter
  public static class RefreshRequest {
    private List<String> allowedStatuses;
  }
}
