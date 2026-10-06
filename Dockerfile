FROM clojure:temurin-25-tools-deps-1.12.6.1673-trixie-slim@sha256:c36d56a5ae0bfda66847f3d0a0641f7b79ce2b90c9b745009a9a0bb57fafe384 AS build

RUN apt-get update \
    && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /build

COPY deps.edn build.clj ./
RUN clojure -P && clojure -T:build compile-common

COPY src/penpot src/penpot
RUN clojure -T:build uber

FROM eclipse-temurin:25-jre@sha256:15090d159279e5c158473eccb48cd87f57b3e3a47511a797eb5a7a7ea6f86b0f

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --no-create-home --shell /usr/sbin/nologin penpot-mcp \
    && mkdir -p /var/spool/penpot-mcp \
    && chown penpot-mcp /var/spool/penpot-mcp

COPY --from=build /build/target/penpot-mcp.jar /opt/penpot-mcp/penpot-mcp.jar

ENV MCP_HOST=0.0.0.0 \
    MALLOC_ARENA_MAX=2 \
    MCP_PORT=4401 \
    WS_HOST=0.0.0.0 \
    WS_PORT=4402

EXPOSE 4401 4402

USER penpot-mcp

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD [ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${MCP_PORT}/mcp")" = "401" ] || exit 1

CMD ["java", "-XX:MaxRAMPercentage=40", "-XX:MinHeapFreeRatio=10", "-XX:MaxHeapFreeRatio=30", "-XX:+UseCompactObjectHeaders", "-XX:TieredStopAtLevel=1", "-jar", "/opt/penpot-mcp/penpot-mcp.jar"]
