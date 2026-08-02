# ═══════════════════════════════════════════════════════════
# Stage 1 — build
# Uses Maven + JDK to compile and package the app into a jar.
# This entire stage is discarded from the final image — none of
# Maven, the JDK's compiler, or the source code itself ships in
# what actually runs. Only the resulting jar moves to Stage 2.
# ═══════════════════════════════════════════════════════════
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy ONLY pom.xml first, not the source code yet. This lets Docker
# cache the dependency-download layer separately from your source —
# so long as pom.xml doesn't change, rebuilding after a code change
# reuses this layer instead of re-downloading every dependency again.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Now copy the actual source and build. Tests run as part of this
# build (not skipped) — the image only gets built at all if all 31
# tests still pass, giving Docker itself a safety net for free.
COPY src ./src
RUN mvn clean package

# ═══════════════════════════════════════════════════════════
# Stage 2 — runtime
# A minimal image with ONLY a JRE (no compiler, no Maven, no
# source) plus the one jar copied over from the build stage.
# This is why multi-stage builds matter: the final image is a
# fraction of the size of the build environment that produced it.
#
# Debian-based, deliberately NOT Alpine — sqlite-jdbc loads a
# native library (visible in every startup log as the
# "System::load ... SQLiteJDBCLoader" warning), and native
# libraries built for glibc frequently fail to load on Alpine's
# musl libc. Staying on glibc avoids that failure entirely.
# ═══════════════════════════════════════════════════════════
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/myfitness.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]