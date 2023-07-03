package com.kodality.termx.sequence;

import com.kodality.commons.model.QueryResult;
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

  @Get("{?params*}")
  public QueryResult<SysSequence> query(SysSequenceQueryParams params) {
    return sysSequenceService.query(params);
  }

  @Get("/{id}")
  public SysSequence load(Long id) {
    return sysSequenceService.load(id);
  }

  @Post
  public SysSequence create(@Body @Valid SysSequence sequence) {
    sequence.setId(null);
    return load(sysSequenceService.save(sequence));
  }

  @Put("/{id}")
  public SysSequence update(Long id, @Body @Valid SysSequence sequence) {
    sequence.setId(id);
    return load(sysSequenceService.save(sequence));
  }

  @Get("/{code}/next")
  public Map<String, Object> getNextValue(String code) {
    return Map.of("value", sysSequenceService.getNextValue(code));
  }
}
