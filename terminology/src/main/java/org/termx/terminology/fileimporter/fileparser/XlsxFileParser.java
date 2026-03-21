package org.termx.terminology.fileimporter.fileparser;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.termx.terminology.ApiError;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipException;

import lombok.extern.slf4j.Slf4j;

import static org.termx.core.utils.XlsxUtil.readRow;
import static org.termx.core.utils.XlsxUtil.readRows;

/**
 * Parses .xlsx (Office Open XML) files. Requires a worksheet named {@code concepts}.
 * <p>
 * Common failure: user selects "Excel" but uploads CSV/TSV text — that is not a ZIP archive and
 * will fail with {@link ApiError#TE739}.
 */
@Slf4j
public class XlsxFileParser implements IFileParser {

    private final List<String> headers;
    private final List<String[]> rows;

    public XlsxFileParser(byte[] xlsx) {
        validateLooksLikeXlsxPackage(xlsx);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {

            Sheet sheet = workbook.getSheet("concepts");
            if (sheet == null) {
                log.debug("XLSX parse: workbook has no sheet named 'concepts'; available sheets: {}",
                    java.util.stream.StreamSupport.stream(workbook.spliterator(), false).map(Sheet::getSheetName).toList());
                throw ApiError.TE740.toApiException();
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
            if (e instanceof ZipException || hasZipExceptionCause(e)) {
                log.debug("XLSX parse: not a valid OOXML/ZIP file (often CSV uploaded as Excel): {}", e.toString());
                throw ApiError.TE739.toApiException();
            }
            log.warn("XLSX parse failed: {}", e.toString());
            throw ApiError.TE739.toApiException();
        }
    }

    /**
     * .xlsx files are ZIP packages and must start with the local file header signature "PK".
     */
    private static void validateLooksLikeXlsxPackage(byte[] xlsx) {
        if (xlsx == null || xlsx.length < 4) {
            log.debug("XLSX parse: file empty or too short ({} bytes)", xlsx == null ? -1 : xlsx.length);
            throw ApiError.TE739.toApiException();
        }
        if (xlsx[0] != 'P' || xlsx[1] != 'K') {
            log.debug("XLSX parse: file does not start with ZIP signature PK (first bytes: {} {}); likely CSV/TSV or wrong format",
                String.format("0x%02X", xlsx[0]), String.format("0x%02X", xlsx[1]));
            throw ApiError.TE739.toApiException();
        }
    }

    private static boolean hasZipExceptionCause(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof ZipException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
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
