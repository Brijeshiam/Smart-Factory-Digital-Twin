package com.smartfactory.service;

import com.smartfactory.model.Machine;

import java.util.List;

public class ProductionSimulationService {

    public record WhatIfResult(
            String machineCode,
            boolean isOffline,
            double adjustedRatePerHour,
            int simulationHours,
            double baselineOutput,
            double scenarioOutput,
            double outputDifference,
            double percentageImpact
    ) {}

    /**
     * Calculates what-if production scenario without mutating live simulation state.
     *
     * @param selectedMachine The machine selected for what-if adjustment
     * @param takeOffline Whether the selected machine is taken offline in the scenario
     * @param targetRateChangePercent e.g. -20% or +15% rate adjustment
     * @param simulationHours timeframe to project (e.g. 8h shift, 24h day, 168h week)
     * @param allMachines all factory machines
     * @return WhatIfResult containing baseline vs scenario comparison
     */
    public WhatIfResult calculateScenario(Machine selectedMachine, boolean takeOffline,
                                          double targetRateChangePercent, int simulationHours,
                                          List<Machine> allMachines) {
        if (selectedMachine == null || simulationHours <= 0) {
            return new WhatIfResult("N/A", false, 0, 0, 0, 0, 0, 0);
        }

        // Standard nominal units per hour per machine (illustrative standard)
        double defaultRatePerHour = 120.0; // 2 parts per minute
        double totalBaselinePerHour = 0.0;
        double totalScenarioPerHour = 0.0;

        for (Machine m : allMachines) {
            if (!m.isEnabled()) continue;

            double machineBaseRate = defaultRatePerHour;
            totalBaselinePerHour += machineBaseRate;

            if (m.getId().equals(selectedMachine.getId())) {
                if (takeOffline) {
                    // 0 output
                } else {
                    double adjustedRate = machineBaseRate * (1.0 + (targetRateChangePercent / 100.0));
                    totalScenarioPerHour += Math.max(0, adjustedRate);
                }
            } else {
                totalScenarioPerHour += machineBaseRate;
            }
        }

        double baselineOutput = totalBaselinePerHour * simulationHours;
        double scenarioOutput = totalScenarioPerHour * simulationHours;
        double diff = scenarioOutput - baselineOutput;
        double percentImpact = baselineOutput > 0 ? (diff / baselineOutput) * 100.0 : 0.0;

        double finalAdjustedRate = takeOffline ? 0.0 : (defaultRatePerHour * (1.0 + (targetRateChangePercent / 100.0)));

        return new WhatIfResult(
                selectedMachine.getMachineCode(),
                takeOffline,
                finalAdjustedRate,
                simulationHours,
                Math.round(baselineOutput),
                Math.round(scenarioOutput),
                Math.round(diff),
                Math.round(percentImpact * 10.0) / 10.0
        );
    }
}
