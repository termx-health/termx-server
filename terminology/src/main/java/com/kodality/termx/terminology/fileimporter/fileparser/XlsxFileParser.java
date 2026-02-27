package com.kodality.termx.terminology.fileimporter.fileparser;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static com.kodality.termx.core.utils.XlsxUtil.readRow;
import static com.kodality.termx.core.utils.XlsxUtil.readRows;

public class XlsxFileParser implements IFileParser {

    private final List<String> headers;
    private final List<String[]> rows;

    public XlsxFileParser(byte[] xlsx) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {

            Sheet sheet = workbook.getSheet("concepts");
            if (sheet == null) {
                throw new IllegalArgumentException("Could not find sheet named 'concepts'");
            }

            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            int firstRowNum = sheet.getFirstRowNum();
            int lastRowNum = sheet.getLastRowNum();

            Row headerRow = sheet.getRow(firstRowNum);
            if (headerRow == null) {
                this.headers = List.of();
                this.rows = List.of();
                return;
            }
            int headerWidth = Math.max(0, headerRow.getLastCellNum());

            this.headers = List.of(readRow(headerRow, headerWidth, formatter, evaluator));
            this.rows = readRows(sheet, firstRowNum + 1, lastRowNum, headerWidth, formatter, evaluator);

        } catch (IOException e) {
            throw new RuntimeException("Failed to parse XLSX", e);
        }
    }

    @Override
    public List<String> getHeaders() {
        return headers;
    }

    @Override
    public List<String[]> getRows() {
        return rows;
    }
}
