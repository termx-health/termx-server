package com.kodality.commons.zmei.util;

import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.range.LocalDateRange;
import com.kodality.commons.util.range.OffsetDateTimeRange;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.other.Bundle;
import java.time.ZoneId;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;

public final class ZmeiComposer {
  private ZmeiComposer() {
  }

  public static Identifier toIdentifier(com.kodality.commons.model.Identifier local) {
    return local == null ? null : new Identifier(local.getSystem(), local.getValue());
  }

  public static Period toPeriod(LocalDateRange range) {
    if (range == null) {
      return null;
    }
    Period p = new Period();
    p.setStart(range.getLower() == null ? null : range.getLower().atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime());
    p.setEnd(range.getUpper() == null ? null : range.getUpper().atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime());
    return p;
  }

  public static Period toPeriod(OffsetDateTimeRange range) {
    if (range == null) {
      return null;
    }
    Period p = new Period();
    p.setStart(range.getLower() == null ? null : range.getLower().atZoneSameInstant(ZoneId.systemDefault()).toOffsetDateTime());
    p.setEnd(range.getUpper() == null ? null : range.getUpper().atZoneSameInstant(ZoneId.systemDefault()).toOffsetDateTime());
    return p;
  }

  public static <T> Bundle toBundle(QueryResult<T> result, Function<T, ? extends DomainResource> mapper) {
    if (result == null) {
      return null;
    }
    return Bundle.of("searchset", result.getData() == null ? null : result.getData().stream().map(mapper).collect(toList()));
  }

}
