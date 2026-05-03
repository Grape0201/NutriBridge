package com.grape0201.nutribridge

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import java.time.Instant
import java.time.ZonedDateTime

class HealthConnectManager(private val context: Context) {
    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    val permissions = setOf(
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.getWritePermission(NutritionRecord::class)
    )

    suspend fun hasAllPermissions(): Boolean {
        return healthConnectClient.permissionController.getGrantedPermissions().containsAll(permissions)
    }

    suspend fun writeNutritionRecord(
        calories: Double,
        protein: Double,
        fat: Double,
        carb: Double,
        menu: String,
        startTime: Instant
    ) {
        try {
            val record = NutritionRecord(
                startTime = startTime,
                startZoneOffset = ZonedDateTime.now().offset,
                endTime = startTime.plusSeconds(1),
                endZoneOffset = ZonedDateTime.now().offset,
                energy = Energy.kilocalories(calories),
                protein = Mass.grams(protein),
                totalFat = Mass.grams(fat),
                totalCarbohydrate = Mass.grams(carb),
                name = menu,
                metadata = Metadata.manualEntry()
            )
            healthConnectClient.insertRecords(listOf(record))
        } catch (e: Exception) {
            // Handle or log error
        }
    }

    suspend fun readNutritionRecords(startTime: Instant, endTime: Instant): List<NutritionRecord> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    NutritionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun deleteNutritionRecord(recordId: String) {
        try {
            healthConnectClient.deleteRecords(
                recordType = NutritionRecord::class,
                recordIdsList = listOf(recordId),
                clientRecordIdsList = emptyList()
            )
        } catch (e: Exception) {
            // Handle or log error
        }
    }
}
