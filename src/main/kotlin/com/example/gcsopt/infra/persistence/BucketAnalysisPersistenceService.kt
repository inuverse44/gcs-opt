package com.example.gcsopt.infra.persistence

import com.example.gcsopt.domain.model.BucketAnalysis
import com.example.gcsopt.domain.model.BucketInfo
import com.example.gcsopt.infra.persistence.entity.BucketAnalysisEntity
import com.example.gcsopt.infra.persistence.repository.BucketAnalysisRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@ApplicationScoped
class BucketAnalysisPersistenceService {

    @Inject
    lateinit var repository: BucketAnalysisRepository

    @Transactional
    fun saveResults(
        projectId: String,
        thresholdDays: Long,
        targetClass: String,
        compressionRatio: Double?,
        analyses: List<BucketAnalysis>,
        analyzedAt: Instant = Instant.now()
    ) {
        analyses.forEach { analysis ->
            repository.persist(analysis.toEntity(projectId, thresholdDays, targetClass, compressionRatio, analyzedAt))
        }
    }

    @Transactional
    fun loadCachedResults(
        projectId: String,
        thresholdDays: Long,
        bucketFilter: String?,
        cacheTtlMinutes: Long?
    ): List<BucketAnalysis> {
        val ttlThreshold = cacheTtlMinutes?.let {
            Instant.now().minus(it, ChronoUnit.MINUTES)
        }

        val entities = repository.findLatest(projectId, thresholdDays, bucketFilter, ttlThreshold)
        if (entities.isEmpty()) return emptyList()

        return entities
            .groupBy { it.bucketName }
            .mapNotNull { (_, bucketEntities) -> bucketEntities.maxByOrNull { it.analyzedAt } }
            .map { it.toDomain() }
    }

    private fun BucketAnalysis.toEntity(
        projectId: String,
        thresholdDays: Long,
        targetClass: String,
        compressionRatio: Double?,
        analyzedAt: Instant
    ): BucketAnalysisEntity {
        return BucketAnalysisEntity().also { entity ->
            entity.projectId = projectId
            entity.bucketName = bucket.name
            entity.bucketLocation = bucket.location
            entity.bucketStorageClass = bucket.storageClass
            entity.bucketCreated = bucket.created
            entity.bucketLabels = bucket.labels.toLabelString()
            entity.thresholdDays = thresholdDays
            entity.targetClass = targetClass
            entity.compressionRatio = compressionRatio
            entity.totalObjects = totalObjects
            entity.totalSize = totalSize
            entity.unaccessedObjects = unaccessedObjects
            entity.unaccessedSize = unaccessedSize
            entity.currentCost = currentCost
            entity.optimizedCost = optimizedCost
            entity.savings = savings
            entity.analyzedAt = analyzedAt
        }
    }

    private fun BucketAnalysisEntity.toDomain(): BucketAnalysis {
        return BucketAnalysis(
            bucket = BucketInfo(
                name = bucketName,
                location = bucketLocation,
                storageClass = bucketStorageClass,
                created = bucketCreated,
                labels = bucketLabels.toLabelMap()
            ),
            totalObjects = totalObjects,
            totalSize = totalSize,
            unaccessedObjects = unaccessedObjects,
            unaccessedSize = unaccessedSize,
            currentCost = currentCost,
            optimizedCost = optimizedCost,
            savings = savings
        )
    }

    private fun Map<String, String>.toLabelString(): String? =
        if (isEmpty()) null else entries.joinToString(separator = "&") { "${it.key}=${it.value}" }

    private fun String?.toLabelMap(): Map<String, String> {
        if (isNullOrBlank()) return emptyMap()
        return split("&").mapNotNull { pair ->
            val separatorIndex = pair.indexOf("=")
            if (separatorIndex <= 0) return@mapNotNull null
            val key = pair.substring(0, separatorIndex)
            val value = pair.substring(separatorIndex + 1)
            key to value
        }.toMap()
    }
}

