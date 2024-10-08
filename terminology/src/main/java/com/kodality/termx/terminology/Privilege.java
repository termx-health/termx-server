package com.kodality.termx.terminology;

public interface Privilege {
  String CS_VIEW = "CodeSystem.view";
  String CS_EDIT = "CodeSystem.edit";
  String CS_PUBLISH = "CodeSystem.publish";

  String DEF_PROP_VIEW = "DefinedProperty.view";
  String DEF_PROP_EDIT = "DefinedProperty.edit";

  String VS_VIEW = "ValueSet.view";
  String VS_EDIT = "ValueSet.edit";
  String VS_PUBLISH = "ValueSet.publish";

  String MS_VIEW = "MapSet.view";
  String MS_EDIT = "MapSet.edit";
  String MS_PUBLISH = "MapSet.publish";

  String AT_VIEW = "AssociationType.view";
  String AT_EDIT = "AssociationType.edit";
  String AT_PUBLISH= "AssociationType.publish";

  String NS_VIEW = "NamingSystem.view";
  String NS_EDIT = "NamingSystem.edit";
  String NS_PUBLISH = "NamingSystem.publish";
}
