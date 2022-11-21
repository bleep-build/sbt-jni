package com.github.sbt.jni.build

import bleep.internal.FileUtils
import bleep.{cli, PathOps}
import bleep.logging.Logger

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

class Cargo(protected val release: Boolean = true) extends BuildTool {

  def name: String = "Cargo"

  def ensureHasBuildFile(sourceDirectory: Path, logger: Logger, libName: String): Unit = {
    val buildScript = sourceDirectory / "Cargo.toml"
    if (FileUtils.exists(buildScript)) () else {
      logger.withContext(buildScript).info(s"Initialized empty build script for $name")
      Files.writeString(buildScript, template(libName))

    }
  }

  def template(libName: String) =
    s"""[package]
      |name = "${libName}"
      |version = "0.1.0"
      |authors = ["John Doe <john.doe@gmail.com>"]
      |edition = "2018"
      |
      |[dependencies]
      |jni = "0.19"
      |
      |[lib]
      |crate_type = ["cdylib"]
      |""".stripMargin

  def getInstance(baseDirectory: Path, buildDirectory: Path, logger: Logger): Instance =
    new Instance(baseDirectory, logger)

  class Instance(protected val baseDirectory: Path, protected val logger: Logger) extends BuildTool.Instance {
    val cliLogger = cli.CliLogger(logger)

    def clean(): Unit =
      cli("cargo clean", baseDirectory, List("cargo", "clean"), cliLogger)

    def library(targetDirectory: Path): Path = {
      cli(
        "cargo build",
        baseDirectory,
        List[Option[String]](
          Some("cargo"),
          Some("build"),
          if (release) Some("--release") else None,
          Some("--target-dir"),
          Some(targetDirectory.toString)
        ).flatten,
        cliLogger
      )

      val subdir = if (release) "release" else "debug"
      val products: List[Path] =
        Files
          .walk(targetDirectory.resolve(subdir))
          .filter(Files.isRegularFile(_))
          .filter { p =>
            val fileName = p.getFileName.toString
            fileName.endsWith(".so") || fileName.endsWith(".dylib")
          }
          .iterator()
          .asScala
          .toList

      // only one produced library is expected
      products match {
        case Nil =>
          sys.error(
            s"No files were created during compilation, " +
              s"something went wrong with the $name configuration."
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
