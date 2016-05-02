ivyLoggingLevel := UpdateLogging.Quiet

lazy val root = (project in file(".")).
  aggregate(core, native)

lazy val core = (project in file("core")).
  settings(target in javah := (sourceDirectory in nativeCompile in native).value / "include").
  dependsOn(native % Runtime)

lazy val native = (project in file("native")).
  settings(sourceDirectory in nativeCompile := sourceDirectory.value).
  enablePlugins(JniNative)
