FROM bellsoft/liberica-runtime-container:jdk-all-25-musl@sha256:7d8158026556c15fa87d285f27c8f42e9fd8d4a8b29950bebbe03160ed11d0be AS build

RUN apk add --no-cache bash curl \
    && curl -fsSL https://github.com/clojure/brew-install/releases/download/1.12.6.1673/linux-install.sh -o /tmp/linux-install.sh \
    && bash /tmp/linux-install.sh \
    && rm /tmp/linux-install.sh

WORKDIR /build

COPY deps.edn build.clj ./
COPY build build
RUN clojure -P && clojure -P -T:build

COPY src/penpot src/penpot
COPY resources resources
RUN clojure -T:build uber

RUN jlink --add-modules java.base,java.desktop,java.management,java.naming,java.net.http,java.security.jgss,java.sql,jdk.unsupported \
    --strip-debug --no-header-files --no-man-pages --compress zip-6 --output /jre

FROM bellsoft/alpaquita-linux-base:stream-musl@sha256:9c31d60aa6d12a472039d9486c6f7123f7d49a80265bcce0140b4609b0020813

RUN adduser -S -H -s /sbin/nologin penpot-mcp \
    && mkdir -p /var/spool/penpot-mcp \
    && chown penpot-mcp /var/spool/penpot-mcp

COPY --from=build /jre /opt/jre
COPY --from=build /build/target/penpot-mcp.jar /opt/penpot-mcp/penpot-mcp.jar

ENV PATH=/opt/jre/bin:$PATH \
    MCP_HOST=0.0.0.0 \
    MCP_PORT=4401 \
    WS_HOST=0.0.0.0 \
    WS_PORT=4402

EXPOSE 4401 4402

USER penpot-mcp

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -q -S -O /dev/null "http://127.0.0.1:${MCP_PORT}/mcp" 2>&1 | grep -q " 401 " || exit 1

CMD ["java", "-XX:+UseG1GC", "-XX:G1PeriodicGCInterval=30000", "-XX:MaxRAMPercentage=40", "-XX:MinHeapFreeRatio=10", "-XX:MaxHeapFreeRatio=30", "-XX:+UseCompactObjectHeaders", "-XX:TieredStopAtLevel=1", "-jar", "/opt/penpot-mcp/penpot-mcp.jar"]
