package com.smartfactory.service;

import com.smartfactory.model.Machine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ProductionSimulationServiceTest {

    @Test
    @DisplayName("Taking a machine offline should calculate negative production impact")
    void testTakingMachineOffline() {
        ProductionSimulationService service = new ProductionSimulationService();

        Machine m1 = new Machine(1L, "CNC-01", "CNC", "Bay 1", true, null, null);
        Machine m2 = new Machine(2L, "MOT-02", "Motor", "Bay 2", true, null, null);
        List<Machine> machines = List.of(m1, m2);

        // Baseline: 2 machines * 120 units/hr * 8 hours = 1920 units
        // Scenario (m1 offline): 1 machine * 120 units/hr * 8 hours = 960 units (-50%)
        ProductionSimulationService.WhatIfResult result =
                service.calculateScenario(m1, true, 0.0, 8, machines);

        assertEquals(1920.0, result.baselineOutput());
        assertEquals(960.0, result.scenarioOutput());
        assertEquals(-960.0, result.outputDifference());
        assertEquals(-50.0, result.percentageImpact());
        assertTrue(result.isOffline());
    }

    @Test
    @DisplayName("Increasing production rate by 25% on one of two machines should calculate +12.5% impact")
    void testIncreasingRate() {
        ProductionSimulationService service = new ProductionSimulationService();

        Machine m1 = new Machine(1L, "CNC-01", "CNC", "Bay 1", true, null, null);
        Machine m2 = new Machine(2L, "MOT-02", "Motor", "Bay 2", true, null, null);
        List<Machine> machines = List.of(m1, m2);

        ProductionSimulationService.WhatIfResult result =
                service.calculateScenario(m1, false, 25.0, 8, machines);

        assertEquals(1920.0, result.baselineOutput());
        // m1 = 150/hr, m2 = 120/hr -> 270 * 8 = 2160
        assertEquals(2160.0, result.scenarioOutput());
        assertEquals(240.0, result.outputDifference());
        assertEquals(12.5, result.percentageImpact());
    }
}
