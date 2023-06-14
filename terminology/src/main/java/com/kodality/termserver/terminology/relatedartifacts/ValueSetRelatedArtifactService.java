package com.kodality.termserver.terminology.relatedartifacts;

import com.kodality.termserver.terminology.valueset.ValueSetVersionService;
import com.kodality.termserver.ts.relatedartifact.RelatedArtifact;
import com.kodality.termserver.ts.valueset.ValueSetVersion;
import com.kodality.termserver.ts.valueset.ValueSetVersionQueryParams;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ValueSetRelatedArtifactService extends RelatedArtifactService {
  private final ValueSetVersionService valueSetVersionService;

  @Override
  public String getResourceType() {
    return "ValueSet";
  }

  @Override
  public List<RelatedArtifact> findRelatedArtifacts(String id) {
    List<RelatedArtifact> artifacts = new ArrayList<>();
    artifacts.addAll(collectFromRules(id));
    return artifacts;
  }

  private List<RelatedArtifact> collectFromRules(String id) {
    List<ValueSetVersion> versions = valueSetVersionService.query(new ValueSetVersionQueryParams().setValueSet(id).all()).getData();
    return versions.stream().map(ValueSetVersion::getRuleSet).filter(Objects::nonNull).filter(r -> r.getRules() != null)
        .flatMap(r -> r.getRules().stream()).filter(Objects::nonNull).map(r -> {
          if (r.getCodeSystem() != null) {
            return new RelatedArtifact().setId(r.getCodeSystem()).setType("CodeSystem");
          }
          if (r.getValueSet() != null) {
            return new RelatedArtifact().setId(r.getValueSet()).setType("ValueSet");
          }
          return null;
        }).filter(Objects::nonNull).filter(distinctByKey(ra -> ra.getType() + ra.getId())).collect(Collectors.toList());
  }

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }
}
