package com.smartfactory.model;

public enum AlertStatus {
    ACTIVE("Active", "#EF4444"),
    ACKNOWLEDGED("Acknowledged", "#F59E0B"),
    RESOLVED("Resolved", "#10B981");

    private final String displayName;
    private final String hexColor;

    AlertStatus(String displayName, String hexColor) {
        this.displayName = displayName;
        this.hexColor = hexColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getHexColor() {
        return hexColor;
    }

    public static AlertStatus fromString(String text) {
        if (text == null) return ACTIVE;
        for (AlertStatus status : AlertStatus.values()) {
            if (status.name().equalsIgnoreCase(text.trim())) {
                return status;
            }
        }
        return ACTIVE;
    }
}
