package com.example.gcsopt.domain.model

import java.time.Instant

/**
 * オブジェクト情報（分析に必要な最小限のフィールド）
 */
data class ObjectInfo(
    val name: String,
    val bucketName: String,
    val size: Long,
    val storageClass: String,
    val updated: Instant,
    val created: Instant
)
