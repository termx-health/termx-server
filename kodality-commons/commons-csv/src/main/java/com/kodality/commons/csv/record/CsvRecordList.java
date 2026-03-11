package com.kodality.commons.csv.record;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class CsvRecordList {
  private List<String> headers;
  private List<CsvRecord> records;
}
