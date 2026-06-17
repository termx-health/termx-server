package org.termx.ts.valueset;

import java.util.Set;

/**
 * The FHIR ValueSet filter operators: https://build.fhir.org/valueset-filter-operator.html
 *
 * <p>This is the single source of truth for the operators TermX accepts on a value set rule filter.
 * It is enforced on save (see {@code ValueSetVersionRuleService}) and implemented by the expansion
 * functions {@code terminology.value_set_expand(bigint)} / {@code (text)}. SNOMED expansion maps a
 * subset to ECL (see {@code SnomedValueSetExpandProvider}).
 */
public interface ValueSetRuleFilterOperator {
  String equal = "=";
  String is_a = "is-a";
  String descendent_of = "descendent-of";
  String child_of = "child-of";
  String descendent_leaf = "descendent-leaf";
  String is_not_a = "is-not-a";
  String regex = "regex";
  String in = "in";
  String not_in = "not-in";
  String generalizes = "generalizes";
  String exists = "exists";

  Set<String> ALL = Set.of(equal, is_a, descendent_of, child_of, descendent_leaf, is_not_a, regex, in, not_in, generalizes, exists);
}
