package bleep.plugin.jni

import ryddig.Logger

import java.nio.file.Path

trait BuildTool {

  /** Name of this build tool.
    */
  def name: String

  def ensureHasBuildFile(sourceDirectory: Path, logger: Logger, libName: String): Unit

  /** Get an instance (build configuration) of this tool, in the specified directory.
    */
  def getInstance(baseDirectory: Path, buildDirectory: Path, logger: Logger, env: List[(String, String)]): BuildTool.Instance
}

object BuildTool {

  /** Actual tasks that can be perfomed on a specific configuration, such as configured in a Makefile.
    */
  trait Instance {

    /** Invokes the native build tool's clean task
      */
    def clean(): Unit

    /** Invokes the native build tool's main task, resulting in a single shared library file.
      *
      * @param baseDirectory
      *   the directory where the native project is located
      * @param buildDirectory
      *   a directory from where the build is called, it may be used to store temporary files
      * @param targetDirectory
      *   the directory into which the native library is copied
      * @return
      *   the native library file
      */
    def library(targetDirectory: Path): Path
  }

}
