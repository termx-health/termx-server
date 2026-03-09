package org.termx.task;

/**
 * Task privilege constants.
 * 
 * Note: Actual Task privileges are resource-specific and derived at login from resource privileges.
 * Format: contextType#resourceId.Task.action
 * 
 * Examples:
 * - code-system#icd-10.Task.view
 * - value-set#disorders.Task.edit
 * - map-set#icd10-to-snomed.Task.publish
 * - code-system#*.Task.edit (wildcard - all CodeSystems)
 * 
 * Derivation:
 * - icd-10.CodeSystem.edit → code-system#icd-10.Task.view + code-system#icd-10.Task.edit
 * - icd-10.CodeSystem.publish → code-system#icd-10.Task.view + code-system#icd-10.Task.edit + code-system#icd-10.Task.publish
 */
public interface Privilege {
  // These constants are used in @Authorized annotations and will be checked against derived privileges
  String T_VIEW = "Task.view";
  String T_EDIT = "Task.edit";
  String T_PUBLISH = "Task.publish";
}
