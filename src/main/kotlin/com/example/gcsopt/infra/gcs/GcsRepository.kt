package com.example.gcsopt.infra.gcs

import com.example.gcsopt.domain.model.BucketInfo
import com.example.gcsopt.domain.model.ObjectInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * GCS Repository Interface
 */
interface GcsRepository {
    fun listBuckets(projectId: String): List<BucketInfo>
    fun listObjects(bucketName: String): Flow<ObjectInfo>
}

/**
 * GCS Repository Implementation
 */
@ApplicationScoped
class GcsRepositoryImpl : GcsRepository {
    
    private val storage: Storage = StorageOptions.getDefaultInstance().service
    
    override fun listBuckets(projectId: String): List<BucketInfo> {
        val buckets = mutableListOf<BucketInfo>()
        
        for (bucket in storage.list().iterateAll()) {
            buckets.add(
                BucketInfo(
                    name = bucket.name,
                    location = bucket.location ?: "UNKNOWN",
                    storageClass = bucket.storageClass?.toString() ?: "STANDARD",
                    created = bucket.createTimeOffsetDateTime.toInstant(),
                    labels = bucket.labels?.filterValues { it != null }?.mapValues { it.value!! } ?: emptyMap()
                )
            )
        }
        
        return buckets
    }
    
    override fun listObjects(bucketName: String): Flow<ObjectInfo> = flow {
        val blobs = storage.list(
            bucketName,
            Storage.BlobListOption.pageSize(1000)
        )
        
        for (blob in blobs.iterateAll()) {
            emit(
                ObjectInfo(
                    name = blob.name,
                    bucketName = blob.bucket,
                    size = blob.size ?: 0,
                    storageClass = blob.storageClass?.toString() ?: "STANDARD",
                    updated = blob.updateTimeOffsetDateTime.toInstant(),
                    created = blob.createTimeOffsetDateTime.toInstant()
                )
            )
        }
    }
}
