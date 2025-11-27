package com.example.gcsopt.infra.persistence

import com.example.gcsopt.domain.model.ObjectInfo
import com.example.gcsopt.infra.persistence.entity.BucketScanStatusEntity
import com.example.gcsopt.infra.persistence.entity.GcsObjectEntity
import com.example.gcsopt.infra.persistence.repository.BucketScanStatusRepository
import com.example.gcsopt.infra.persistence.repository.GcsObjectRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import java.time.Instant
import java.time.temporal.ChronoUnit

@ApplicationScoped
class GcsObjectPersistenceService {

    @Inject
    lateinit var objectRepository: GcsObjectRepository

    @Inject
    lateinit var statusRepository: BucketScanStatusRepository

    @Transactional
    fun clearCache(bucketName: String) {
        objectRepository.deleteByBucketName(bucketName)
        statusRepository.deleteById(bucketName)
    }

    @Transactional
    fun saveChunk(bucketName: String, objects: List<ObjectInfo>) {
        val now = Instant.now()
        objects.forEach { obj ->
            val entity = GcsObjectEntity().apply {
                this.bucketName = bucketName
                this.name = obj.name
                this.size = obj.size
                this.storageClass = obj.storageClass
                this.created = obj.created
                this.updated = obj.updated
                this.scannedAt = now
            }
            objectRepository.persist(entity)
        }
    }

    @Transactional
    fun updateScanStatus(bucketName: String) {
        val now = Instant.now()
        val status = statusRepository.findById(bucketName) ?: BucketScanStatusEntity().apply {
            this.bucketName = bucketName
        }
        status.lastScannedAt = now
        statusRepository.persist(status)
    }

    @Transactional
    fun loadObjects(bucketName: String): Flow<ObjectInfo> {
        return objectRepository.findByBucketName(bucketName)
            .map { entity ->
                ObjectInfo(
                    name = entity.name,
                    bucketName = entity.bucketName,
                    size = entity.size,
                    storageClass = entity.storageClass,
                    created = entity.created,
                    updated = entity.updated
                )
            }.asFlow()
    }

    @Transactional
    fun isCacheValid(bucketName: String, ttlMinutes: Long): Boolean {
        val status = statusRepository.findById(bucketName) ?: return false
        val threshold = Instant.now().minus(ttlMinutes, ChronoUnit.MINUTES)
        return status.lastScannedAt.isAfter(threshold)
    }
}
