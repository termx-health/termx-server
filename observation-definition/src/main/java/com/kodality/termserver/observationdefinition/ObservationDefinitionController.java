package com.kodality.termserver.observationdefinition;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.Privilege;
import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.observationdefintion.ObservationDefinition;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller("/observation-definitions")
@RequiredArgsConstructor
public class ObservationDefinitionController {
  private final ObservationDefinitionService observationDefinitionService;

  @Authorized(Privilege.OBS_DEF_EDIT)
  @Post()
  public ObservationDefinition create(@Body @Valid ObservationDefinition def) {
    def.setId(null);
    observationDefinitionService.save(def);
    return load(def.getId());
  }

  @Authorized(Privilege.OBS_DEF_EDIT)
  @Put("/{id}")
  public ObservationDefinition update(@Parameter Long id, @Body @Valid ObservationDefinition def) {
    def.setId(id);
    observationDefinitionService.save(def);
    return load(def.getId());
  }

  @Authorized(Privilege.OBS_DEF_VIEW)
  @Get("/{id}")
  public ObservationDefinition load(@Parameter Long id) {
    ObservationDefinition def = observationDefinitionService.load(id);
    if (def == null) {
      throw new NotFoundException("Observation definition", id);
    }
    return def;
  }

  @Authorized(Privilege.OBS_DEF_VIEW)
  @Get("/{?params*}")
  public QueryResult<ObservationDefinition> search(ObservationDefinitionSearchParams params) {
    return observationDefinitionService.search(params);
  }

}
