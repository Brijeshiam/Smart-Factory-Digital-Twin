package com.smartfactory.util;

import com.smartfactory.model.SensorReading;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CsvExporter {

    public static void exportSensorReadings(List<SensorReading> readings, String machineCode, File destinationFile) throws IOException {
        if (destinationFile == null) {
            throw new IllegalArgumentException("Destination file cannot be null");
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(destinationFile, StandardCharsets.UTF_8))) {
            // Write CSV Header
            writer.write("Machine Code,Recorded At,Temperature (C),Vibration (mm/s),Motor RPM,Production Count,Status,Alert Message");
            writer.newLine();

            if (readings != null) {
                for (SensorReading r : readings) {
                    writer.write(escapeCsv(machineCode != null ? machineCode : String.valueOf(r.getMachineId())));
                    writer.write(",");
                    writer.write(escapeCsv(DateTimeUtil.formatFull(r.getRecordedAt())));
                    writer.write(",");
                    writer.write(String.format("%.2f", r.getTemperatureC()));
                    writer.write(",");
                    writer.write(String.format("%.2f", r.getVibrationMmS()));
                    writer.write(",");
                    writer.write(String.format("%.0f", r.getMotorRpm()));
                    writer.write(",");
                    writer.write(String.valueOf(r.getProductionCount()));
                    writer.write(",");
                    writer.write(escapeCsv(r.getMachineStatus().name()));
                    writer.write(",");
                    writer.write(escapeCsv(r.getAlertMessage() != null ? r.getAlertMessage() : ""));
                    writer.newLine();
                }
            }
            writer.flush();
        }
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
