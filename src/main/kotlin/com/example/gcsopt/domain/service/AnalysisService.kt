package com.example.gcsopt.domain.service

import com.example.gcsopt.domain.model.BucketAnalysis
import com.example.gcsopt.domain.model.BucketInfo
import com.example.gcsopt.infra.gcs.GcsRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 分析サービス
 */
@ApplicationScoped
class AnalysisService {
    
    @Inject
    lateinit var repository: GcsRepository
    
    @Inject
    lateinit var calculator: CostCalculator
    
    /**
     * バケットを分析
     */
    /**
     * バケットを分析
     */
    fun analyzeBucket(
        bucket: BucketInfo,
        thresholdDays: Long,
        targetClass: String = "COLDLINE",
        compressionRatio: Double? = null
    ): BucketAnalysis = runBlocking {
        println("  Analyzing bucket: ${bucket.name}...")
        
        val threshold = Instant.now().minus(thresholdDays, ChronoUnit.DAYS)
        
        var totalObjects = 0L
        var totalSize = 0L
        var unaccessedObjects = 0L
        var unaccessedSize = 0L
        var oldestObject: Instant? = null
        
        // Flowを使ってストリーム処理（メモリ効率化）
        repository.listObjects(bucket.name).collect { obj ->
            totalObjects++
            totalSize += obj.size
            
            if (oldestObject == null || obj.created.isBefore(oldestObject)) {
                oldestObject = obj.created
            }
            
            // GCS Lifecycle Ruleの "Age" は作成日時(created)に基づくため、
            // updatedではなくcreatedを使用するように変更
            if (obj.created.isBefore(threshold)) {
                unaccessedObjects++
                unaccessedSize += obj.size
            }
            
            // 進捗表示（10000件ごと）
            if (totalObjects % 10000 == 0L) {
                print("\r    Processed $totalObjects objects...")
            }
        }
        println("\r    Processed $totalObjects objects. Done.")
        println("    Oldest object created at: ${oldestObject ?: "N/A"}")
        
        val currentCost = calculator.calculateCurrentCost(totalSize, bucket.storageClass)
        val optimizedCost = calculator.calculateOptimizedCost(
            totalSize,
            unaccessedSize,
            bucket.storageClass,
            targetStorageClass = targetClass,
            compressionRatio = compressionRatio
        )
        val savings = currentCost - optimizedCost
        
        println("    Analysis complete")
        
        BucketAnalysis(
            bucket = bucket,
            totalObjects = totalObjects,
            totalSize = totalSize,
            unaccessedObjects = unaccessedObjects,
            unaccessedSize = unaccessedSize,
            currentCost = currentCost,
            optimizedCost = optimizedCost,
            savings = savings
        )
    }
    
    /**
     * プロジェクト全体を分析
     */
    fun analyzeProject(
        projectId: String,
        thresholdDays: Long,
        targetClass: String = "COLDLINE",
        compressionRatio: Double? = null,
        bucketFilter: String? = null
    ): List<BucketAnalysis> {
        println("Fetching bucket list...")
        val buckets = repository.listBuckets(projectId)
            .filter { bucketFilter == null || it.name == bucketFilter }
        
        println("Found ${buckets.size} bucket(s) to analyze\n")
        
        return buckets.mapIndexed { index, bucket ->
            println("[${index + 1}/${buckets.size}]")
            analyzeBucket(bucket, thresholdDays, targetClass, compressionRatio)
        }
    }
}
