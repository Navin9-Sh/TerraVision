# --- Build stage -------------------------------------------------------
# Builds the jar with Maven inside a throwaway container, so the final image
# never needs a JDK or Maven installed, only a slim JRE to run the built jar.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependency resolution separately from source changes: this layer only
# re-runs when pom.xml changes, not on every source edit.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

# --- Runtime stage -------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd --system --create-home appuser
USER appuser

COPY --from=build /build/target/backend-0.1.0.jar app.jar

# The TorchScript model + classes.json produced by model-export/export_model.py.
# Baked into the image here for simplicity (single artifact to deploy, no
# extra volume/object-storage wiring); see ARCHITECTURE.md for the trade-off
# against mounting it as an external volume instead.
COPY model ./model

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
