import Foundation
import HealthKit

/// What the watch or phone recorded for one day, read from Apple Health.
struct DayActivity: Equatable {
    var activeKcal: Double = 0
    var steps: Int = 0
    var sleepHours: Double?
    var exerciseMinutes: Int = 0
}

/// Read-only access to Apple Health. Apple Watch writes there directly; Garmin Connect, Mi Fitness, Zepp,
/// Huawei Health, Samsung Health and most other watch apps can sync into it from their own settings.
final class HealthService {
    static let shared = HealthService()
    private let store = HKHealthStore()

    var available: Bool { HKHealthStore.isHealthDataAvailable() }

    private var readTypes: Set<HKObjectType> {
        let quantities: [HKQuantityTypeIdentifier] = [.activeEnergyBurned, .stepCount, .appleExerciseTime, .bodyMass, .height]
        var types = Set<HKObjectType>(quantities.compactMap { HKObjectType.quantityType(forIdentifier: $0) })
        if let sleep = HKObjectType.categoryType(forIdentifier: .sleepAnalysis) { types.insert(sleep) }
        return types
    }

    /// Shows Apple's permission sheet. Apple never tells an app which read permissions were refused,
    /// so success only means the sheet was answered.
    func requestAccess() async -> Bool {
        guard available else { return false }
        do {
            try await store.requestAuthorization(toShare: [], read: readTypes)
            return true
        } catch {
            return false
        }
    }

    func activity(on day: Date) async -> DayActivity {
        let start = Calendar.current.startOfDay(for: day)
        guard let end = Calendar.current.date(byAdding: .day, value: 1, to: start) else { return DayActivity() }
        async let active = sum(.activeEnergyBurned, .kilocalorie(), start, end)
        async let steps = sum(.stepCount, .count(), start, end)
        async let exercise = sum(.appleExerciseTime, .minute(), start, end)
        // Last night's sleep: from 18:00 the evening before until noon.
        async let sleep = sleepHours(from: start.addingTimeInterval(-6 * 3600), to: start.addingTimeInterval(12 * 3600))
        return DayActivity(activeKcal: await active, steps: Int(await steps), sleepHours: await sleep, exerciseMinutes: Int(await exercise))
    }

    /// The last `days` days, keyed by yyyy-MM-dd.
    func history(days: Int) async -> [String: DayActivity] {
        await withTaskGroup(of: (String, DayActivity).self) { group in
            for back in 0..<days {
                let day = Calendar.current.date(byAdding: .day, value: -back, to: Date()) ?? Date()
                group.addTask { (dayKey(day), await self.activity(on: day)) }
            }
            var out: [String: DayActivity] = [:]
            for await (key, value) in group { out[key] = value }
            return out
        }
    }

    /// The newest weight (kg) and height (cm) on record, if any.
    func latestBody() async -> (weightKg: Double?, heightCm: Double?) {
        async let weight = latest(.bodyMass, .gramUnit(with: .kilo))
        async let height = latest(.height, .meterUnit(with: .centi))
        return (await weight, await height)
    }

    private func sum(_ id: HKQuantityTypeIdentifier, _ unit: HKUnit, _ start: Date, _ end: Date) async -> Double {
        guard let type = HKQuantityType.quantityType(forIdentifier: id) else { return 0 }
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end, options: .strictStartDate)
        return await withCheckedContinuation { done in
            // Statistics merge overlapping samples, so steps counted by both the phone and the watch are not doubled.
            let query = HKStatisticsQuery(quantityType: type, quantitySamplePredicate: predicate, options: .cumulativeSum) { _, result, _ in
                done.resume(returning: result?.sumQuantity()?.doubleValue(for: unit) ?? 0)
            }
            store.execute(query)
        }
    }

    private func latest(_ id: HKQuantityTypeIdentifier, _ unit: HKUnit) async -> Double? {
        guard let type = HKQuantityType.quantityType(forIdentifier: id) else { return nil }
        let newest = NSSortDescriptor(key: HKSampleSortIdentifierEndDate, ascending: false)
        return await withCheckedContinuation { done in
            let query = HKSampleQuery(sampleType: type, predicate: nil, limit: 1, sortDescriptors: [newest]) { _, samples, _ in
                done.resume(returning: (samples?.first as? HKQuantitySample)?.quantity.doubleValue(for: unit))
            }
            store.execute(query)
        }
    }

    private func sleepHours(from start: Date, to end: Date) async -> Double? {
        guard let type = HKObjectType.categoryType(forIdentifier: .sleepAnalysis) else { return nil }
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end, options: [])
        return await withCheckedContinuation { done in
            let query = HKSampleQuery(sampleType: type, predicate: predicate, limit: HKObjectQueryNoLimit, sortDescriptors: nil) { _, samples, _ in
                let asleep = (samples as? [HKCategorySample] ?? []).filter { $0.value != HKCategoryValueSleepAnalysis.inBed.rawValue
                    && $0.value != HKCategoryValueSleepAnalysis.awake.rawValue }
                // Several sources (watch and phone) can record the same night: merge overlapping spans before adding them up.
                var total: TimeInterval = 0
                var cursor = Date.distantPast
                for sample in asleep.sorted(by: { $0.startDate < $1.startDate }) {
                    let from = max(sample.startDate, cursor)
                    if sample.endDate > from { total += sample.endDate.timeIntervalSince(from) }
                    cursor = max(cursor, sample.endDate)
                }
                done.resume(returning: total > 0 ? total / 3600 : nil)
            }
            store.execute(query)
        }
    }
}
