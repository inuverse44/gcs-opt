package com.example.gcsopt.domain.service

import com.example.gcsopt.domain.model.BucketAnalysis
import com.example.gcsopt.domain.model.BucketInfo
import com.example.gcsopt.infra.gcs.GcsRepository
import com.example.gcsopt.infra.persistence.BucketAnalysisPersistenceService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit

@ApplicationScoped
class AnalysisService {
    
    @Inject
    lateinit var repository: GcsRepository
    
    @Inject
    lateinit var calculator: CostCalculator

    @Inject
    lateinit var persistence: BucketAnalysisPersistenceService
    
    suspend fun analyzeBucket(
        bucket: BucketInfo,
        thresholdDays: Long,
        targetClass: String = "COLDLINE",
        compressionRatio: Double? = null
    ): BucketAnalysis {
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
        
        return BucketAnalysis(
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
    
    fun analyzeProject(
        projectId: String,
        thresholdDays: Long,
        targetClass: String = "COLDLINE",
        compressionRatio: Double? = null,
        bucketFilter: String? = null
    ): List<BucketAnalysis> = runBlocking {
        println("Fetching bucket list...")
        val buckets = repository.listBuckets(projectId)
            .filter { bucketFilter == null || it.name == bucketFilter }
        
        println("Found ${buckets.size} bucket(s) to analyze\n")
        
        // 並列実行
        buckets.mapIndexed { index, bucket ->
            async {
                println("[${index + 1}/${buckets.size}] Starting analysis for ${bucket.name}")
                analyzeBucket(bucket, thresholdDays, targetClass, compressionRatio)
            }
        }.awaitAll().also { analyses ->
            persistence.saveResults(
                projectId = projectId,
                thresholdDays = thresholdDays,
                targetClass = targetClass,
                compressionRatio = compressionRatio,
                analyses = analyses
            )
        }
    }

    fun loadCachedProjectAnalysis(
        projectId: String,
        thresholdDays: Long,
        bucketFilter: String? = null,
        cacheTtlMinutes: Long? = null
    ): List<BucketAnalysis> =
        persistence.loadCachedResults(projectId, thresholdDays, bucketFilter, cacheTtlMinutes)
}
