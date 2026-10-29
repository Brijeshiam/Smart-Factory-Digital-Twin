package com.smartfactory.util;

import com.smartfactory.exception.ValidationException;

import java.time.Instant;

public class ValidationUtil {

    public static void validateMachineCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new ValidationException("Machine code cannot be empty.");
        }
        String trimmed = code.trim();
        if (trimmed.length() < 2 || trimmed.length() > 50) {
            throw new ValidationException("Machine code must be between 2 and 50 characters.");
        }
        if (!trimmed.matches("^[a-zA-Z0-9_-]+$")) {
            throw new ValidationException("Machine code can only contain letters, numbers, hyphens, and underscores.");
        }
    }

    public static void validateMachineName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Machine name cannot be empty.");
        }
        if (name.trim().length() > 100) {
            throw new ValidationException("Machine name cannot exceed 100 characters.");
        }
    }

    public static void validateThresholds(double tempWarn, double tempCrit, double vibWarn, double vibCrit) {
        if (tempWarn <= 0 || tempCrit <= 0) {
            throw new ValidationException("Temperature thresholds must be positive values.");
        }
        if (tempWarn >= tempCrit) {
            throw new ValidationException(String.format(
                    "Temperature warning limit (%.1f°C) must be strictly less than critical limit (%.1f°C).",
                    tempWarn, tempCrit
            ));
        }

        if (vibWarn <= 0 || vibCrit <= 0) {
            throw new ValidationException("Vibration thresholds must be positive values.");
        }
        if (vibWarn >= vibCrit) {
            throw new ValidationException(String.format(
                    "Vibration warning limit (%.2f mm/s) must be strictly less than critical limit (%.2f mm/s).",
                    vibWarn, vibCrit
            ));
        }
    }

    public static void validateDateRange(Instant start, Instant end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new ValidationException("Start date/time cannot be after end date/time.");
        }
    }
}
