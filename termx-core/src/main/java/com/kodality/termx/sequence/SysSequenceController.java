package com.kodality.termx.sequence;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.auth.Authorized;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.validation.Validated;
import java.util.Map;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;


@Validated
@Controller("/sequences")
@RequiredArgsConstructor
public class SysSequenceController {
  private final SysSequenceService sysSequenceService;

  @Authorized("Sequence.edit")
  @Get("{?params*}")
  public QueryResult<SysSequence> query(SysSequenceQueryParams params) {
    return sysSequenceService.query(params);
  }

  @Authorized("Sequence.view")
  @Get("/{id}")
  public SysSequence load(@Parameter Long id) {
    return sysSequenceService.load(id);
  }

  @Authorized("Sequence.edit")
  @Post
  public SysSequence create(@Body @Valid SysSequence sequence) {
    sequence.setId(null);
    return load(sysSequenceService.save(sequence));
  }

  @Authorized("Sequence.edit")
  @Put("/{id}")
  public SysSequence update(@Parameter Long id, @Body @Valid SysSequence sequence) {
    sequence.setId(id);
    return load(sysSequenceService.save(sequence));
  }

  @Authorized(privilege = "Sequence.view") //TODO: privilege
  @Get("/{code}/next")
  public Map<String, Object> getNextValue(String code) {
    return Map.of("value", sysSequenceService.getNextValue(code));
  }
}
