package com.xroig.finance.shared.domain;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Shared-kernel text normalization: lowercases, strips accents (NFD + combining-mark
 * removal), drops the UTF-8 BOM and trims. Used wherever the domain compares free text
 * insensitive to case and accents (category-rule matching, and the import header/value
 * detection that still delegates here from the legacy parser).
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String normalize(String text) {
        String s = Normalizer.normalize(text.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return s.replace('﻿', ' ').trim();
    }
}
