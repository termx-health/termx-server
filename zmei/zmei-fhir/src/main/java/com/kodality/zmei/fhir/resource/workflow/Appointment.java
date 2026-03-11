package com.kodality.zmei.fhir.resource.workflow;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Annotation;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.datatypes.VirtualServiceDetail;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.ResourceType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Appointment extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private String status;
  private CodeableConcept cancelationReason;
  @JsonProperty("class")
  private List<CodeableConcept> clazz;
  private List<CodeableConcept> serviceCategory;
  private List<CodeableReference> serviceType;
  private List<CodeableConcept> specialty;
  private CodeableConcept appointmentType;
  private List<CodeableReference> reason;
  private CodeableConcept priority;
  private String description;
  private List<Reference> replaces;
  private VirtualServiceDetail virtualService;
  private List<Reference> supportingInformation;
  private Reference previousAppointment;
  private Reference originatingAppointment;
  private OffsetDateTime start;
  private OffsetDateTime end;
  private Integer minutesDuration;
  private List<Period> requestedPeriod;
  private List<Reference> slot;
  private List<Reference> account;
  private OffsetDateTime created;
  private OffsetDateTime cancellationDate;
  private List<Annotation> note;
  private List<CodeableReference> patientInstruction;
  private List<Reference> basedOn;
  private Reference subject;
  private List<AppointmentParticipant> participant;
  private Integer recurrenceId;
  private Boolean occurrenceChanged;
  private List<AppointmentRecurrenceTemplate> recurrenceTemplate;

  public Appointment() {
    super(ResourceType.appointment);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class AppointmentParticipant extends BackboneElement {
    private List<CodeableConcept> type;
    private Period period;
    private Reference actor;
    private Boolean required;
    private String status;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class AppointmentRecurrenceTemplate extends BackboneElement {
    private CodeableConcept timezone;
    private CodeableConcept recurrenceType;
    private LocalDate lastOccurrenceDate;
    private Integer occurrenceCount;
    private LocalDate occurrenceDate;
    private AppointmentRecurrenceTemplateWeeklyTemplate weeklyTemplate;
    private AppointmentRecurrenceTemplateMonthlyTemplate monthlyTemplate;
    private AppointmentRecurrenceTemplateYearlyTemplate yearlyTemplate;
    private LocalDate excludingDate;
    private Integer excludingRecurrenceId;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class AppointmentRecurrenceTemplateWeeklyTemplate extends BackboneElement {
    private Boolean monday;
    private Boolean tuesday;
    private Boolean wednesday;
    private Boolean thursday;
    private Boolean friday;
    private Boolean saturday;
    private Boolean sunday;
    private Integer weekInterval;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class AppointmentRecurrenceTemplateMonthlyTemplate extends BackboneElement {
    private Integer dayOfMonth;
    private Coding nthWeekOfMonth;
    private Coding  dayOfWeek;
    private Integer monthInterval;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class AppointmentRecurrenceTemplateYearlyTemplate extends BackboneElement {
    private Integer yearInterval;
  }

}
