package com.xroig.finance.imports.infrastructure.parser;

import com.xroig.finance.imports.application.ImportFileReader;
import com.xroig.finance.imports.domain.ImportRow;
import com.xroig.finance.shared.domain.TextNormalizer;
import com.xroig.finance.shared.domain.ValidationException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Inbound anti-corruption adapter implementing {@link ImportFileReader}: parses
 * CSV/XLSX import files into {@link ImportRow}s keyed by normalized header name
 * (lowercase, accents stripped, via {@link TextNormalizer}). CSV may use "," or ";"
 * as delimiter; bank exports often carry preamble rows before the header. Format/IO
 * problems surface as a {@link ValidationException} (→ 400).
 */
@Component
public class ImportFileParser implements ImportFileReader {

    @Override
    public List<ImportRow> read(MultipartFile file) {
        String name = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        try {
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                return wrap(parseExcel(file));
            }
            if (name.endsWith(".csv") || name.endsWith(".txt")) {
                return wrap(parseCsv(file));
            }
        } catch (IOException e) {
            throw new ValidationException("No se pudo leer el fichero: " + e.getMessage());
        }
        throw new ValidationException("Formato no soportado. Usa un fichero .csv o .xlsx");
    }

    private static List<ImportRow> wrap(List<Map<String, String>> rows) {
        return rows.stream().map(ImportRow::new).toList();
    }

    private static String normalize(String header) {
        return TextNormalizer.normalize(header);
    }

    /**
     * True for a row that looks like the header of a movements/transfers table. Bank
     * exports often have preamble rows before it.
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
        try (Reader reader = new StringReader(content);
             CSVParser parser = CSVParser.parse(reader, format)) {
            List<Map<String, String>> rows = new ArrayList<>();
            List<String> headers = parser.getHeaderNames().stream()
                    .map(ImportFileParser::normalize)
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
            List<String> cells = Arrays.stream(lines.get(i).split(String.valueOf(delimiter)))
                    .map(c -> normalize(c.replace("\"", "")))
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
                    cells.add(normalize(formatter.formatCellValue(cell)));
                }
                if (isHeaderRow(cells)) {
                    headerRowIndex = r;
                    headers = cells;
                }
            }
            if (headerRowIndex < 0) {
                throw new ValidationException(
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
            return BigDecimal.valueOf(cell.getNumericCellValue())
                    .stripTrailingZeros().toPlainString();
        }
        return formatter.formatCellValue(cell).trim();
    }
}
