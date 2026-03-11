package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Timing extends Element {
  private List<OffsetDateTime> event;
  private TimingRepeat repeat;
  private CodeableConcept code;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class TimingRepeat extends Element {
    private Duration boundsDuration;
    private Range boundsRange;
    private Period boundsPeriod;
    private Integer count;
    private Integer countMax;
    private BigDecimal duration;
    private BigDecimal durationMax;
    private String durationUnit;
    private Integer frequency;
    private Integer frequencyMax;
    private BigDecimal period;
    private BigDecimal periodMax;
    private String periodUnit;
    private List<String> dayOfWeek;
    private List<LocalTime> timeOfDay;
    private List<String> when;
    private Integer offset;
  }
}
