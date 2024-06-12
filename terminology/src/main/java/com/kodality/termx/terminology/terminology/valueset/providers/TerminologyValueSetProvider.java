package com.kodality.termx.terminology.terminology.valueset.providers;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.termx.core.ts.ValueSetProvider;
import com.kodality.termx.terminology.terminology.valueset.ValueSetService;
import com.kodality.termx.terminology.terminology.valueset.version.ValueSetVersionService;
import com.kodality.termx.terminology.terminology.valueset.compare.ValueSetCompareService;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetCompareResult;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
@RequiredArgsConstructor
public class TerminologyValueSetProvider extends ValueSetProvider {
  private final ValueSetService valueSetService;
  private final ValueSetVersionService valueSetVersionService;
  private final ValueSetCompareService valueSetCompareService;

  @Override
  public Optional<ValueSetVersion> loadValueSetVersion(String valueSet, String version) {
    return valueSetVersionService.load(valueSet, version);
  }

  @Override
  public void activateVersion(String valueSet, String version) {
    valueSetVersionService.activate(valueSet, version);
  }

  @Override
  public Pair<ValueSet, ValueSetCompareResult> compareWithPreviousVersion(String valueSet, String version) {
    ValueSetVersion currentVersion = valueSetVersionService.load(valueSet, version)
        .orElseThrow(() -> new NotFoundException("ValueSetVersion not found: " + valueSet + "--" + version));
    ValueSetVersion previousVersion = valueSetVersionService.loadPreviousVersion(valueSet, version);

    ValueSet vs = valueSetService.load(valueSet);
    vs.setVersions(Stream.of(previousVersion, currentVersion).filter(Objects::nonNull).toList());
    if (previousVersion == null) {
      return Pair.of(vs, null);
    }


    ValueSetCompareResult compare = valueSetCompareService.compare(previousVersion.getId(), currentVersion.getId());
    return Pair.of(vs, compare);
  }
}
