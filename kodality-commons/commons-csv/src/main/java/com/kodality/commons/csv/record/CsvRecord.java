package com.kodality.commons.csv.record;

import com.kodality.commons.util.DateUtil;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

@ToString
public class CsvRecord {
  private final Map<String, String> valueMap;
  private final List<String> values;
  private final List<String> headers;
  private final long number;

  public CsvRecord(long number, List<String> headers, List<String> values) {
    this.number = number;
    this.values = values;
    this.headers = headers;

    // by default all values are stored with case insensitive keys
    valueMap = new RecordValueMap();
    for (int i = 0; i < headers.size(); i++) {
      valueMap.put(headers.get(i), values.get(i));
    }
  }

  public long getNumber() {
    return number;
  }

  public List<String> getValues() {
    return values;
  }

  public List<String> getHeaders() {
    return headers;
  }

  public boolean has(String name) {
    return headers.contains(name);
  }

  public boolean isNotBlank(String name) {
    return headers.contains(name) && StringUtils.isNotBlank(get(name));
  }

  public String get(String name) {
    return valueMap.get(name);
  }

  public String get(int index) {
    return values.get(index);
  }

  public LocalDate getAsLocalDate(String name) {
    return DateUtil.parseDate(get(name),
                              DateTimeFormatter.ISO_DATE,
                              DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                              DateTimeFormatter.ofPattern("dd.MM.yyyy"));
  }

  public LocalDateTime getAsLocalDateTime(String name) {
    return DateUtil.parseDateTime(get(name),
                                  DateTimeFormatter.ISO_OFFSET_DATE_TIME,
                                  DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                                  DateTimeFormatter.ISO_DATE_TIME,
                                  DateUtil.PG_TIMESTAMP,
                                  DateUtil.PG_TIMESTAMP_X);
  }

  public LocalTime getAsLocalTime(String name) {
    return DateUtil.parseTime(get(name), DateTimeFormatter.ISO_TIME, DateTimeFormatter.ofPattern("HH:mm"));
  }

  public BigDecimal getAsBigDecimal(String name) {
    String value = valueMap.get(name);
    if (value == null) {
      return null;
    }
    return new BigDecimal(value.replaceAll(",", ".").replaceAll(" ", ""));
  }
}
