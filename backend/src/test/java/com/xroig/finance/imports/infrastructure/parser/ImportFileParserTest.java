package com.xroig.finance.imports.infrastructure.parser;

import com.xroig.finance.imports.domain.ImportRow;
import com.xroig.finance.shared.domain.ValidationException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Adapter tests for {@link ImportFileParser} (the {@code ImportFileReader} ACL): CSV
 * preamble/delimiter/BOM handling, Excel header detection and cell formatting, and the
 * unsupported/headerless cases surfacing as a {@link ValidationException} (→ 400).
 */
class ImportFileParserTest {

    private final ImportFileParser parser = new ImportFileParser();

    // --- CSV ---

    @Test
    void read_csv_skipsPreambleAndDetectsSemicolon() {
        String csv = "Extracto de la cuenta\n"
                + "fecha;importe;descripcion\n"
                + "15/06/2026;-12,50;Compra LIDL\n";

        List<ImportRow> rows = parser.read(csvFile("mov.csv", csv));

        assertThat(rows).hasSize(1);
        ImportRow row = rows.get(0);
        assertThat(row.value("fecha")).isEqualTo("15/06/2026");
        assertThat(row.value("importe")).isEqualTo("-12,50");
        assertThat(row.value("descripcion")).isEqualTo("Compra LIDL");
    }

    @Test
    void read_csv_detectsCommaDelimiterAndStripsBom() {
        String csv = "﻿fecha,importe,descripcion\n2026-06-15,10.00,Nomina\n";

        List<ImportRow> rows = parser.read(csvFile("mov.csv", csv));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).value("fecha")).isEqualTo("2026-06-15");
        assertThat(rows.get(0).value("importe")).isEqualTo("10.00");
    }

    @Test
    void read_rejectsUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "datos.pdf",
                "application/pdf", "x".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> parser.read(file)).isInstanceOf(ValidationException.class);
    }

    // --- Excel ---

    @Test
    void read_excel_readsHeaderRowDatesAndNumbers() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "mov.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", buildXlsx());

        List<ImportRow> rows = parser.read(file);

        assertThat(rows).hasSize(1);
        ImportRow row = rows.get(0);
        assertThat(row.value("fecha")).isEqualTo("2026-06-15");
        assertThat(row.value("importe")).isEqualTo("-12.5");
        assertThat(row.value("descripcion")).isEqualTo("Compra");
    }

    @Test
    void read_excel_withoutHeaderRowFails() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("solo texto");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            MockMultipartFile file = new MockMultipartFile("file", "mov.xlsx",
                    "application/octet-stream", out.toByteArray());

            assertThatThrownBy(() -> parser.read(file)).isInstanceOf(ValidationException.class);
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
