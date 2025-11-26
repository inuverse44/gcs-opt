package com.example.gcsopt

import com.example.gcsopt.cli.ScanCommand
import io.quarkus.picocli.runtime.annotations.TopCommand
import picocli.CommandLine

@TopCommand
@CommandLine.Command(
    name = "gcs-opt",
    mixinStandardHelpOptions = true,
    version = ["1.0.0"],
    description = ["GCS Optimizer CLI - Analyze and optimize Google Cloud Storage costs"],
    subcommands = [ScanCommand::class]
)
class GcsOptCommand : Runnable {
    override fun run() {
        // デフォルトではヘルプを表示
        CommandLine(this).usage(System.out)
    }
}
