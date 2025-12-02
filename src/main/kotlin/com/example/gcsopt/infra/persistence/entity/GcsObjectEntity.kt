package com.example.gcsopt.infra.persistence.entity

import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntity
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "gcs_objects", indexes = [
    Index(name = "idx_bucket_name", columnList = "bucketName")
])
open class GcsObjectEntity : PanacheEntity() {
    lateinit var bucketName: String
    lateinit var name: String
    var size: Long = 0
    lateinit var storageClass: String
    lateinit var created: Instant
    lateinit var updated: Instant
    lateinit var scannedAt: Instant
}
