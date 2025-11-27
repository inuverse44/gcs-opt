package com.example.gcsopt.domain.service

import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class CostCalculator {
    
    companion object {
        // 単位: USD per GB per Month
        // https://cloud.google.com/storage/pricing
        private val RATES = mapOf(
            // Region (e.g. asia-northeast1)
            "STANDARD" to 0.023,
            "NEARLINE" to 0.016,
            "COLDLINE" to 0.006,
            "ARCHIVE" to 0.0025,
            
            // Multi-Region (e.g. asia)
            "MULTI_REGION" to 0.026
        )
        
        private const val BYTES_PER_GB = 1_073_741_824.0 // 1024^3
    }
    
    fun calculateCurrentCost(sizeBytes: Long, storageClass: String): Double {
        val sizeGb = sizeBytes / BYTES_PER_GB
        val rate = RATES[storageClass] ?: RATES["STANDARD"]!!
        return sizeGb * rate
    }
    
    fun calculateOptimizedCost(
        totalSizeBytes: Long,
        unaccessedSizeBytes: Long,
        currentStorageClass: String,
        targetStorageClass: String = "COLDLINE",
        compressionRatio: Double? = null
    ): Double {
        val accessedSize = totalSizeBytes - unaccessedSizeBytes
        val accessedSizeGb = accessedSize / BYTES_PER_GB
        
        var unaccessedSize = unaccessedSizeBytes
        if (compressionRatio != null) {
            unaccessedSize = (unaccessedSize * compressionRatio).toLong()
        }
        val unaccessedSizeGb = unaccessedSize / BYTES_PER_GB
        
        val currentRate = RATES[currentStorageClass] ?: RATES["STANDARD"]!!
        
        val targetRate = if (targetStorageClass.uppercase() == "DELETE") {
            0.0
        } else {
            RATES[targetStorageClass] ?: RATES["COLDLINE"]!!
        }
        
        return (accessedSizeGb * currentRate) + (unaccessedSizeGb * targetRate)
    }
}
