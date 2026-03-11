package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import java.time.LocalTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Availability extends Element {
  private List<AvailabilityAvailableTime> availableTime;
  private List<AvailabilityNotAvailableTime> notAvailableTime;
  @Getter
  @Setter
  @Accessors(chain = true)
  private static class AvailabilityAvailableTime extends Element {
    private List<String> daysOfWeeks;
    private Boolean allDay;
    private LocalTime availableStartTime;
    private LocalTime availableEndTime;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class AvailabilityNotAvailableTime extends Element {
    private String description;
    private Period during;
  }
}
