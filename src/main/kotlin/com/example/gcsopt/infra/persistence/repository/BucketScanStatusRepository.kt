package com.example.gcsopt.infra.persistence.repository

import com.example.gcsopt.infra.persistence.entity.BucketScanStatusEntity
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class BucketScanStatusRepository : PanacheRepositoryBase<BucketScanStatusEntity, String>
