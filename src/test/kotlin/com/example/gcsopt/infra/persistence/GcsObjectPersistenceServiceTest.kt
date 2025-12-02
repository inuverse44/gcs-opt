package com.example.gcsopt.infra.persistence

import com.example.gcsopt.domain.model.ObjectInfo
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@QuarkusTest
class GcsObjectPersistenceServiceTest {

    @Inject
    lateinit var service: GcsObjectPersistenceService

    private val testBucketName = "test-bucket-${System.currentTimeMillis()}"

    @BeforeEach
    @Transactional
    fun cleanup() {
        service.clearCache(testBucketName)
    }

    @Test
    fun `isCacheValid should return false when no cache exists`() {
        val isValid = service.isCacheValid(testBucketName, 1440)
        assertFalse(isValid)
    }

    @Test
    @Transactional
    fun `saveChunk and loadObjects should persist and retrieve objects`() = runBlocking {
        val objects = listOf(
            ObjectInfo("obj1", testBucketName, 100, "STANDARD", Instant.now(), Instant.now()),
            ObjectInfo("obj2", testBucketName, 200, "STANDARD", Instant.now(), Instant.now())
        )

        service.saveChunk(testBucketName, objects)
        service.updateScanStatus(testBucketName)

        val loaded = service.loadObjects(testBucketName).toList()
        assertEquals(2, loaded.size)
        assertEquals("obj1", loaded[0].name)
        assertEquals("obj2", loaded[1].name)
    }

    @Test
    @Transactional
    fun `isCacheValid should return true when cache is fresh`() {
        val objects = listOf(
            ObjectInfo("obj1", testBucketName, 100, "STANDARD", Instant.now(), Instant.now())
        )

        service.saveChunk(testBucketName, objects)
        service.updateScanStatus(testBucketName)

        val isValid = service.isCacheValid(testBucketName, 1440)
        assertTrue(isValid)
    }

    @Test
    @Transactional
    fun `isCacheValid should return false when cache is expired`() {
        val objects = listOf(
            ObjectInfo("obj1", testBucketName, 100, "STANDARD", Instant.now(), Instant.now())
        )

        service.saveChunk(testBucketName, objects)
        service.updateScanStatus(testBucketName)

        // TTL of 0 minutes means cache is immediately expired
        val isValid = service.isCacheValid(testBucketName, 0)
        assertFalse(isValid)
    }

    @Test
    @Transactional
    fun `clearCache should remove all objects and status`() = runBlocking {
        val objects = listOf(
            ObjectInfo("obj1", testBucketName, 100, "STANDARD", Instant.now(), Instant.now())
        )

        service.saveChunk(testBucketName, objects)
        service.updateScanStatus(testBucketName)

        service.clearCache(testBucketName)

        val loaded = service.loadObjects(testBucketName).toList()
        assertEquals(0, loaded.size)

        val isValid = service.isCacheValid(testBucketName, 1440)
        assertFalse(isValid)
    }
}
