package bleep
package plugin.jni

import bleep.internal.FileUtils
import bleep.logging.Logger

import java.nio.file.{Files, Path}

/** Wraps a native build system in sbt tasks.
  */
class JniNative(
    val logger: Logger,
    val nativeCompileSourceDirectory: Path,
    // this will be appended something like `/native/arm64-darwin`
    val nativeTargetDirectory: Path,
    // The build tool to be used when building a native library.
    val nativeBuildTool: BuildTool,
    val libName: String,
    val env: List[(String, String)]
) {
  nativeBuildTool.ensureHasBuildFile(nativeCompileSourceDirectory, logger, libName)

  // Builds a native library by calling the native build tool.
  def nativeCompile(): Path = {
    val tool = nativeBuildTool
    val toolInstance = nativeBuildToolInstance
    val targetDir = nativeCompileTarget / "bin"
    Files.createDirectories(targetDir)

    logger.info(s"Building library with native build tool ${tool.name}")
    val lib = toolInstance.library(targetDir)
    logger.info(s"Library built in $lib")
    lib

  }

  // Platform (architecture-kernel) of the system this build is running on.
  // the value returned must match that of `com.github.sbt.jni.PlatformMacros#current()` of project `macros`
  lazy val nativePlatform: String =
    try {
      val lines = cli("check platform", FileUtils.TempDir, List("uname", "-sm"), logger = logger, out = cli.Out.ViaLogger(logger)).stdout
      if (lines.isEmpty) {
        sys.error("Error occurred trying to run `uname`")
      }
      // uname -sm returns "<kernel> <hardware name>"
      val parts = lines.head.split(" ")
      if (parts.length != 2) {
        sys.error("'uname -sm' returned unexpected string: " + lines.head)
      } else {
        val arch = parts(1).toLowerCase.replaceAll("\\s", "")
        val kernel = parts(0).toLowerCase.replaceAll("\\s", "")
        arch + "-" + kernel
      }
    } catch {
      case _: Exception =>
        logger.error("Error trying to determine platform.")
        logger.warn("Cannot determine platform! It will be set to 'unknown'.")
        "unknown-unknown"
    }

  lazy val nativeCompileTarget = nativeTargetDirectory / "native" / nativePlatform

  // Get an instance of the current native build tool.
  lazy val nativeBuildToolInstance: BuildTool.Instance = {
    val buildDir = nativeCompileTarget / "build"
    Files.createDirectories(buildDir)

    nativeBuildTool.getInstance(
      baseDirectory = nativeCompileSourceDirectory,
      buildDirectory = buildDir,
      logger,
      env
    )
  }

  def nativeCompileClean() = {
    logger.debug("Cleaning native build")
    try {
      val toolInstance = nativeBuildToolInstance
      toolInstance.clean()
    } catch {
      case ex: Exception =>
        logger.debug(s"Native cleaning failed: $ex")
    }
  }
}
