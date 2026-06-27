package com.xroig.finance.imports.application.port;

import com.xroig.finance.imports.application.ImportResult;
import org.springframework.web.multipart.MultipartFile;

/** Inbound port: bulk-import transfers (rows with origin and destination accounts) from a file. */
public interface ImportTransfers {

    ImportResult importTransfers(MultipartFile file);
}
