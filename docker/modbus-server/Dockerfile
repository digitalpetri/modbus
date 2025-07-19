# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17 AS builder

# Set working directory inside the container
WORKDIR /app

# Copy the project files
COPY . .

# See https://bugs.openjdk.org/browse/JDK-834529
ARG TARGETPLATFORM
ARG ENV_JTO=${TARGETPLATFORM/linux\/arm64/-XX:UseSVE=0}
ARG ENV_JTO=${ENV_JTO/$TARGETPLATFORM/}

RUN echo "ENV_JTO is set to: $ENV_JTO"
ENV JAVA_TOOL_OPTIONS=$ENV_JTO

# Build the application using Maven
RUN mvn clean package

# Stage 2: Run the application using a minimal Java runtime image
FROM bellsoft/liberica-openjdk-alpine:17 AS runtime

# Set working directory inside the container
WORKDIR /app

# Copy the compiled JAR from the build stage
COPY --from=builder /app/target/modbus-server.jar modbus-server.jar

# See https://bugs.openjdk.org/browse/JDK-834529
ARG TARGETPLATFORM
ARG ENV_JTO=${TARGETPLATFORM/linux\/arm64/-XX:UseSVE=0}
ARG ENV_JTO=${ENV_JTO/$TARGETPLATFORM/}

RUN echo "ENV_JTO is set to: $ENV_JTO"
ENV JAVA_TOOL_OPTIONS="$ENV_JTO"

# Define the entry point to run server
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar modbus-server.jar"]
