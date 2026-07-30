# Multi-stage build (docs/contexto-materia.md seccion 17.4), ajustado a
# Gradle Groovy DSL (build.gradle, no .kts) y a Java 17. La misma imagen
# sirve para local (docker run --env-file .env), Ubuntu/systemd o Render
# (docs/contexto-materia.md seccion 17.8): el ambiente se selecciona 100%
# por variables de entorno, nunca reconstruyendo la imagen.

# ===================== ETAPA 1: build (JDK) =====================
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /workspace/app

COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

COPY src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar -x test --no-daemon

# Separa BOOT-INF/lib (dependencias, cambian poco) de BOOT-INF/classes
# (código, cambia seguido): permite a Docker reutilizar capas cuando solo
# cambia el código de la app, no sus dependencias.
RUN mkdir -p build/dependency && cd build/dependency && jar -xf ../libs/app.jar

# ===================== ETAPA 2: runtime (JRE) =====================
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# wget solo para el HEALTHCHECK de Docker de más abajo (para `docker run`/
# Compose sueltos; en Render el healthCheckPath de render.yaml ya cubre esto
# a nivel de plataforma).
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system spring && useradd --system --gid spring spring

ARG DEPENDENCY=/workspace/app/build/dependency
COPY --from=builder --chown=spring:spring ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --from=builder --chown=spring:spring ${DEPENDENCY}/META-INF /app/META-INF
COPY --from=builder --chown=spring:spring ${DEPENDENCY}/BOOT-INF/classes /app

USER spring:spring

# Documental: el puerto real lo decide server.port=${PORT:8080} (Render
# inyecta PORT); EXPOSE no lo fija ni lo restringe.
EXPOSE 8080

# El contenedor NO escribe archivos permanentes: no hay VOLUME declarado ni
# directorio de escritura montado. Los reportes (PDF/Excel — prompts 17-18)
# se generan 100% en memoria (ByteArrayOutputStream) y se devuelven en la
# respuesta HTTP; nunca se guardan en disco.
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- "http://localhost:${PORT:-8080}/api/actuator/health" || exit 1

# Sin -Xms/-Xmx hardcodeados a propósito: los límites de memoria de la JVM
# vienen de JAVA_TOOL_OPTIONS (variable de entorno — ver render.yaml/.env),
# así se pueden ajustar según el plan de Render sin reconstruir la imagen.
ENTRYPOINT ["java", "-cp", "/app:/app/lib/*", "ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.AcademicEventsApiApplication"]
