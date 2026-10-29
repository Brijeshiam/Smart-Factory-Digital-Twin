package com.smartfactory.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DateTimeUtil {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private static final DateTimeFormatter FULL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    public static String formatTimeOnly(Instant instant) {
        if (instant == null) return "--:--:--";
        return TIME_FORMATTER.format(instant);
    }

    public static String formatFull(Instant instant) {
        if (instant == null) return "N/A";
        return FULL_FORMATTER.format(instant);
    }

    public static String formatIso(Instant instant) {
        if (instant == null) return "";
        return ISO_FORMATTER.format(instant);
    }
}
