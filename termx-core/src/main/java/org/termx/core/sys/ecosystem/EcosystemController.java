package org.termx.core.sys.ecosystem;

import com.kodality.commons.model.QueryResult;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.termx.core.Privilege;
import org.termx.core.auth.Authorized;
import org.termx.core.auth.SessionStore;
import org.termx.sys.ecosystem.Ecosystem;
import org.termx.sys.ecosystem.EcosystemQueryParams;

@Controller("/ecosystems")
@RequiredArgsConstructor
public class EcosystemController {
  private final EcosystemService ecosystemService;

  @Authorized(privilege = Privilege.S_EDIT)
  @Post
  public Ecosystem create(@Valid @Body Ecosystem p) {
    p.setId(null);
    return ecosystemService.save(p);
  }

  @Authorized(Privilege.S_EDIT)
  @Put("{id}")
  public Ecosystem update(@PathVariable Long id, @Valid @Body Ecosystem p) {
    p.setId(id);
    return ecosystemService.save(p);
  }

  @Authorized(Privilege.S_VIEW)
  @Get("{id}")
  public Ecosystem load(@PathVariable Long id) {
    return ecosystemService.load(id);
  }

  @Authorized(Privilege.S_VIEW)
  @Get("/{?params*}")
  public QueryResult<Ecosystem> search(EcosystemQueryParams params) {
    params.setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.S_VIEW, Long::valueOf));
    return ecosystemService.query(params);
  }
}
