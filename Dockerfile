# =============================================================================
# MCP Sessionize Server - Quarkus Native Multi-stage Build
# =============================================================================
# Builds a native executable using Mandrel (Red Hat's GraalVM distribution)
# and deploys on the smallest possible image (ubi9-quarkus-micro-image)
#
# Build:
#   docker build -t ghcr.io/jeanlopezxyz/mcp-sessionize .
#
# Run:
#   docker run -i --rm -p 8080:8080 -e SESSIONIZE_EVENT_ID=xxx ghcr.io/jeanlopezxyz/mcp-sessionize
#
# Image size: ~50-100MB (vs ~400MB with JVM)
# Startup time: ~50ms (vs ~2s with JVM)
# =============================================================================

# Stage 1: Build Native Executable
FROM quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25 AS build

WORKDIR /build

# Copy all source files
COPY --chown=quarkus:quarkus mvnw .
COPY --chown=quarkus:quarkus .mvn .mvn
COPY --chown=quarkus:quarkus pom.xml .
COPY --chown=quarkus:quarkus src src

# Build native executable (single step to avoid permission issues)
USER quarkus
RUN ./mvnw package -DskipTests -Dnative -B

# Stage 2: Runtime (Micro Image - smallest possible)
FROM quay.io/quarkus/ubi9-quarkus-micro-image:2.0

LABEL maintainer="Jean Lopez"
LABEL description="MCP Server for Sessionize (Native)"
LABEL io.k8s.display-name="MCP Sessionize Server"
LABEL io.openshift.tags="mcp,sessionize,conferences,speakers,sessions,quarkus,native"

WORKDIR /work/

# Setup permissions
RUN chown 1001 /work \
    && chmod "g+rwX" /work \
    && chown 1001:root /work

# Copy native executable from build stage
COPY --from=build --chown=1001:root --chmod=0755 /build/target/*-runner /work/application

EXPOSE 8080

USER 1001

# Environment variables for MCP HTTP transports
ENV QUARKUS_HTTP_HOST=0.0.0.0
ENV QUARKUS_HTTP_PORT=8080
ENV SESSIONIZE_EVENT_ID=

# MCP Transport Configuration
# Streamable HTTP: http://localhost:8080/mcp
# SSE: http://localhost:8080/mcp/sse
ENV QUARKUS_MCP_SERVER_HTTP_ROOT_PATH=/mcp
ENV QUARKUS_MCP_SERVER_STDIO_ENABLED=false

# CORS for Streamable HTTP
ENV QUARKUS_HTTP_CORS=true
ENV QUARKUS_HTTP_CORS_ORIGINS=*

ENTRYPOINT ["./application"]
