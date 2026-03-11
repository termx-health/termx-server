package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Period extends Element {
  private OffsetDateTime start;
  private OffsetDateTime end;

  public Period() {
  }

  public Period(LocalDateTime start, LocalDateTime end) {
    ZoneId defaultZone = ZoneId.systemDefault();
    this.start = start == null ? null : start.atZone(defaultZone).toOffsetDateTime();
    this.end = end == null ? null : end.atZone(defaultZone).toOffsetDateTime();
  }

  public Period(OffsetDateTime start, OffsetDateTime end) {
    this.start = start;
    this.end = end;
  }
}
