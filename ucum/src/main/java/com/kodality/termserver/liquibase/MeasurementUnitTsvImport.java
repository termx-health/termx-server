package com.kodality.termserver.liquibase;

import com.kodality.commons.micronaut.BeanContext;
import com.kodality.commons.model.LocalizedName;
import com.kodality.commons.util.DateUtil;
import com.kodality.commons.util.range.LocalDateRange;
import com.kodality.termserver.AuthorizedFileReaderCustomChange;
import com.kodality.termserver.auth.CommonSessionProvider;
import com.kodality.termserver.measurementunit.MeasurementUnitService;
import com.kodality.termserver.ucum.MeasurementUnit;
import com.univocity.parsers.common.processor.RowListProcessor;
import com.univocity.parsers.tsv.TsvParser;
import com.univocity.parsers.tsv.TsvParserSettings;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MeasurementUnitTsvImport extends AuthorizedFileReaderCustomChange {
  private final MeasurementUnitService measurementUnitService;

  public MeasurementUnitTsvImport() {
    super(BeanContext.getBean(CommonSessionProvider.class));
    measurementUnitService = BeanContext.getBean(MeasurementUnitService.class);
  }

  @Override
  protected void handleMigrationFile(String name, byte[] content) {
    log.info("Updating measurement units from " + name);
    RowListProcessor processor = csvProcessor(content);

    List<String> headers = Arrays.asList(processor.getHeaders());
    processor.getRows().forEach(row -> {
      MeasurementUnit unit = parseRow(headers, row);
      measurementUnitService.merge(unit);
    });
  }

  private MeasurementUnit parseRow(List<String> headers, String[] row) {
    MeasurementUnit u = new MeasurementUnit();
    u.setCode(row[headers.indexOf("Code")]);
    u.setNames(new LocalizedName(Map.of("en", row[headers.indexOf("ConceptID")])));
    u.setAlias(new LocalizedName(Map.of("en", row[headers.indexOf("Synonym")])));
    u.setPeriod(new LocalDateRange(parseDate(row[headers.indexOf("Date Created")]), null));
    u.setKind(row[headers.indexOf("Kind of Quantity")]);
    return u;
  }

  protected RowListProcessor csvProcessor(byte[] csv) {
    RowListProcessor processor = new RowListProcessor();
    TsvParserSettings settings = new TsvParserSettings();
    settings.setLineSeparatorDetectionEnabled(true);
    settings.setProcessor(processor);
    settings.setHeaderExtractionEnabled(true);
    new TsvParser(settings).parse(new ByteArrayInputStream(csv));
    return processor;
  }

  private LocalDate parseDate(String date) {
    if (date == null) {
      return null;
    }
    return DateUtil.parseDate(date, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
  }
}
