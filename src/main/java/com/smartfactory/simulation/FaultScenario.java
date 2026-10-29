package com.smartfactory.simulation;

public enum FaultScenario {
    NONE("Normal Operation"),
    HIGH_TEMPERATURE("Inject High Temperature (>90°C Critical)"),
    MODERATE_TEMPERATURE("Inject Warning Temperature (82°C-88°C)"),
    HIGH_VIBRATION("Inject Excessive Vibration (>5.2 mm/s Critical)"),
    MODERATE_VIBRATION("Inject Warning Vibration (3.8-4.8 mm/s)"),
    MOTOR_STALL("Inject Motor Stall / Rapid RPM Drop");

    private final String description;

    FaultScenario(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
