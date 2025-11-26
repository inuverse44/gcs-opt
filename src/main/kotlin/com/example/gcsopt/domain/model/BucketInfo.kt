package com.example.gcsopt.domain.model

import java.time.Instant

/**
 * バケット情報
 */
data class BucketInfo(
    val name: String,
    val location: String,
    val storageClass: String,
    val created: Instant,
    val labels: Map<String, String> = emptyMap()
)
