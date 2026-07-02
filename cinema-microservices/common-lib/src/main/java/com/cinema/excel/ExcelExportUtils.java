package com.cinema.excel;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

public final class ExcelExportUtils {

    private static final String EXCEL_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private ExcelExportUtils() {
    }

    public static byte[] exportSingleSheet(String sheetName, List<String> headers, List<? extends List<?>> rows) {
        return exportSingleSheet(sheetName, null, headers, rows);
    }

    public static byte[] exportSingleSheet(
            String sheetName,
            String title,
            List<String> headers,
            List<? extends List<?>> rows) {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(normalizeSheetName(sheetName));
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            int headerRowIndex = writeTitleRow(sheet, title, headers == null ? 0 : headers.size(), titleStyle);

            writeHeaderRow(sheet, headerRowIndex, headers, headerStyle);
            writeRows(sheet, headerRowIndex + 1, rows);
            autoSizeColumns(sheet, headers == null ? 0 : headers.size());

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to export excel workbook", ex);
        }
    }

    public static ResponseEntity<byte[]> buildDownloadResponse(byte[] content, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + sanitizeFilename(filename) + "\"")
                .contentType(MediaType.parseMediaType(EXCEL_MIME))
                .contentLength(content == null ? 0 : content.length)
                .body(content == null ? new byte[0] : content);
    }

    private static int writeTitleRow(Sheet sheet, String title, int columnCount, CellStyle titleStyle) {
        if (!StringUtils.hasText(title)) {
            return 0;
        }

        Row row = sheet.createRow(0);
        Cell cell = row.createCell(0);
        cell.setCellValue(title.trim());
        cell.setCellStyle(titleStyle);

        int lastColumnIndex = Math.max(0, columnCount - 1);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, lastColumnIndex));
        for (int i = 1; i <= lastColumnIndex; i++) {
            Cell mergedCell = row.createCell(i);
            mergedCell.setCellStyle(titleStyle);
        }

        return 1;
    }

    private static void writeHeaderRow(Sheet sheet, int rowIndex, List<String> headers, CellStyle headerStyle) {
        Row row = sheet.createRow(rowIndex);
        if (headers == null || headers.isEmpty()) {
            return;
        }

        for (int i = 0; i < headers.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
        }
    }

    private static void writeRows(Sheet sheet, int startRowIndex, List<? extends List<?>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        int rowIndex = startRowIndex;
        for (List<?> rowData : rows) {
            Row row = sheet.createRow(rowIndex++);
            if (rowData == null || rowData.isEmpty()) {
                continue;
            }
            for (int i = 0; i < rowData.size(); i++) {
                Cell cell = row.createCell(i);
                writeCellValue(cell, rowData.get(i));
            }
        }
    }

    private static void writeCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }

        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }

        if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
            return;
        }

        if (value instanceof LocalDateTime localDateTime) {
            cell.setCellValue(localDateTime.format(DATE_TIME_FORMATTER));
            return;
        }

        if (value instanceof LocalDate localDate) {
            cell.setCellValue(localDate.format(DATE_FORMATTER));
            return;
        }

        if (value instanceof BigDecimal bigDecimal) {
            cell.setCellValue(bigDecimal.doubleValue());
            return;
        }

        cell.setCellValue(String.valueOf(value));
    }

    private static void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static CellStyle createTitleStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private static CellStyle createHeaderStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor((short) 22);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static String normalizeSheetName(String sheetName) {
        String value = StringUtils.hasText(sheetName) ? sheetName.trim() : "Sheet1";
        value = value.replaceAll("[\\\\/?*\\[\\]:]", " ");
        if (value.length() > 31) {
            value = value.substring(0, 31);
        }
        return StringUtils.hasText(value) ? value : "Sheet1";
    }

    private static String sanitizeFilename(String filename) {
        String value = StringUtils.hasText(filename) ? filename.trim() : "export.xlsx";
        return value.replace("\"", "");
    }
}
