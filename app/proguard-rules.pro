# Readable stack traces: a crashing home app silently loses its default status, so traces matter.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization needs no rules here: kotlinx-serialization-core 1.11.0 ships its own
# (META-INF/com.android.tools/r8/kotlinx-serialization-{common,r8}.pro) and R8 merges them in.
# They cover the sealed Action hierarchy and are kept current by the library; hand-written copies
# only go stale. Check them with ./gradlew analyzeReleaseR8Config if a decode ever fails in release.
