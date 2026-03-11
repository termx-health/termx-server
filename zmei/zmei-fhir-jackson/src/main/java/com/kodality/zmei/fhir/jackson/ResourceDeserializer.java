package com.kodality.zmei.fhir.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kodality.zmei.fhir.resource.Resource;
import com.kodality.zmei.fhir.resource.ResourceType;
import com.kodality.zmei.fhir.resource.billing.Invoice;
import com.kodality.zmei.fhir.resource.definitionalartifacts.ActivityDefinition;
import com.kodality.zmei.fhir.resource.definitionalartifacts.Questionnaire;
import com.kodality.zmei.fhir.resource.diagnostics.CarePlan;
import com.kodality.zmei.fhir.resource.diagnostics.DiagnosticReport;
import com.kodality.zmei.fhir.resource.diagnostics.ImagingStudy;
import com.kodality.zmei.fhir.resource.diagnostics.Observation;
import com.kodality.zmei.fhir.resource.diagnostics.QuestionnaireResponse;
import com.kodality.zmei.fhir.resource.diagnostics.Specimen;
import com.kodality.zmei.fhir.resource.documents.Composition;
import com.kodality.zmei.fhir.resource.documents.DocumentReference;
import com.kodality.zmei.fhir.resource.entities.HealthcareService;
import com.kodality.zmei.fhir.resource.entities.Location;
import com.kodality.zmei.fhir.resource.entities.Organization;
import com.kodality.zmei.fhir.resource.entities.Substance;
import com.kodality.zmei.fhir.resource.general.ChargeItem;
import com.kodality.zmei.fhir.resource.individual.Patient;
import com.kodality.zmei.fhir.resource.individual.Practitioner;
import com.kodality.zmei.fhir.resource.individual.PractitionerRole;
import com.kodality.zmei.fhir.resource.individual.RelatedPerson;
import com.kodality.zmei.fhir.resource.management.Encounter;
import com.kodality.zmei.fhir.resource.management.EpisodeOfCare;
import com.kodality.zmei.fhir.resource.management.Library;
import com.kodality.zmei.fhir.resource.medications.Dosage;
import com.kodality.zmei.fhir.resource.medications.Immunization;
import com.kodality.zmei.fhir.resource.medications.ImmunizationRecommendation;
import com.kodality.zmei.fhir.resource.medications.Medication;
import com.kodality.zmei.fhir.resource.medications.MedicationAdministration;
import com.kodality.zmei.fhir.resource.medications.MedicationDispense;
import com.kodality.zmei.fhir.resource.medications.MedicationRequest;
import com.kodality.zmei.fhir.resource.medications.MedicationStatement;
import com.kodality.zmei.fhir.resource.other.Basic;
import com.kodality.zmei.fhir.resource.other.Binary;
import com.kodality.zmei.fhir.resource.other.Bundle;
import com.kodality.zmei.fhir.resource.other.Linkage;
import com.kodality.zmei.fhir.resource.other.OperationOutcome;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.request.DeviceRequest;
import com.kodality.zmei.fhir.resource.security.Consent;
import com.kodality.zmei.fhir.resource.security.Provenance;
import com.kodality.zmei.fhir.resource.servicerequest.ServiceRequest;
import com.kodality.zmei.fhir.resource.summary.AllergyIntolerance;
import com.kodality.zmei.fhir.resource.summary.ClinicalImpression;
import com.kodality.zmei.fhir.resource.summary.Condition;
import com.kodality.zmei.fhir.resource.summary.DetectedIssue;
import com.kodality.zmei.fhir.resource.summary.Procedure;
import com.kodality.zmei.fhir.resource.support.Coverage;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap;
import com.kodality.zmei.fhir.resource.terminology.ValueSet;
import com.kodality.zmei.fhir.resource.workflow.Appointment;
import com.kodality.zmei.fhir.resource.workflow.Schedule;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ResourceDeserializer extends JsonDeserializer<Resource> {
  private static final Map<String, Class<? extends Resource>> types = new HashMap<>();

  static {
    types.put(ResourceType.invoice, Invoice.class);

    types.put(ResourceType.activityDefinition, ActivityDefinition.class);
    types.put(ResourceType.questionnaire, Questionnaire.class);
    types.put(ResourceType.imagingStudy, ImagingStudy.class);

    types.put(ResourceType.carePlan, CarePlan.class);
    types.put(ResourceType.diagnosticReport, DiagnosticReport.class);
    types.put(ResourceType.observation, Observation.class);
    types.put(ResourceType.questionnaireResponse, QuestionnaireResponse.class);
    types.put(ResourceType.specimen, Specimen.class);

    types.put(ResourceType.composition, Composition.class);
    types.put(ResourceType.documentReference, DocumentReference.class);

    types.put(ResourceType.healthcareService, HealthcareService.class);
    types.put(ResourceType.location, Location.class);
    types.put(ResourceType.organization, Organization.class);
    types.put(ResourceType.substance, Substance.class);

    types.put(ResourceType.chargeItem, ChargeItem.class);

    types.put(ResourceType.patient, Patient.class);
    types.put(ResourceType.practitioner, Practitioner.class);
    types.put(ResourceType.practitionerRole, PractitionerRole.class);
    types.put(ResourceType.relatedPerson, RelatedPerson.class);

    types.put(ResourceType.encounter, Encounter.class);
    types.put(ResourceType.episodeOfCare, EpisodeOfCare.class);
    types.put(ResourceType.library, Library.class);

    types.put(ResourceType.immunization, Immunization.class);
    types.put(ResourceType.immunizationRecommendation, ImmunizationRecommendation.class);
    types.put(ResourceType.medication, Medication.class);
    types.put(ResourceType.medicationAdministration, MedicationAdministration.class);
    types.put(ResourceType.medicationDispense, MedicationDispense.class);
    types.put(ResourceType.medicationRequest, MedicationRequest.class);
    types.put(ResourceType.medicationStatement, MedicationStatement.class);

    types.put(ResourceType.basic, Basic.class);
    types.put(ResourceType.binary, Binary.class);
    types.put(ResourceType.bundle, Bundle.class);
    types.put(ResourceType.linkage, Linkage.class);
    types.put(ResourceType.operationOutcome, OperationOutcome.class);
    types.put(ResourceType.parameters, Parameters.class);

    types.put(ResourceType.deviceRequest, DeviceRequest.class);

    types.put(ResourceType.consent, Consent.class);
    types.put(ResourceType.provenance, Provenance.class);

    types.put(ResourceType.serviceRequest, ServiceRequest.class);

    types.put(ResourceType.allergyIntolerance, AllergyIntolerance.class);
    types.put(ResourceType.clinicalImpression, ClinicalImpression.class);
    types.put(ResourceType.condition, Condition.class);
    types.put(ResourceType.detectedIssue, DetectedIssue.class);
    types.put(ResourceType.procedure, Procedure.class);

    types.put(ResourceType.coverage, Coverage.class);

    types.put(ResourceType.codeSystem, CodeSystem.class);
    types.put(ResourceType.conceptMap, ConceptMap.class);
    types.put(ResourceType.valueSet, ValueSet.class);

    types.put(ResourceType.appointment, Appointment.class);
    types.put(ResourceType.schedule, Schedule.class);
  }

  @Override
  public Resource deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    ObjectMapper mapper = (ObjectMapper) p.getCodec();
    Map<String, Object> map = mapper.readValue(p, HashMap.class);
    String resourceType = (String) map.get("resourceType");
    return mapper.convertValue(map, getTypeClass(resourceType));
  }

  private Class<? extends Resource> getTypeClass(String type) {
    if (type == null) {
      throw new IllegalArgumentException("Could not construct resource. resourceType is missing");
    }
    if (!types.containsKey(type)) {
      throw new IllegalArgumentException("Could not construct resource. Unknown type " + type + ". ");
    }
    return types.get(type);
  }
}
