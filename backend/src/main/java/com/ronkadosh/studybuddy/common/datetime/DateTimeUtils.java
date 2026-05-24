package com.ronkadosh.studybuddy.common.datetime;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class DateTimeUtils {
    private DateTimeUtils() {}

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public static String format(Instant instant) {
        return ISO_FORMATTER.format(instant.atZone(ZoneId.of("UTC")));
    }

    public static Instant parseInstant(String value) {
        return ZonedDateTime.parse(value, ISO_FORMATTER).toInstant();
    }
}
