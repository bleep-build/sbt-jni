package bleep.plugin.jni

import bleep.logging.Logger
import bleep.{PathOps, ProjectPaths, fixedClasspath}
import bloop.config.Config

import java.io.File
import java.net.URI
import java.nio.file.{Path, Paths}
import scala.collection.JavaConverters._

/**
 * Adds `javah` header-generation functionality to projects.
 */
class JniJavah(logger: Logger, projectPaths: ProjectPaths, bloopProject: Config.Project) {
  lazy val targetDir: Path = projectPaths.targetDir
  val javahTarget = targetDir / "native" / "include"

  // Finds fully qualified names of classes containing native declarations.
  def javahClasses(): Set[String] = {
    import xsbti.compile._

    val compiled: CompileAnalysis =
      FileAnalysisStore.getDefault(projectPaths.incrementalAnalysis.toFile).get().get().getAnalysis

    val classFiles: Set[File] = compiled
      .readStamps()
      .getAllProductStamps
      .asScala
      .keySet
      .map { vf =>
        (vf.names() match {
          case Array(prefix, first, more@_*) if prefix.startsWith("${") =>
            Paths.get(first, more: _*)
          case _ =>
            Paths.get(URI.create("file:///" + vf.id().stripPrefix("/")))
        }).toFile
      }
      .toSet
    val nativeClasses = classFiles.flatMap { file =>
      BytecodeUtil.nativeClasses(file)
    }
    nativeClasses
  }


  // Generate JNI headers. Returns the directory containing generated headers.
  def javah(): Path = {
      val out = javahTarget

      val task = new com.github.sbt.jni.javah.JavahTask

      val log = logger

      val classes = javahClasses()
      if (classes.nonEmpty) {
        log.info("Headers will be generated to " + out)
      }
      classes.foreach(task.addClass)

      // fullClasspath can't be used here since it also generates resources. In
      // a project combining JniJavah and JniPackage, we would have a chicken-and-egg
      // problem.
      fixedClasspath(bloopProject).foreach(task.addClassPath)

      task.addRuntimeSearchPath()
      task.setOutputDir(out)
      task.run()

      out
    }
}
