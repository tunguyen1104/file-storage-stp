package com.filestorage.shared.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

public final class VietnameseUtils {

    private static final Pattern COMBINING_DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private VietnameseUtils() {
    }

    public static boolean containsAccent(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        if (COMBINING_DIACRITICS.matcher(normalized).find()) {
            return true;
        }
        String lower = input.toLowerCase();
        return lower.indexOf('đ') >= 0;
    }

    public static String removeAccent(String input) {
        if (input == null) {
            return null;
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String withoutMarks = COMBINING_DIACRITICS.matcher(normalized).replaceAll("");
        return withoutMarks.replace('đ', 'd').replace('Đ', 'D');
    }
}
