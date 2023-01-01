package bleep.plugin.jni

import bleep.logging.Logger
import bleep.{fixedClasspath, PathOps, ProjectPaths}
import bloop.config.Config

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

/** Adds `javah` header-generation functionality to projects.
  */
class JniJavah(logger: Logger, projectPaths: ProjectPaths, bloopProject: Config.Project) {
  lazy val targetDir: Path = projectPaths.targetDir
  val javahTarget = targetDir / "native" / "include"

  // Finds fully qualified names of classes containing native declarations.
  def javahClasses(): Set[String] =
    Files
      .walk(projectPaths.targetDir)
      .filter(_.getFileName.toString.endsWith(".class"))
      .iterator()
      .asScala
      .flatMap(p => BytecodeUtil.nativeClasses(p.toFile))
      .toSet

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
