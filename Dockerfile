# Distributed API Gateway — runtime image
#
# The jar is built on the host (`mvn package`) and copied in, rather than compiled inside the
# image. The repository's build already produces an executable Spring Boot jar, and a build stage
# would re-resolve every Maven dependency over the network on each image build — slower, and it
# fails outright in restricted or offline environments. Nothing about the application requires a
# compiler at image build time.
#
# Build:
#   mvn package -DskipTests
#   docker build -t distributed-api-gateway:1.0.0-SNAPSHOT .
#
# The image carries no Gateway identity of its own: one image runs every instance in the cluster,
# and identity arrives through the environment (see GATEWAY_INSTANCE_ID below). That is what lets
# the cluster scale by adding containers rather than by changing code.

FROM eclipse-temurin:21-jre-alpine

# No packages are installed: the health check uses the BusyBox wget already present in the base
# image, so the build needs no network access and the image gains no extra layers.
RUN addgroup -S gateway \
 && adduser -S -G gateway gateway

WORKDIR /app

ARG JAR_FILE=target/distributed-api-gateway-*.jar
COPY ${JAR_FILE} /app/gateway.jar

# Runs unprivileged: the Gateway needs no root capability, and a compromised container should not
# hold one.
USER gateway

EXPOSE 8080

# Identity and Redis location are supplied per container. The defaults keep a bare
# `docker run` usable for a single instance against a local Redis.
ENV GATEWAY_INSTANCE_ID=gateway-local \
    REDIS_HOST=redis \
    REDIS_PORT=6379 \
    JAVA_OPTS=""

# Container-level liveness only — "is this instance serving?" — which is what the orchestrator
# needs to route traffic away from a failed node. Application-level cluster health aggregation is
# a separate concern and is deliberately not part of this image.
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/health > /dev/null 2>&1 || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/gateway.jar"]
