package com.smartfactory.simulation;

import com.smartfactory.model.MachineState;
import com.smartfactory.model.SensorReading;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MachineSensorStateTest {

    @Test
    @DisplayName("Sensor values should remain within physical bounds during simulation")
    void testSensorBounds() {
        MachineSensorState state = new MachineSensorState(1L, "CNC-01", 3000.0, 50L);

        for (int i = 0; i < 50; i++) {
            SensorReading reading = state.step();
            assertTrue(reading.getTemperatureC() >= 15.0 && reading.getTemperatureC() <= 125.0,
                    "Temperature must remain in physical range");
            assertTrue(reading.getVibrationMmS() >= 0.0 && reading.getVibrationMmS() <= 15.0,
                    "Vibration must remain in physical range");
            assertTrue(reading.getMotorRpm() >= 0.0 && reading.getMotorRpm() <= 8000.0,
                    "RPM must remain in physical range");
            assertTrue(reading.getProductionCount() >= 50L,
                    "Production count must not decrease");
        }
    }

    @Test
    @DisplayName("Injecting HIGH_TEMPERATURE fault should gradually elevate temperature")
    void testFaultInjectionHighTemp() {
        MachineSensorState state = new MachineSensorState(1L, "CNC-01", 3000.0, 100L);
        double initialTemp = state.getCurrentTemp();

        state.setFaultScenario(FaultScenario.HIGH_TEMPERATURE);

        for (int i = 0; i < 20; i++) {
            state.step();
        }

        double finalTemp = state.getCurrentTemp();
        assertTrue(finalTemp > initialTemp + 5.0, "Temperature should climb significantly under high temp fault");
        assertTrue(finalTemp >= 88.0, "Temperature should reach or approach critical territory");
    }

    @Test
    @DisplayName("Disabling machine should yield OFFLINE status and zero RPM")
    void testDisabledMachine() {
        MachineSensorState state = new MachineSensorState(1L, "CNC-01", 3000.0, 100L);
        state.setEnabled(false);

        SensorReading reading = state.step();
        assertEquals(MachineState.OFFLINE, reading.getMachineStatus());
        assertEquals(0.0, reading.getMotorRpm());
    }
}
