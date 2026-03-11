package com.kodality.commons.zmei.util;

import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.range.LocalDateRange;
import com.kodality.commons.util.range.LocalDateTimeRange;
import com.kodality.commons.util.range.OffsetDateTimeRange;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.other.Bundle;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;

public final class ZmeiReader {
  public ZmeiReader() {
  }

  public static <T> QueryResult<T> toQueryResult(Bundle bundle, Function<DomainResource, T> mapper) {
    QueryResult<T> local = new QueryResult<>();
    local.getMeta().setTotal(bundle.getTotal());
    local.setData(bundle.getEntry().stream().map(e -> mapper.apply(e.getResource())).collect(toList()));
    return local;
  }

  public static Identifier toIdentifier(com.kodality.zmei.fhir.datatypes.Identifier fhir) {
    return fhir == null ? null : new Identifier(fhir.getSystem(), fhir.getValue());
  }

  public static LocalDateTimeRange toLocalDateTimeRange(Period range) {
    if (range == null) {
      return null;
    }
    return new LocalDateTimeRange(range.getStart() == null ? null : range.getStart().toLocalDateTime(),
        range.getEnd() == null ? null : range.getEnd().toLocalDateTime());
  }

  public static OffsetDateTimeRange toOffsetDateTimeRange(Period range) {
    if (range == null) {
      return null;
    }
    return new OffsetDateTimeRange(range.getStart(), range.getEnd());
  }

  public static LocalDateRange toLocalDateRange(Period range) {
    if (range == null) {
      return null;
    }
    LocalDateRange local = new LocalDateRange();
    local.setLower(range.getStart() == null ? null : range.getStart().toLocalDate());
    local.setUpper(range.getEnd() == null ? null : range.getEnd().toLocalDate());
    return local;
  }

}
