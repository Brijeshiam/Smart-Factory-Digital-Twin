package com.smartfactory.model;

public enum MachineState {
    NORMAL("Normal", "#10B981"),      // Emerald Green
    WARNING("Warning", "#F59E0B"),    // Amber / Yellow
    CRITICAL("Critical", "#EF4444"),  // Bright Red
    OFFLINE("Offline", "#6B7280");    // Slate Gray

    private final String displayName;
    private final String hexColor;

    MachineState(String displayName, String hexColor) {
        this.displayName = displayName;
        this.hexColor = hexColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getHexColor() {
        return hexColor;
    }

    public static MachineState fromString(String text) {
        if (text == null) return OFFLINE;
        for (MachineState state : MachineState.values()) {
            if (state.name().equalsIgnoreCase(text.trim())) {
                return state;
            }
        }
        return OFFLINE;
    }
}
