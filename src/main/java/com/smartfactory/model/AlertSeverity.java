package com.smartfactory.model;

public enum AlertSeverity {
    INFO(1, "#3B82F6"),       // Blue
    WARNING(2, "#F59E0B"),    // Amber
    CRITICAL(3, "#EF4444");   // Red

    private final int level;
    private final String hexColor;

    AlertSeverity(int level, String hexColor) {
        this.level = level;
        this.hexColor = hexColor;
    }

    public int getLevel() {
        return level;
    }

    public String getHexColor() {
        return hexColor;
    }

    public static AlertSeverity fromString(String text) {
        if (text == null) return INFO;
        for (AlertSeverity s : AlertSeverity.values()) {
            if (s.name().equalsIgnoreCase(text.trim())) {
                return s;
            }
        }
        return INFO;
    }
}
