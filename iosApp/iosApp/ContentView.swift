import SwiftUI
import shared

struct ContentView: View {
    @State private var currentMeasurement: PowerMeasurement? = nil
    @State private var energyReport: EnergyConsumptionReport? = nil
    @State private var powerData: PowerConsumptionData? = nil
    @State private var averagePower: Int? = nil
    @State private var averageCurrent: Int? = nil
    @State private var averageVoltage: Int? = nil
    @State private var appPowerConsumption: Float = 0.0
    @State private var averageBatteryConsumption: Double? = nil
    @State private var intervalData: [Any] = []

    // Timer for periodic updates
    let timer = Timer.publish(every: 2, on: .main, in: .common).autoconnect()

    var body: some View {
        NavigationView {
            List {
                // Current Power Measurement
                Section(header: Text("Current Measurement")) {
                    if let measurement = currentMeasurement {
                        InfoRow(label: "Current", value: measurement.currentInMicroamps !=ap nil ? "\(measurement.currentInMicroamps!) µA" : "Not available")
                        InfoRow(label: "Voltage", value: measurement.voltageInMillivolts != nil ? "\(measurement.voltageInMillivolts!) mV" : "Not available")
                        InfoRow(label: "Power", value: measurement.instantPowerInMicrowatts != nil ? "\(measurement.instantPowerInMicrowatts!) µW" : "Not available")
                        InfoRow(label: "Timestamp", value: "\(formatDate(Date(timeIntervalSince1970: Double(measurement.timestamp) / 1000.0)))")
                    } else {
                        Text("No measurement available")
                            .italic()
                            .foregroundColor(.gray)
                    }
                }

                // Energy Consumption Report
                Section(header: Text("Energy Report")) {
                    if let report = energyReport {
                        InfoRow(label: "Duration", value: "\(report.durationMillis / 1000) seconds")
                        InfoRow(label: "Avg Current", value: report.averageCurrentMicroamps != nil ? "\(report.averageCurrentMicroamps!) µA" : "N/A")
                        InfoRow(label: "Avg Voltage", value: report.averageVoltageMv != nil ? "\(report.averageVoltageMv!) mV" : "N/A")
                        InfoRow(label: "Avg Power", value: report.averagePowerMicroWatts != nil ? "\(report.averagePowerMicroWatts!) µW" : "N/A")
                        InfoRow(label: "Total Energy", value: report.totalEnergyMicroWattHours != nil ? "\(String(format: "%.3f", report.totalEnergyMicroWattHours!)) µWh" : "N/A")
                    } else {
                        Text("No energy report available")
                            .italic()
                            .foregroundColor(.gray)
                    }
                }

                // Power Consumption Data
                Section(header: Text("Power Consumption")) {
                    if let data = powerData {
                        InfoRow(label: "Energy Used", value: "\(String(format: "%.3f", data.energyUsedMicroWattHours)) µWh")
                        InfoRow(label: "Avg Power Draw", value: data.averagePowerDrawMicroWatts != nil ? "\(data.averagePowerDrawMicroWatts!) µW" : "N/A")
                        InfoRow(label: "Duration", value: "\(data.durationMillis / 1000) seconds")
                        InfoRow(label: "Rate", value: data.getConsumptionRateWattsPerHour() != nil ? "\(String(format: "%.5f", data.getConsumptionRateWattsPerHour()!)) W/h" : "N/A")
                    } else {
                        Text("No power consumption data available")
                            .italic()
                            .foregroundColor(.gray)
                    }
                }

                // Average Values
                Section(header: Text("Averages")) {
                    InfoRow(label: "Avg Current", value: averageCurrent != nil ? "\(averageCurrent!) µA" : "Not available")
                    InfoRow(label: "Avg Voltage", value: averageVoltage != nil ? "\(averageVoltage!) mV" : "Not available")
                    InfoRow(label: "Avg Power", value: averagePower != nil ? "\(averagePower!) µW" : "Not available")
                    InfoRow(label: "App Power", value: "\(String(format: "%.3f", appPowerConsumption))")
                    InfoRow(label: "Avg Battery", value: averageBatteryConsumption != nil ? "\(String(format: "%.3f", averageBatteryConsumption!)) %" : "Not available")
                }

                // Manual Refresh Button
                Section {
                    Button("Refresh Data") {
                        updateAllData()
                    }
                }
            }
            .navigationTitle("Power Monitor")
            .onAppear {
                // Initialize SDK when view appears
                PowerMonitorSDK().initialize(context: nil)
                updateAllData()
            }
            .onReceive(timer) { _ in
                // Update data periodically
                updateAllData()
            }
        }
    }

    // Function to update all power-related data
    private func updateAllData() {
        let powerManager = PowerMonitorSDK().getInstance()

        currentMeasurement = powerManager.getCurrentPowerMeasurement()
        energyReport = powerManager.getEnergyConsumptionReport()
        powerData = powerManager.getPowerConsumptionData()

        averageCurrent = powerManager.getAverageCurrentDraw()
        averageVoltage = powerManager.getAverageVoltageDraw()
        averagePower = powerManager.getAveragePower()

        appPowerConsumption = powerManager.getAppPowerConsumption()
        averageBatteryConsumption = powerManager.getAverageBatteryConsumption(intervals: nil)

        let intervalDataResult = powerManager.getIntervalConsumptionData(maxIntervals: 5)
        intervalData = intervalDataResult as [Any]
    }

    // Helper function to format date
    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.timeStyle = .medium
        return formatter.string(from: date)
    }
}

// Helper view for rows
struct InfoRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label)
            Spacer()
            Text(value)
                .foregroundColor(.secondary)
        }
    }
}

// SwiftUI preview
struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}