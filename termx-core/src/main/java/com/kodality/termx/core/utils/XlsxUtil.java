package com.kodality.termx.core.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

public class XlsxUtil {

  public static byte[] composeXlsx(List<String> headers, List<Object[]> rows, String sheetName) {

    Workbook workbook = new XSSFWorkbook();
    Sheet sheet = workbook.createSheet(sheetName);

    int rowNum = 0;
    rowNum = addXlsxRow(headers.toArray(), sheet, rowNum);
    for (Object[] row : rows) {
      rowNum = addXlsxRow(row, sheet, rowNum);
    }

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try {
      workbook.write(bos);
      bos.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return bos.toByteArray();
  }

  private static int addXlsxRow(Object[] array, Sheet sheet, int rowNum) {
    Row row = sheet.createRow(rowNum);
    int cellNum = 0;
    for (Object o : array) {
      Cell cell = row.createCell(cellNum);
      cell.setCellValue(defaultIfNull(o, "").toString());
      cellNum++;
    }
    rowNum++;
    return rowNum;
  }
}
