package com.example.gcsopt.infra.persistence.repository

import com.example.gcsopt.infra.persistence.entity.GcsObjectEntity
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepository
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class GcsObjectRepository : PanacheRepository<GcsObjectEntity> {
    fun findByBucketName(bucketName: String): List<GcsObjectEntity> {
        return list("bucketName", bucketName)
    }

    fun deleteByBucketName(bucketName: String) {
        delete("bucketName", bucketName)
    }
}
