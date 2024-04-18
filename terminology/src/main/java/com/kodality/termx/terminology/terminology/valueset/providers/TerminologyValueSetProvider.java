package com.kodality.termx.terminology.terminology.valueset.providers;

import com.kodality.termx.core.ts.ValueSetProvider;
import com.kodality.termx.terminology.terminology.valueset.ValueSetVersionService;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyValueSetProvider extends ValueSetProvider {
  private final ValueSetVersionService valueSetVersionService;

  @Override
  public Optional<ValueSetVersion> loadValueSetVersion(String valueSet, String version) {
    return valueSetVersionService.load(valueSet, version);
  }

  @Override
  public void activateVersion(String valueSet, String version) {
    valueSetVersionService.activate(valueSet, version);
  }
}
