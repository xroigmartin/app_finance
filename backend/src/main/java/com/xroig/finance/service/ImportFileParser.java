package com.xroig.finance.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import com.xroig.finance.shared.domain.TextNormalizer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses CSV / XLSX import files into rows keyed by normalized header name
 * (lowercase, accents stripped). CSV may use "," or ";" as delimiter.
 */
@Component
public class ImportFileParser {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"));

    public List<Map<String, String>> parse(MultipartFile file) {
        String name = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        try {
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                return parseExcel(file);
            }
            if (name.endsWith(".csv") || name.endsWith(".txt")) {
                return parseCsv(file);
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se pudo leer el fichero: " + e.getMessage());
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Formato no soportado. Usa un fichero .csv o .xlsx");
    }

    public static String normalizeHeader(String header) {
        return TextNormalizer.normalize(header);
    }

    public static LocalDate parseDate(String value) {
        String v = value.trim();
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(v, format);
            } catch (Exception ignored) {
            }
        }
        throw new IllegalArgumentException("Fecha no válida: \"" + value + "\"");
    }

    /**
     * Accepts "1234.56", "1.234,56", "1234,56", "-12 €"…
     */
    public static java.math.BigDecimal parseAmount(String value) {
        String v = value.replaceAll("[€$\\s]", "");
        if (v.isEmpty()) {
            throw new IllegalArgumentException("Importe vacío");
        }
        int lastComma = v.lastIndexOf(',');
        int lastDot = v.lastIndexOf('.');
        if (lastComma >= 0 && lastDot >= 0) {
            if (lastComma > lastDot) {
                v = v.replace(".", "").replace(',', '.');
            } else {
                v = v.replace(",", "");
            }
        } else if (lastComma >= 0) {
            v = v.replace(',', '.');
        }
        try {
            return new java.math.BigDecimal(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Importe no válido: \"" + value + "\"");
        }
    }

    /**
     * True for a row that looks like the header of a movements/transfers
     * table. Bank exports often have preamble rows before it.
     */
    private static boolean isHeaderRow(List<String> normalizedCells) {
        return normalizedCells.stream().anyMatch(c -> c.equals("fecha") || c.equals("date"))
                && normalizedCells.stream().anyMatch(c -> c.equals("importe") || c.equals("amount"));
    }

    private List<Map<String, String>> parseCsv(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String content = new String(bytes, StandardCharsets.UTF_8);
        if (content.startsWith("﻿")) {
            content = content.substring(1);
        }
        content = skipPreamble(content);
        char delimiter = detectDelimiter(content);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();
        try (Reader reader = new java.io.StringReader(content);
             CSVParser parser = CSVParser.parse(reader, format)) {
            List<Map<String, String>> rows = new ArrayList<>();
            List<String> headers = parser.getHeaderNames().stream()
                    .map(ImportFileParser::normalizeHeader)
                    .toList();
            for (CSVRecord record : parser) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size() && i < record.size(); i++) {
                    row.put(headers.get(i), record.get(i).trim());
                }
                rows.add(row);
            }
            return rows;
        }
    }

    private String skipPreamble(String content) {
        List<String> lines = content.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            char delimiter = detectDelimiter(lines.get(i));
            List<String> cells = java.util.Arrays.stream(lines.get(i).split(String.valueOf(delimiter)))
                    .map(c -> normalizeHeader(c.replace("\"", "")))
                    .toList();
            if (isHeaderRow(cells)) {
                return String.join("\n", lines.subList(i, lines.size()));
            }
        }
        return content;
    }

    private char detectDelimiter(String content) {
        String firstLine = content.lines().findFirst().orElse("");
        return firstLine.chars().filter(c -> c == ';').count()
                > firstLine.chars().filter(c -> c == ',').count() ? ';' : ',';
    }

    private List<Map<String, String>> parseExcel(MultipartFile file) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            int headerRowIndex = -1;
            List<String> headers = new ArrayList<>();
            for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum() && headerRowIndex < 0; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                List<String> cells = new ArrayList<>();
                for (Cell cell : row) {
                    cells.add(normalizeHeader(formatter.formatCellValue(cell)));
                }
                if (isHeaderRow(cells)) {
                    headerRowIndex = r;
                    headers = cells;
                }
            }
            if (headerRowIndex < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No se encontró una fila de cabecera con columnas \"fecha\" e \"importe\"");
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (int r = headerRowIndex + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                boolean empty = true;
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.getCell(c);
                    String value = cellValue(cell, formatter);
                    if (!value.isEmpty()) {
                        empty = false;
                    }
                    values.put(headers.get(c), value);
                }
                if (!empty) {
                    rows.add(values);
                }
            }
            return rows;
        }
    }

    private String cellValue(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return java.math.BigDecimal.valueOf(cell.getNumericCellValue())
                    .stripTrailingZeros().toPlainString();
        }
        return formatter.formatCellValue(cell).trim();
    }
}
