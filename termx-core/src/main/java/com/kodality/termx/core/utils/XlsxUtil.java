package com.kodality.termx.core.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.apache.commons.lang3.StringUtils.isAllBlank;

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

    public static List<String[]> readRows(Sheet sheet, int firstRowNum, int lastRowNum, int width, DataFormatter formatter, FormulaEvaluator evaluator) {
        List<String[]> rows = new ArrayList<>();
        for (int r = firstRowNum + 1; r <= lastRowNum; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }

            String[] values = readRow(row, width, formatter, evaluator);

            if (!isAllBlank(values)) {
                rows.add(values);
            }
        }
        return rows;
    }

    public static String[] readRow(Row row, int width, DataFormatter formatter, FormulaEvaluator evaluator) {
        String[] values = new String[width];
        for (int c = 0; c < width; c++) {
            Cell cell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            values[c] = defaultIfNull(getCellValue(cell, formatter, evaluator), "");
        }
        return values;
    }

    public static String getCellValue(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        return formatter.formatCellValue(cell, evaluator);
    }
}
