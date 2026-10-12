package com.calocare.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.calocare.app.data.WearableDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Reads what the user's watch or band recorded. Wear OS, Samsung Health, Fitbit, Garmin (via sync),
 * Mi Fitness / Zepp and others all write into Health Connect, so one integration covers them.
 */
class HealthConnect(private val context: Context) {
    data class Body(val weightKg: Float?, val heightCm: Float?)

    val permissions = setOf(
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    /** One of HealthConnectClient.SDK_AVAILABLE / SDK_UNAVAILABLE / SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED. */
    fun status(): Int = HealthConnectClient.getSdkStatus(context)

    val available get() = status() == HealthConnectClient.SDK_AVAILABLE

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun granted(): Set<String> =
        if (available) runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet()) else emptySet()

    // Each metric is read on its own: the user may grant only some permissions, and one refusal
    // must not throw away the data we are allowed to read.
    private suspend fun <T : Any> metric(metric: AggregateMetric<T>, range: TimeRangeFilter): T? =
        runCatching { client.aggregate(AggregateRequest(setOf(metric), range))[metric] }.getOrNull()

    suspend fun readDays(days: Int): Map<String, WearableDay> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val result = mutableMapOf<String, WearableDay>()
        for (i in 0 until days) {
            val date = today.minusDays(i.toLong())
            val start = date.atStartOfDay(zone).toInstant()
            val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant(), Instant.now())
            val range = TimeRangeFilter.between(start, end)
            val active = metric(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, range)?.inKilocalories ?: 0.0
            val total = metric(TotalCaloriesBurnedRecord.ENERGY_TOTAL, range)?.inKilocalories ?: 0.0
            val steps = metric(StepsRecord.COUNT_TOTAL, range) ?: 0L
            // The night that ended on this date: from the previous evening to noon.
            val night = TimeRangeFilter.between(start.minusSeconds(6 * 3600), minOf(start.plusSeconds(12 * 3600), Instant.now()))
            val sleep = metric(SleepSessionRecord.SLEEP_DURATION_TOTAL, night)?.toMinutes() ?: 0L
            if (active > 0 || total > 0 || steps > 0 || sleep > 0) {
                result[date.toString()] = WearableDay(active.roundToInt(), total.roundToInt(), steps.toInt(), sleep.toInt())
            }
        }
        return result
    }

    suspend fun readBody(): Body {
        val range = TimeRangeFilter.before(Instant.now())
        val weight = runCatching {
            client.readRecords(ReadRecordsRequest(WeightRecord::class, range, ascendingOrder = false, pageSize = 1))
                .records.firstOrNull()?.weight?.inKilograms?.toFloat()
        }.getOrNull()
        val height = runCatching {
            client.readRecords(ReadRecordsRequest(HeightRecord::class, range, ascendingOrder = false, pageSize = 1))
                .records.firstOrNull()?.height?.inMeters?.times(100)?.toFloat()
        }.getOrNull()
        return Body(weight, height)
    }
}
