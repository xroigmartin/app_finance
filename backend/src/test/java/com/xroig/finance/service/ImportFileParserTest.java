package com.xroig.finance.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportFileParserTest {

    private final ImportFileParser parser = new ImportFileParser();

    // --- parseAmount ---

    @ParameterizedTest
    @CsvSource({
            "1234.56, 1234.56",
            "'1.234,56', 1234.56",
            "'1234,56', 1234.56",
            "'1,234.56', 1234.56",
            "'-12 €', -12",
            "'  10 ', 10"
    })
    void parseAmount_acceptsCommonFormats(String input, String expected) {
        assertThat(ImportFileParser.parseAmount(input)).isEqualByComparingTo(new BigDecimal(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "abc", "1.2.3,4,5x"})
    void parseAmount_rejectsInvalid(String input) {
        assertThatThrownBy(() -> ImportFileParser.parseAmount(input))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- parseDate ---

    @ParameterizedTest
    @CsvSource({
            "2026-06-15, 2026-06-15",
            "15/06/2026, 2026-06-15",
            "15-06-2026, 2026-06-15",
            "15/06/26, 2026-06-15"
    })
    void parseDate_acceptsKnownFormats(String input, String expected) {
        assertThat(ImportFileParser.parseDate(input)).isEqualTo(LocalDate.parse(expected));
    }

    @Test
    void parseDate_rejectsInvalid() {
        assertThatThrownBy(() -> ImportFileParser.parseDate("no-soy-fecha"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- normalizeHeader ---

    @ParameterizedTest
    @CsvSource({
            "'  Categoría ', categoria",
            "IMPORTE, importe",
            "'Más datos', mas datos",
            "Descripción, descripcion"
    })
    void normalizeHeader_lowercasesStripsAccentsAndTrims(String input, String expected) {
        assertThat(ImportFileParser.normalizeHeader(input)).isEqualTo(expected);
    }

    // --- parse (CSV) ---

    @Test
    void parse_csv_skipsPreambleAndDetectsSemicolon() {
        String csv = "Extracto de la cuenta\n"
                + "fecha;importe;descripcion\n"
                + "15/06/2026;-12,50;Compra LIDL\n";
        MockMultipartFile file = csvFile("mov.csv", csv);

        List<Map<String, String>> rows = parser.parse(file);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .containsEntry("fecha", "15/06/2026")
                .containsEntry("importe", "-12,50")
                .containsEntry("descripcion", "Compra LIDL");
    }

    @Test
    void parse_csv_detectsCommaDelimiterAndStripsBom() {
        String csv = "﻿fecha,importe,descripcion\n2026-06-15,10.00,Nomina\n";
        MockMultipartFile file = csvFile("mov.csv", csv);

        List<Map<String, String>> rows = parser.parse(file);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("fecha", "2026-06-15").containsEntry("importe", "10.00");
    }

    @Test
    void parse_rejectsUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "datos.pdf",
                "application/pdf", "x".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> parser.parse(file)).isInstanceOf(ResponseStatusException.class);
    }

    // --- parse (Excel) ---

    @Test
    void parse_excel_readsHeaderRowDatesAndNumbers() throws Exception {
        byte[] bytes = buildXlsx();
        MockMultipartFile file = new MockMultipartFile("file", "mov.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        List<Map<String, String>> rows = parser.parse(file);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .containsEntry("fecha", "2026-06-15")
                .containsEntry("importe", "-12.5")
                .containsEntry("descripcion", "Compra");
    }

    @Test
    void parse_excel_withoutHeaderRowFails() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("solo texto");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            MockMultipartFile file = new MockMultipartFile("file", "mov.xlsx",
                    "application/octet-stream", out.toByteArray());

            assertThatThrownBy(() -> parser.parse(file)).isInstanceOf(ResponseStatusException.class);
        }
    }

    private static MockMultipartFile csvFile(String name, String content) {
        return new MockMultipartFile("file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] buildXlsx() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("Mi banco — extracto");

            Row header = sheet.createRow(1);
            header.createCell(0).setCellValue("Fecha");
            header.createCell(1).setCellValue("Importe");
            header.createCell(2).setCellValue("Descripcion");

            Row data = sheet.createRow(2);
            CreationHelper helper = wb.getCreationHelper();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));
            Cell dateCell = data.createCell(0);
            dateCell.setCellValue(LocalDateTime.of(2026, 6, 15, 0, 0));
            dateCell.setCellStyle(dateStyle);
            data.createCell(1).setCellValue(-12.5);
            data.createCell(2).setCellValue("Compra");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }
}
