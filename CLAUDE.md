# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Qué es

Servidor MCP (Model Context Protocol) que expone datos de eventos de Sessionize (speakers, sesiones, agenda) como herramientas y prompts para asistentes IA. Construido con **Quarkus 3.33.2 + Java 25 + Quarkus MCP Server 1.13.0** (combo certificada por la doc oficial del MCP server). Se distribuye como ejecutable nativo (Mandrel) y vía `npx mcp-sessionize`.

## Comandos

```bash
./mvnw quarkus:dev          # Dev mode (HTTP habilitado, log DEBUG, live reload)
./mvnw verify               # Build + tests (lo que corre CI)
./mvnw test                 # Solo unit tests
./mvnw package -Dnative     # Ejecutable nativo (requiere Mandrel/GraalVM)
docker build -t mcp-sessionize .   # Imagen nativa multi-stage

# Un solo test / método
./mvnw test -Dtest=SessionizeToolTest
./mvnw test -Dtest=SessionizeToolTest#findSpeaker_returnsMatch
```

Usar siempre el wrapper `./mvnw`. CI (`.github/workflows/ci.yml`) ejecuta `./mvnw verify` con Java 25 (temurin) y, en push a `main`, publica la imagen a `ghcr.io`.

> **⚠️ `JAVA_HOME` local**: `mvnw` prioriza `JAVA_HOME` sobre el `java` del PATH. El proyecto **requiere Java 25** (`maven.compiler.release=25`, module imports + el flag `--sun-misc-unsafe-memory-access` de `.mvn/jvm.config`, que solo existe desde Java 23). Si `JAVA_HOME` apunta a Java ≤21 el build falla con `Unrecognized option`. Forzar el JDK correcto, p.ej.:
> ```bash
> JAVA_HOME="$HOME/.sdkman/candidates/java/current" ./mvnw verify   # con sdkman 25-graalce
> ```

## Arquitectura

Tres capas, todas en el paquete canónico **`io.mcp.sessionize`**:

- **`client/SessionizeClient`** — interfaz REST Client de MicroProfile (`@RegisterRestClient(configKey = "sessionize")`) contra `https://sessionize.com/api/v2/{eventId}/view/{Speakers|Sessions|GridSmart}`. Fuerza headers no-cache.
- **`model/SessionizeModel`** — clase sellada con `record`s inmutables anidados (`Speaker`, `Session`, `SessionGroup`, `GridDay`, etc.) que mapean el JSON de la API.
- **`tool/SessionizeTool`** — herramientas MCP (`@Tool`). Los métodos Java son `getSpeakers`, `findSpeaker`, `getSessionsBySpeaker`, `getSessions`, `findSession`, `getSchedule`, pero el **nombre MCP registrado** lleva prefijo de servicio vía `@Tool(name = "sessionize_…")` (evita colisiones con otros servers). Todas llevan `@Tool.Annotations(readOnlyHint=true, idempotentHint=true, openWorldHint=true)` — son de solo-lectura contra una API externa; mantener prefijo + hints al añadir tools. Las tools de listado (`get_speakers`, `find_speaker`, `get_sessions`, `find_session`) aceptan `limit`/`offset` y devuelven metadata de paginación vía el helper `paginate`.
- **`prompt/SessionizePrompt`** — plantillas MCP (`@Prompt`): `event_overview`, `find_speaker_info`, `sessions_by_topic`, `conference_schedule`, `speaker_sessions`, `recommend_sessions`.

Las clases `@Tool`/`@Prompt` **no llevan scope explícito**: Quarkus MCP Server les inyecta `@Singleton` automáticamente.

### Convenciones clave del código

- **Resolución de evento**: cada tool acepta `eventId` opcional; si viene vacío usa `sessionize.event-id` (env `SESSIONIZE_EVENT_ID`). `resolveEventId` + `sanitizeEventId` eliminan todo lo que no sea alfanumérico antes de llamar a la API — mantener esa sanitización al añadir tools.
- **Filtrado de sesiones**: `fetchAllSessions` aplana los `SessionGroup` y filtra a `Session::isConfirmed` y `!isServiceSession()` (descarta breaks/registro). Es comportamiento intencional (ver commits recientes).
- **Async**: las tools devuelven `Uni<ToolResponse>` y corren en `Infrastructure.getDefaultWorkerPool()` vía el helper `validateAndExecute`, que centraliza validación y manejo de errores (mapea status HTTP a mensajes amigables en `extractErrorMessage`).
- **Salida**: todas las respuestas se formatean como Markdown (`formatSpeaker`, `formatSession`, `formatSchedule`).
- **Java 25**: se usa `import module java.base;` (module import declarations, JEP 511 final en 25) en lugar de imports individuales de `java.util.*`.
- **Versiones MCP**: la versión de los artefactos `quarkus-mcp-server-*` la gestiona el BOM `io.quarkiverse.mcp:quarkus-mcp-server-bom` importado en `dependencyManagement` (propiedad `mcp.server.version`). Las dependencias MCP **no** llevan `<version>` propio.

### Transportes MCP (configurados en `application.properties`)

- **STDIO** activo por defecto (Claude Desktop / Claude Code). El transporte stdio ya redirige el logging de consola a **stderr** y pone stdout en null, así que los logs no corrompen el protocolo JSON-RPC. El perfil default usa `quarkus.log.level=WARN` + `quarkus.log.console.stderr=true` (banner off, `host-enabled=false`). **Nunca escribir a `System.out`** en código de tools/prompts: eso sí rompe STDIO.
- **Perfil `%sse`** (`--port 8080` o env equivalente): habilita HTTP en `/mcp` con CORS y logging.
- **Perfil `%dev`**: HTTP + logs DEBUG.
- La imagen Docker corre en modo HTTP (`QUARKUS_MCP_SERVER_STDIO_ENABLED=false`).

## Nota de paquetes (histórico)

El working tree tenía tres copias del código en paquetes distintos (`io.mcp.sessionize` versionado + `dev.cloudnativelima` e `io.github.mcp` sin trackear, experimentos viejos). Esas copias duplicaban los nombres de `@Tool`/`@Prompt` y **rompían el build**. Se eliminaron los dos paquetes sin trackear; **`io.mcp.sessionize` es el único y canónico**. El `groupId` del `pom.xml` sigue siendo `io.github.mcp` (coordenadas Maven, no tiene que coincidir con el paquete Java).
