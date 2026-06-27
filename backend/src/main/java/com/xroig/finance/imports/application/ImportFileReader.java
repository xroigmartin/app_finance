package com.xroig.finance.imports.application;

import com.xroig.finance.imports.domain.ImportRow;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Outbound port (anti-corruption layer): turns an uploaded CSV/XLSX file into a list
 * of {@link ImportRow}s keyed by normalized header. Format/IO problems surface as a
 * domain {@code ValidationException} (→ 400). Implemented by the parser adapter.
 */
public interface ImportFileReader {

    List<ImportRow> read(MultipartFile file);
}
