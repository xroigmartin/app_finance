package com.xroig.finance.imports.application.port;

import com.xroig.finance.imports.application.ImportResult;
import org.springframework.web.multipart.MultipartFile;

/** Inbound port: bulk-import movements from a bank export, using a default account for rows that omit one. */
public interface ImportTransactions {

    ImportResult importTransactions(MultipartFile file, Long defaultAccountId);
}
