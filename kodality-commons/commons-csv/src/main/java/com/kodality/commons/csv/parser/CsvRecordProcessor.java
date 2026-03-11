package com.kodality.commons.csv.parser;

import com.univocity.parsers.common.ParsingContext;
import com.univocity.parsers.common.processor.RowListProcessor;
import com.kodality.commons.csv.record.CsvRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CsvRecordProcessor extends RowListProcessor {

  private List<String> headers;
  private List<CsvRecord> records;

  @Override
  public void processStarted(ParsingContext context) {
    super.processStarted(context);
    headers = Arrays.asList(context.headers());
    records = new ArrayList<>();
  }

  @Override
  public void rowProcessed(String[] row, ParsingContext context) {
    super.rowProcessed(row, context);
    List<String> values = Arrays.asList(row);
    records.add(new CsvRecord(records.size() + 1, headers, values));
  }

  public List<CsvRecord> getRecords() {
    return records;
  }

  public List<String> getHeaderList() {
    return headers;
  }
}
