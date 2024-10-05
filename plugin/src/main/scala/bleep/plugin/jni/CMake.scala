package bleep
package plugin.jni

import bleep.internal.FileUtils
import ryddig.Logger

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

object CMake extends BuildTool {

  override val name = "CMake"

  def template(libName: String) =
    s"""################################################################
      |# A minimal CMake file that is compatible with sbt-jni         #
      |#                                                              #
      |# All settings required by sbt-jni have been marked so, please #
      |# add/modify/remove settings to build your specific library.   #
      |################################################################
      |
      |cmake_minimum_required(VERSION 3.12)
      |
      |option(SBT "Set if invoked from sbt-jni" OFF)
      |
      |# Define project and related variables
      |# (required by sbt-jni) please use semantic versioning
      |#
      |project (${libName})
      |set(PROJECT_VERSION_MAJOR 0)
      |set(PROJECT_VERSION_MINOR 0)
      |set(PROJECT_VERSION_PATCH 0)
      |
      |# Setup JNI
      |find_package(JNI REQUIRED)
      |if (JNI_FOUND)
      |    message (STATUS "JNI include directories: $${JNI_INCLUDE_DIRS}")
      |endif()
      |
      |# Include directories
      |include_directories(.)
      |include_directories(include)
      |include_directories($${JNI_INCLUDE_DIRS})
      |
      |# Sources
      |file(GLOB LIB_SRC
      |  "*.c"
      |  "*.cc"
      |  "*.cpp"
      |)
      |
      |# Setup installation targets
      |# (required by sbt-jni) major version should always be appended to library name
      |#
      |set (LIB_NAME $${PROJECT_NAME}$${PROJECT_VERSION_MAJOR})
      |add_library($${LIB_NAME} SHARED $${LIB_SRC})
      |install(TARGETS $${LIB_NAME} LIBRARY DESTINATION .)
      |""".stripMargin

  override def ensureHasBuildFile(sourceDirectory: Path, logger: Logger, libName: String) = {
    val buildScript = sourceDirectory / "CMakeLists.txt"
    if (FileUtils.exists(buildScript)) ()
    else FileUtils.writeString(logger, Some(s"Initialized empty build script for $name"), buildScript, template(libName))
    ()
  }

  override def getInstance(baseDir: Path, buildDir: Path, logger: Logger, env: List[(String, String)]): BuildTool.Instance =
    new Instance(baseDir, buildDir, logger, env)

  class Instance(baseDir: Path, buildDir: Path, logger: Logger, env: List[(String, String)]) extends BuildTool.Instance {

    private val cliOut: cli.Out = cli.Out.ViaLogger(logger)

    def cmakeProcess(args: List[String]): cli.WrittenLines =
      cli("cmake", baseDir, "cmake" :: args, logger = logger, out = cliOut, env = env)

    def cmakeProcess(args: String*): cli.WrittenLines =
      cmakeProcess(args.toList)

    lazy val cmakeVersion =
      cmakeProcess("--version").stdout.head
        .split("\\s+")
        .last
        .split("\\.") match {
        case Array(maj, min, rev) =>
          logger.info(s"Using CMake version $maj.$min.$rev")
          maj.toInt * 100 + min.toInt
        case _ => -1
      }

    def parallelJobs: Int = java.lang.Runtime.getRuntime.availableProcessors()

    def parallelOptions: Seq[String] =
      if (cmakeVersion >= 312) Seq("--parallel", parallelJobs.toString())
      else Seq.empty

    override def clean(): Unit =
      cmakeProcess("--build", buildDir.toString, "--target", "clean").discard()

    def configure(target: Path): Unit =
      cmakeProcess(
        List(
          // disable producing versioned library files, not needed for fat jars
          s"-DCMAKE_INSTALL_PREFIX:PATH=$target",
          "-DCMAKE_BUILD_TYPE=Release",
          "-DSBT:BOOLEAN=true",
          cmakeVersion.toString,
          baseDir.toString
        )
      ).discard()

    def make(): Unit =
      cmakeProcess(List("--build", buildDir.toString) ++ parallelOptions).discard()

    def install(): Unit =
      // https://cmake.org/cmake/help/v3.15/release/3.15.html#id6
      // Beginning with version 3.15, CMake introduced the install switch
      if (cmakeVersion >= 315) cmakeProcess("--install", buildDir.toString).discard()
      else cli("make install", buildDir, List("make", "install"), logger = logger, out = cliOut, env = env).discard()

    def library(targetDirectory: Path): Path = {
      configure(targetDirectory)
      make()
      install()

      val products: List[Path] =
        Files
          .walk(targetDirectory)
          .filter(Files.isRegularFile(_))
          .filter { p =>
            val fileName = p.getFileName.toString
            fileName.endsWith(".so") || fileName.endsWith(".dylib")
          }
          .iterator
          .asScala
          .toList

      // only one produced library is expected
      products match {
        case Nil =>
          sys.error(
            s"No files were created during compilation, " +
              s"something went wrong with the ${name} configuration."
          )
        case head :: Nil =>
          head
        case head :: _ =>
          logger.warn(
            "More than one file was created during compilation, " +
              s"only the first one ($head) will be used."
          )
          head
      }
    }
  }
}
