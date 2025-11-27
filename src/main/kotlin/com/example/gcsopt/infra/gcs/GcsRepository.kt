package com.example.gcsopt.infra.gcs

import com.example.gcsopt.domain.model.BucketInfo
import com.example.gcsopt.domain.model.ObjectInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.annotation.Priority
import jakarta.enterprise.inject.Alternative
import jakarta.inject.Named
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
 * GCS Repository Implementation (Direct API Access)
 */
class RealGcsRepository(private val storage: Storage) : GcsRepository {
    
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
            Storage.BlobListOption.pageSize(5000),
            Storage.BlobListOption.fields(
                Storage.BlobField.NAME,
                Storage.BlobField.BUCKET,
                Storage.BlobField.SIZE,
                Storage.BlobField.STORAGE_CLASS,
                Storage.BlobField.TIME_CREATED,
                Storage.BlobField.UPDATED
            )
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

/**
 * Cached GCS Repository
 */
@ApplicationScoped
class CachedGcsRepository : GcsRepository {

    @Inject
    lateinit var storage: Storage

    private val delegate: RealGcsRepository by lazy { RealGcsRepository(storage) }

    @Inject
    lateinit var persistence: com.example.gcsopt.infra.persistence.GcsObjectPersistenceService
    
    // Default TTL: 24 hours (can be configured via properties if needed, but hardcoded for now as per plan)
    // The user request mentioned -d 30 and -d 180, implying we want to reuse cache regardless of CLI args for analysis.
    // The cache validity here is about "how fresh is the object list".
    private val cacheTtlMinutes = 1440L 

    override fun listBuckets(projectId: String): List<BucketInfo> {
        // Buckets are not cached in this layer currently, delegate to real
        return delegate.listBuckets(projectId)
    }

    override fun listObjects(bucketName: String): Flow<ObjectInfo> {
        if (persistence.isCacheValid(bucketName, cacheTtlMinutes)) {
            println("    [Cache] Using cached objects for bucket: $bucketName")
            return persistence.loadObjects(bucketName)
        }

        println("    [Cache] Cache miss or expired for bucket: $bucketName. Fetching from GCS...")
        
        // Clear old cache before starting
        persistence.clearCache(bucketName)

        return flow {
            val buffer = mutableListOf<ObjectInfo>()
            val bufferSize = 1000

            delegate.listObjects(bucketName).collect { obj ->
                emit(obj) // Pass through to caller immediately
                
                buffer.add(obj)
                if (buffer.size >= bufferSize) {
                    persistence.saveChunk(bucketName, buffer.toList())
                    buffer.clear()
                }
            }
            
            // Save remaining
            if (buffer.isNotEmpty()) {
                persistence.saveChunk(bucketName, buffer.toList())
            }
            
            // Mark as complete
            persistence.updateScanStatus(bucketName)
        }
    }
}
