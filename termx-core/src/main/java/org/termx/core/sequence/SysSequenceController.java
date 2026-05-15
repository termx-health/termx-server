package org.termx.core.sequence;

import com.kodality.commons.model.QueryResult;
import org.termx.core.auth.Authorized;
import org.termx.sequence.SysSequence;
import org.termx.sequence.SysSequenceQueryParams;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.validation.Validated;
import java.util.Map;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@Validated
@Controller("/sequences")
@RequiredArgsConstructor
public class SysSequenceController {
  private final SysSequenceService sysSequenceService;

  @Authorized("Sequence.read")
  @Get("{?params*}")
  public QueryResult<SysSequence> query(SysSequenceQueryParams params) {
    return sysSequenceService.query(params);
  }

  @Authorized("Sequence.read")
  @Get("/{id}")
  public SysSequence load(@PathVariable Long id) {
    return sysSequenceService.load(id);
  }

  @Authorized("Sequence.write")
  @Post
  public SysSequence create(@Body @Valid SysSequence sequence) {
    sequence.setId(null);
    return load(sysSequenceService.save(sequence));
  }

  @Authorized("Sequence.write")
  @Put("/{id}")
  public SysSequence update(@PathVariable Long id, @Body @Valid SysSequence sequence) {
    sequence.setId(id);
    return load(sysSequenceService.save(sequence));
  }

  @Authorized(privilege = "Sequence.read") //TODO: privilege
  @Get("/{code}/next")
  public Map<String, Object> getNextValue(String code) {
    return Map.of("value", sysSequenceService.getNextValue(code));
  }
}
