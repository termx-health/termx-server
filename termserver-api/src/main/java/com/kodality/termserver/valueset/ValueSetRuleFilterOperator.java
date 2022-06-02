package com.kodality.termserver.valueset;

public interface ValueSetRuleFilterOperator {
  String is_a = "is-a";
  String descendent_of = "descendent-of";
  String is_not_a = "is-not-a";
  String regex = "regex";
  String in = "in";
  String not_in = "not-in";
  String generalizes = "generalizes";
  String exists = "exists";
}
