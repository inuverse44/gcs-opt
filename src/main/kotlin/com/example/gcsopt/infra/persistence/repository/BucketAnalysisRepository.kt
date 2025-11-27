package com.example.gcsopt.infra.persistence.repository

import com.example.gcsopt.infra.persistence.entity.BucketAnalysisEntity
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant

/**
 * バケット分析結果のリポジトリ
 */
@ApplicationScoped
class BucketAnalysisRepository : PanacheRepositoryBase<BucketAnalysisEntity, Long> {

    fun findLatest(
        projectId: String,
        thresholdDays: Long,
        bucketFilter: String? = null,
        minAnalyzedAt: Instant? = null
    ): List<BucketAnalysisEntity> {
        val params = mutableMapOf<String, Any>(
            "projectId" to projectId,
            "thresholdDays" to thresholdDays
        )
        val query = StringBuilder("projectId = :projectId AND thresholdDays = :thresholdDays")

        bucketFilter?.let {
            query.append(" AND bucketName = :bucketName")
            params["bucketName"] = it
        }

        minAnalyzedAt?.let {
            query.append(" AND analyzedAt >= :minAnalyzedAt")
            params["minAnalyzedAt"] = it
        }

        query.append(" ORDER BY analyzedAt DESC")

        return find(query.toString(), params).list()
    }
}

