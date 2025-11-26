package com.example.gcsopt.domain.model

/**
 * 分析結果（バケット単位）
 */
data class BucketAnalysis(
    val bucket: BucketInfo,
    val totalObjects: Long,
    val totalSize: Long,
    val unaccessedObjects: Long,
    val unaccessedSize: Long,
    val currentCost: Double,
    val optimizedCost: Double,
    val savings: Double
) {
    val savingsRatio: Double
        get() = if (currentCost > 0) (savings / currentCost) * 100 else 0.0
    
    val unaccessedRatio: Double
        get() = if (totalObjects > 0) (unaccessedObjects.toDouble() / totalObjects) * 100 else 0.0
}
