package com.kodality.termserver.ts.valueset.concept.external;

import com.kodality.termserver.valueset.ValueSetVersionConcept;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet;
import java.util.List;

public abstract class ValueSetExternalExpandProvider {

  public abstract List<ValueSetVersionConcept> expand(Long versionId, ValueSetVersionRuleSet ruleSet);
}
