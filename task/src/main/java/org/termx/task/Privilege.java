package org.termx.task;

/**
 * Task privilege constants.
 *
 * Note: Actual Task privileges are resource-specific and derived at login from resource privileges.
 * Format: contextType#resourceId.Task.action
 *
 * Examples:
 * - code-system#icd-10.Task.read
 * - value-set#disorders.Task.write
 * - map-set#icd10-to-snomed.Task.maintain
 * - code-system#*.Task.write (wildcard - all CodeSystems)
 *
 * Derivation:
 * - icd-10.CodeSystem.write    → code-system#icd-10.Task.read + code-system#icd-10.Task.write
 * - icd-10.CodeSystem.maintain → code-system#icd-10.Task.read + code-system#icd-10.Task.write + code-system#icd-10.Task.maintain
 */
public interface Privilege {
  // These constants are used in @Authorized annotations and will be checked against derived privileges
  String T_READ = "Task.read";
  String T_WRITE = "Task.write";
  String T_MAINTAIN = "Task.maintain";
}
