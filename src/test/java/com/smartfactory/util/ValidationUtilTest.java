package com.smartfactory.util;

import com.smartfactory.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class ValidationUtilTest {

    @Test
    @DisplayName("Valid machine code and name should pass")
    void testValidMachine() {
        assertDoesNotThrow(() -> ValidationUtil.validateMachineCode("CNC-01"));
        assertDoesNotThrow(() -> ValidationUtil.validateMachineName("CNC Milling Center"));
    }

    @Test
    @DisplayName("Invalid machine code formats should throw ValidationException")
    void testInvalidMachineCode() {
        assertThrows(ValidationException.class, () -> ValidationUtil.validateMachineCode(""));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateMachineCode("A")); // too short
        assertThrows(ValidationException.class, () -> ValidationUtil.validateMachineCode("CNC#01!")); // special chars
    }

    @Test
    @DisplayName("Warning threshold >= Critical threshold should throw ValidationException")
    void testThresholdValidation() {
        // Warning 90 >= Critical 85 -> invalid
        assertThrows(ValidationException.class, () -> ValidationUtil.validateThresholds(90.0, 85.0, 3.0, 5.0));

        // Negative value -> invalid
        assertThrows(ValidationException.class, () -> ValidationUtil.validateThresholds(-10.0, 90.0, 3.0, 5.0));

        // Valid limits -> passes
        assertDoesNotThrow(() -> ValidationUtil.validateThresholds(80.0, 90.0, 3.5, 5.0));
    }

    @Test
    @DisplayName("Start date after end date should throw ValidationException")
    void testDateRangeValidation() {
        Instant now = Instant.now();
        Instant future = now.plusSeconds(3600);

        assertDoesNotThrow(() -> ValidationUtil.validateDateRange(now, future));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateDateRange(future, now));
    }
}
