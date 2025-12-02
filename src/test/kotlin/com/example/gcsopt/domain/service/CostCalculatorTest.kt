package com.example.gcsopt.domain.service

import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@QuarkusTest
class CostCalculatorTest {

    @Inject
    lateinit var calculator: CostCalculator

    @Test
    fun `calculateCurrentCost should calculate STANDARD storage cost correctly`() {
        val sizeBytes = 1_073_741_824L // 1 GB
        val cost = calculator.calculateCurrentCost(sizeBytes, "STANDARD")
        assertEquals(0.023, cost, 0.001)
    }

    @Test
    fun `calculateCurrentCost should calculate COLDLINE storage cost correctly`() {
        val sizeBytes = 1_073_741_824L // 1 GB
        val cost = calculator.calculateCurrentCost(sizeBytes, "COLDLINE")
        assertEquals(0.006, cost, 0.001)
    }

    @Test
    fun `calculateOptimizedCost should calculate savings when moving to COLDLINE`() {
        val totalSize = 10_737_418_240L // 10 GB
        val unaccessedSize = 5_368_709_120L // 5 GB
        
        val optimizedCost = calculator.calculateOptimizedCost(
            totalSizeBytes = totalSize,
            unaccessedSizeBytes = unaccessedSize,
            currentStorageClass = "STANDARD",
            targetStorageClass = "COLDLINE",
            compressionRatio = null
        )
        
        // 5GB STANDARD (0.023) + 5GB COLDLINE (0.006) = 0.115 + 0.03 = 0.145
        assertEquals(0.145, optimizedCost, 0.001)
    }

    @Test
    fun `calculateOptimizedCost should calculate savings with DELETE target`() {
        val totalSize = 10_737_418_240L // 10 GB
        val unaccessedSize = 5_368_709_120L // 5 GB
        
        val optimizedCost = calculator.calculateOptimizedCost(
            totalSizeBytes = totalSize,
            unaccessedSizeBytes = unaccessedSize,
            currentStorageClass = "STANDARD",
            targetStorageClass = "DELETE",
            compressionRatio = null
        )
        
        // Only 5GB STANDARD remains = 0.115
        assertEquals(0.115, optimizedCost, 0.001)
    }

    @Test
    fun `calculateOptimizedCost should apply compression ratio when specified`() {
        val totalSize = 10_737_418_240L // 10 GB
        val unaccessedSize = 5_368_709_120L // 5 GB
        
        val optimizedCost = calculator.calculateOptimizedCost(
            totalSizeBytes = totalSize,
            unaccessedSizeBytes = unaccessedSize,
            currentStorageClass = "STANDARD",
            targetStorageClass = "COLDLINE",
            compressionRatio = 0.5
        )
        
        // 5GB STANDARD (0.023) + 2.5GB COLDLINE (0.006) = 0.115 + 0.015 = 0.13
        assertEquals(0.13, optimizedCost, 0.001)
    }
}
