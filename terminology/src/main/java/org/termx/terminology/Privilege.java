package org.termx.terminology;

public interface Privilege {
  String CS_READ = "CodeSystem.read";
  String CS_TRIAGE = "CodeSystem.triage";
  String CS_WRITE = "CodeSystem.write";
  String CS_MAINTAIN = "CodeSystem.maintain";

  String DEF_PROP_READ = "DefinedProperty.read";
  String DEF_PROP_WRITE = "DefinedProperty.write";

  String VS_READ = "ValueSet.read";
  String VS_TRIAGE = "ValueSet.triage";
  String VS_WRITE = "ValueSet.write";
  String VS_MAINTAIN = "ValueSet.maintain";

  String MS_READ = "MapSet.read";
  String MS_TRIAGE = "MapSet.triage";
  String MS_WRITE = "MapSet.write";
  String MS_MAINTAIN = "MapSet.maintain";

  String AT_READ = "AssociationType.read";
  String AT_WRITE = "AssociationType.write";
  String AT_MAINTAIN = "AssociationType.maintain";

  String NS_READ = "NamingSystem.read";
  String NS_WRITE = "NamingSystem.write";
  String NS_MAINTAIN = "NamingSystem.maintain";
}
