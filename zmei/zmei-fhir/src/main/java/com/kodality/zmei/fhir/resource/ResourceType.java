package com.kodality.zmei.fhir.resource;

public interface ResourceType {
  String invoice = "Invoice";

  String activityDefinition = "ActivityDefinition";
  String questionnaire = "Questionnaire";
  String imagingStudy = "ImagingStudy";

  String carePlan = "CarePlan";
  String diagnosticReport = "DiagnosticReport";
  String observation = "Observation";
  String questionnaireResponse = "QuestionnaireResponse";
  String specimen = "Specimen";

  String composition = "Composition";
  String documentReference = "DocumentReference";

  String healthcareService = "HealthcareService";
  String location = "Location";
  String organization = "Organization";
  String substance = "Substance";

  String chargeItem = "ChargeItem";

  String patient = "Patient";
  String practitioner = "Practitioner";
  String practitionerRole = "PractitionerRole";
  String relatedPerson = "RelatedPerson";

  String encounter = "Encounter";
  String episodeOfCare = "EpisodeOfCare";
  String library = "Library";

  String immunization = "Immunization";
  String immunizationRecommendation = "ImmunizationRecommendation";
  String medication = "Medication";
  String medicationAdministration = "MedicationAdministration";
  String medicationDispense = "MedicationDispense";
  String medicationRequest = "MedicationRequest";
  String medicationStatement = "MedicationStatement";

  String basic = "Basic";
  String binary = "Binary";
  String bundle = "Bundle";
  String linkage = "Linkage";
  String operationOutcome = "OperationOutcome";
  String parameters = "Parameters";

  String deviceRequest = "DeviceRequest";

  String consent = "Consent";
  String provenance = "Provenance";

  String serviceRequest = "ServiceRequest";

  String allergyIntolerance = "AllergyIntolerance";
  String clinicalImpression = "ClinicalImpression";
  String condition = "Condition";
  String detectedIssue = "DetectedIssue";
  String procedure = "Procedure";

  String coverage = "Coverage";

  String codeSystem = "CodeSystem";
  String conceptMap = "ConceptMap";
  String valueSet = "ValueSet";

  String appointment = "Appointment";
  String schedule = "Schedule";
}
