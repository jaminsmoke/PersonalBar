# Ktor (`IntellijIdeaDebugDetector`) referencia `java.lang.management`, que no
# existe en Android. Sin esto, `minifyReleaseWithR8` falla y CodeQL autobuild
# no llega a analizar.
-dontwarn java.lang.management.**
