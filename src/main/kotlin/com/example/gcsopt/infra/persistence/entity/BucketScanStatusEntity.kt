package com.example.gcsopt.infra.persistence.entity

import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "bucket_scan_status")
open class BucketScanStatusEntity : PanacheEntityBase {
    @Id
    lateinit var bucketName: String
    
    lateinit var lastScannedAt: Instant
}
