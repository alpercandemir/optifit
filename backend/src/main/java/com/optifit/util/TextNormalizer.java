package com.optifit.util;

import java.text.Normalizer;
import java.util.Locale;

public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s.toLowerCase(Locale.forLanguageTag("tr")).replace('ı', 'i'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}
