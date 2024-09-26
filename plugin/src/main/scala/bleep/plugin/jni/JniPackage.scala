package bleep
package plugin.jni

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Packages libraries built with JniNative.
  */
class JniPackage(
    val baseDirectory: Path,
    val jniNative: JniNative
) {

  // Unmanaged directories containing native libraries. The libraries must be regular files contained in a subdirectory corresponding to a platform. For example `<unmanagedNativeDirectory>/x86_64-linux/libfoo.so` is an unmanaged library for machines having the x86_64 architecture and running the Linux kernel.
  lazy val unmanagedNativeDirectories: Seq[Path] =
    Seq(baseDirectory / "lib_native")

  // Unmanaged directories containing native libraries. The libraries must be regular files contained in a subdirectory corresponding to a platform. For example `<unmanagedNativeDirectory>/x86_64-linux/libfoo.so` is an unmanaged library for machines having the x86_64 architecture and running the Linux kernel.
  lazy val unmanagedPlatformDependentNativeDirectories: Seq[(String, Path)] =
    Seq(jniNative.nativePlatform -> baseDirectory / "lib_native")

  // Reads `unmanagedNativeDirectories` and maps libraries to their locations on the classpath (i.e. their path in a fat jar).
  lazy val unmanagedNativeLibraries: Seq[(Path, RelPath)] = {
    val mappings: Seq[(Path, RelPath)] =
      unmanagedNativeDirectories.flatMap { dir =>
        regularFilesUnder(dir).map { p =>
          val relative0 = RelPath.relativeTo(dir, p)
          val relative1 = relative0.prefixed("native")
          (p, relative1)
        }
      }

    val mappingsPlatform: Seq[(Path, RelPath)] =
      unmanagedPlatformDependentNativeDirectories.flatMap { case (platform, dir) =>
        regularFilesUnder(dir).map { p =>
          val relative0 = RelPath.relativeTo(dir, p)
          val relative1 = relative0.prefixed(s"native/$platform")
          (p, relative1)
        }
      }
    mappings ++ mappingsPlatform
  }

  private def regularFilesUnder(dir: Path): Iterator[Path] =
    if (Files.exists(dir)) Files.walk(dir).filter(Files.isRegularFile(_)).iterator().asScala
    else Iterator.empty

  // Maps locally built, platform-dependant libraries to their locations on the classpath.
  lazy val managedNativeLibraries: Seq[(Path, RelPath)] = {
    val library: Path = jniNative.nativeCompile()
    val relPath = RelPath.of("native", jniNative.nativePlatform, System.mapLibraryName(jniNative.libName))
    Seq(library -> relPath)
  }

  // All native libraries, managed and unmanaged.
  lazy val nativeLibraries: Seq[(Path, RelPath)] =
    distinctBy(unmanagedNativeLibraries ++ managedNativeLibraries)(_._2)

  def copyTo(resourceManaged: Path): Seq[Path] =
    nativeLibraries.map { case (file, relPath) =>
      // native library as a managed resource file
      val resource = resourceManaged / relPath

      // copy native library to a managed resource, so that it is always available
      // on the classpath, even when not packaged as a jar
      Files.createDirectories(resource.getParent)
      Files.write(resource, Files.readAllBytes(file))
      resource
    }

  // compat between 2.12 and 2.13
  private def distinctBy[A, B](seq: Seq[A])(f: A => B): Seq[A] = {
    val builder = Seq.newBuilder[A]
    val i = seq.iterator
    var set = Set[B]()
    while (i.hasNext) {
      val o = i.next()
      val b = f(o)
      if (!set(b)) {
        set += b
        builder += o
      }
    }
    builder.result()
  }

}
