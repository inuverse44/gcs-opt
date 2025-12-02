package com.example.gcsopt.infra.persistence.entity

import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "bucket_analysis")
class BucketAnalysisEntity : PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false)
    lateinit var projectId: String

    @Column(nullable = false)
    lateinit var bucketName: String

    @Column(nullable = false)
    lateinit var bucketLocation: String

    @Column(nullable = false)
    lateinit var bucketStorageClass: String

    @Column(nullable = false)
    var bucketCreated: Instant = Instant.EPOCH

    @Column(columnDefinition = "TEXT")
    var bucketLabels: String? = null

    @Column(nullable = false)
    var thresholdDays: Long = 0

    @Column(nullable = false)
    lateinit var targetClass: String

    var compressionRatio: Double? = null

    @Column(nullable = false)
    var totalObjects: Long = 0

    @Column(nullable = false)
    var totalSize: Long = 0

    @Column(nullable = false)
    var unaccessedObjects: Long = 0

    @Column(nullable = false)
    var unaccessedSize: Long = 0

    @Column(nullable = false)
    var currentCost: Double = 0.0

    @Column(nullable = false)
    var optimizedCost: Double = 0.0

    @Column(nullable = false)
    var savings: Double = 0.0

    @Column(nullable = false)
    var analyzedAt: Instant = Instant.now()
}

