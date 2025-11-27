package com.example.gcsopt.cli

import com.example.gcsopt.domain.model.BucketAnalysis
import com.example.gcsopt.domain.service.AnalysisService
import jakarta.inject.Inject
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import kotlin.math.abs

/**
 * Scan Command
 */
@Command(
    name = "scan",
    mixinStandardHelpOptions = true,
    description = ["Scan GCS buckets for optimization opportunities"]
)
class ScanCommand : Runnable {
    
    @Option(
        names = ["-p", "--project"],
        description = ["Google Cloud Project ID"],
        required = true
    )
    lateinit var projectId: String
    
    @Option(
        names = ["-b", "--bucket"],
        description = ["Specific bucket name (optional, scans all if not specified)"]
    )
    var bucketName: String? = null
    
    @Option(
        names = ["-d", "--threshold-days"],
        description = ["Days to consider object as unaccessed (default: 180)"],
        defaultValue = "180"
    )
    var thresholdDays: Long = 180
    
    @Option(
        names = ["-t", "--target-class"],
        description = ["Target storage class for optimization (e.g. NEARLINE, COLDLINE, ARCHIVE, DELETE). Default: COLDLINE"],
        defaultValue = "COLDLINE"
    )
    var targetClass: String = "COLDLINE"
    
    @Option(
        names = ["-c", "--estimate-compression"],
        description = ["Estimate compression savings (default compression ratio: 0.5)"]
    )
    var estimateCompression: Boolean = false
    
    @Option(
        names = ["-o", "--output"],
        description = ["Output format: text or json (default: text)"],
        defaultValue = "text"
    )
    var outputFormat: String = "text"

    @Option(
        names = ["--use-cache"],
        description = ["Use cached analysis results when available (default: false)"]
    )
    var useCache: Boolean = false

    @Option(
        names = ["--cache-ttl-minutes"],
        description = ["Cache TTL minutes when --use-cache is enabled (default: 1440)"],
        defaultValue = "1440"
    )
    var cacheTtlMinutes: Long = 1440
    
    @Inject
    lateinit var analysisService: AnalysisService
    
    override fun run() {
        println("Scanning project: $projectId")
        println("Threshold: $thresholdDays days")
        println("Target Class: $targetClass")
        if (bucketName != null) {
            println("Bucket filter: $bucketName")
        }
        if (estimateCompression) {
            println("Compression estimation: enabled (ratio: 0.5)")
        }
        println()
        
        val compressionRatio = if (estimateCompression) 0.5 else null

        if (useCache) {
            val cachedResults = analysisService.loadCachedProjectAnalysis(
                projectId = projectId,
                thresholdDays = thresholdDays,
                bucketFilter = bucketName,
                cacheTtlMinutes = cacheTtlMinutes
            )

            if (cachedResults.isNotEmpty()) {
                println("Using cached analysis (<= ${cacheTtlMinutes}min). Use without --use-cache to force refresh.")
                println()
                when (outputFormat.lowercase()) {
                    "json" -> {
                        printJson(cachedResults)
                    }

                    else -> {
                        printText(cachedResults)
                    }
                }
                return
            } else {
                println("No cached analysis within ${cacheTtlMinutes}min. Running live scan...\n")
            }
        }
        
        val results = analysisService.analyzeProject(
            projectId = projectId,
            thresholdDays = thresholdDays,
            targetClass = targetClass,
            compressionRatio = compressionRatio,
            bucketFilter = bucketName
        )
        
        when (outputFormat.lowercase()) {
            "json" -> printJson(results)
            else -> printText(results)
        }
    }
    
    private fun printText(results: List<BucketAnalysis>) {
        if (results.isEmpty()) {
            println("No buckets found.")
            return
        }
        
        println("%-30s %-10s %8s %10s %20s %12s %12s %15s".format(
            "Bucket", "Class", "Objects", "Size", "Unaccessed(${thresholdDays}d)", 
            "Est.Cost/Mo", "Optimized", "Savings"
        ))
        println("-".repeat(130))
        
        var totalObjects = 0L
        var totalSize = 0L
        var totalUnaccessed = 0L
        var totalCurrentCost = 0.0
        var totalOptimizedCost = 0.0
        var totalSavings = 0.0
        
        for (result in results) {
            totalObjects += result.totalObjects
            totalSize += result.totalSize
            totalUnaccessed += result.unaccessedObjects
            totalCurrentCost += result.currentCost
            totalOptimizedCost += result.optimizedCost
            totalSavings += result.savings
            
            println("%-30s %-10s %8d %10s %10d (%5.1f%%) %12s %12s %15s".format(
                result.bucket.name.take(30),
                result.bucket.storageClass.take(10),
                result.totalObjects,
                formatSize(result.totalSize),
                result.unaccessedObjects,
                result.unaccessedRatio,
                "$%.2f".format(result.currentCost),
                "$%.2f".format(result.optimizedCost),
                "$%.2f (%.0f%%)".format(abs(result.savings), abs(result.savingsRatio))
            ))
        }
        
        println("-".repeat(130))
        val totalSavingsRatio = if (totalCurrentCost > 0) (totalSavings / totalCurrentCost) * 100 else 0.0
        val totalUnaccessedRatio = if (totalObjects > 0) (totalUnaccessed.toDouble() / totalObjects) * 100 else 0.0
        
        println("%-30s %-10s %8d %10s %10d (%5.1f%%) %12s %12s %15s".format(
            "Total",
            "",
            totalObjects,
            formatSize(totalSize),
            totalUnaccessed,
            totalUnaccessedRatio,
            "$%.2f".format(totalCurrentCost),
            "$%.2f".format(totalOptimizedCost),
            "$%.2f (%.0f%%)".format(abs(totalSavings), abs(totalSavingsRatio))
        ))
    }
    
    private fun printJson(results: List<BucketAnalysis>) {
        // Simple JSON output (in production, use kotlinx.serialization)
        println("{")
        println("  \"summary\": {")
        println("    \"totalBuckets\": ${results.size},")
        println("    \"totalSavings\": ${results.sumOf { it.savings }},")
        println("    \"currency\": \"USD\"")
        println("  },")
        println("  \"buckets\": [")
        
        results.forEachIndexed { index, result ->
            println("    {")
            println("      \"name\": \"${result.bucket.name}\",")
            println("      \"location\": \"${result.bucket.location}\",")
            println("      \"storageClass\": \"${result.bucket.storageClass}\",")
            println("      \"stats\": {")
            println("        \"objectCount\": ${result.totalObjects},")
            println("        \"totalSize\": ${result.totalSize},")
            println("        \"unaccessedCount\": ${result.unaccessedObjects},")
            println("        \"unaccessedSize\": ${result.unaccessedSize}")
            println("      },")
            println("      \"costs\": {")
            println("        \"current\": ${result.currentCost},")
            println("        \"optimized\": ${result.optimizedCost},")
            println("        \"savings\": ${result.savings}")
            println("      }")
            print("    }")
            if (index < results.size - 1) println(",") else println()
        }
        
        println("  ]")
        println("}")
    }
    
    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.2f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }
}
