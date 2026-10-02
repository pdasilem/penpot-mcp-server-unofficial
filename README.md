# Penpot MCP Server

MCP server for self-hosted [Penpot](https://penpot.app) that gives AI agents typed tools instead of a JavaScript runner: every tool has a fixed input schema, and agents cannot execute their own code in the editor.

It runs inside the Penpot stack in place of the official `penpot-mcp` service, or as a separate container that Penpot points to. In both cases Penpot's bundled MCP plugin connects to this server instead of the official one.

A release `X.Y.Z.N` is built for Penpot `X.Y.Z` and works only with that Penpot version: on any other version every tool returns an error asking to update the server. See [Versions](#versions).

## How it works

| Channel | Used for |
|---|---|
| Penpot RPC API | Reading files, shapes, libraries, design tokens, comments, versions; projects, files, pages, comments, snapshots, media |
| Penpot notifications WebSocket | Users currently in a file |
| Penpot's bundled MCP plugin | Every change in the editor: shapes, layout, text, styles, components and variants, library colors and typographies, design tokens, token sets and themes; image export |

- Penpot's nginx proxies `/mcp/stream` (MCP clients) and `/mcp/ws` (the bundled plugin) to the server, so TLS and the public address come from Penpot.
- Canvas tools run in the Penpot editor. The file must be open in a browser tab with MCP enabled; when a shape is on another page the editor switches to it.
- A read issued after canvas changes returns the saved state: the server waits until the editor has saved the changes to Penpot.
- Shapes are created and laid out by the editor itself, so flex and grid layouts, text measurement and token values behave exactly as in Penpot.
- The server checks the Penpot version at start and periodically. Any other Penpot version blocks every tool with an error.

### Access model

The server holds the access token and the password of one Penpot account. Read tools and the tools for projects, files, pages, comments, versions and media act as that account through the Penpot API and work without an open editor. Canvas tools act through the bundled plugin in the editor tab that has the file open with MCP enabled. The official Penpot MCP server holds no credentials and works only through the plugin.

The full tool reference is in [TOOLS.md](TOOLS.md).

## Penpot preparation

1. Enable the flags `enable-mcp` and `enable-access-tokens` in Penpot (`PENPOT_FLAGS` for the frontend and backend containers, or `config.flags` in the Helm chart).
2. Sign in with the Penpot account the agent will work as and open *Settings → Integrations*:
   - create an access token;
   - in the *MCP Server* section create an MCP key and turn MCP on.
3. Keep the account email and password: they are used for the presence tool, which needs a Penpot session.

## Installation

The server is a single container listening on port 4401 (MCP) and 4402 (plugin). Run exactly one instance: plugin connections and pending-save tracking live in memory.

### Instead of the official MCP: Docker Compose

1. Clone the release for your Penpot version next to your Penpot `docker-compose.yaml`:

   ```bash
   git clone --branch v<version> https://github.com/pdasilem/penpot-mcp-server-unofficial.git
   ```

2. Run `./penpot-mcp-server-unofficial/setup.sh`. It writes `penpot-mcp-server-unofficial/.env` and pulls the image of the release. To do it by hand, copy `.env.example` to `.env`, fill it in and pull the image `ghcr.io/pdasilem/penpot-mcp-server-unofficial:<version>`.
3. In your Penpot compose file, replace the `penpot-mcp` service with the one from [docker-compose.penpot.yml](docker-compose.penpot.yml). Keep the service name `penpot-mcp`: Penpot's nginx proxies to `http://penpot-mcp:4401` and `http://penpot-mcp:4402` by default.
4. Make sure `PENPOT_FLAGS` contains `enable-mcp` and `enable-access-tokens`, then:

   ```bash
   docker compose up -d penpot-mcp penpot-frontend
   ```

### Instead of the official MCP: Helm

Create a secret with the credentials:

```bash
kubectl -n penpot create secret generic penpot-mcp \
  --from-literal=access-token=<access token> \
  --from-literal=email=<email> \
  --from-literal=password=<password> \
  --from-literal=mcp-key=<MCP key>
```

Override the MCP component of the [Penpot chart](https://github.com/penpot/penpot-helm):

```yaml
config:
  flags: "enable-login-with-password enable-access-tokens enable-mcp"
mcp:
  image:
    repository: ghcr.io/pdasilem/penpot-mcp-server-unofficial
    tag: "<version>"
  replicaCount: 1
  autoscaling:
    hpa:
      enabled: false
  extraEnvs:
    - name: PENPOT_BASE_URL
      value: "http://<frontend service>.<namespace>.svc.cluster.local:8080"
    - name: PENPOT_ACCESS_TOKEN
      valueFrom: {secretKeyRef: {name: penpot-mcp, key: access-token}}
    - name: PENPOT_EMAIL
      valueFrom: {secretKeyRef: {name: penpot-mcp, key: email}}
    - name: PENPOT_PASSWORD
      valueFrom: {secretKeyRef: {name: penpot-mcp, key: password}}
    - name: PENPOT_MCP_KEY
      valueFrom: {secretKeyRef: {name: penpot-mcp, key: mcp-key}}
```

`<frontend service>` is the chart's full name: the release name when it contains `penpot`, otherwise `<release>-penpot`, or `fullnameOverride` when set. Keep `mcp.service.httpPort` 4401 and `mcp.service.wsPort` 4402, or set `MCP_PORT` and `WS_PORT` in `extraEnvs` to the same values. The chart already points the frontend at the `-mcp` service.

### Standalone (Docker)

Run the server outside the Penpot stack, for example on another host, and point Penpot at it.

1. Start the container with the variables from [Configuration](#configuration) and publish ports 4401 and 4402 only on an address the Penpot frontend reaches, never on a public interface:

   ```bash
   docker run -d --name penpot-mcp --env-file .env \
     -p <private address>:4401:4401 -p <private address>:4402:4402 ghcr.io/pdasilem/penpot-mcp-server-unofficial:<version>
   ```

   `PENPOT_BASE_URL` must be a Penpot address reachable from this container.
2. On the Penpot frontend container set `enable-mcp` in `PENPOT_FLAGS` and:

   ```
   PENPOT_MCP_URI=http://<mcp host>:4401
   PENPOT_MCP_URI_WS=http://<mcp host>:4402
   ```

   Penpot's nginx resolves `<mcp host>` through its DNS resolver: use a DNS name or an IP address, not an `/etc/hosts` alias such as `host.docker.internal`.
3. Do not run the official `penpot-mcp` service.

With the Helm chart use the replacement above: with `enable-mcp` the chart deploys its own MCP service and points the frontend at it.

## Connecting an agent

The MCP endpoint is Penpot's public address plus `/mcp/stream`, authenticated by the MCP key:

```
https://penpot.example.com/mcp/stream?userToken=<MCP key>
```

Claude Code:

```bash
claude mcp add --transport http penpot "https://penpot.example.com/mcp/stream?userToken=<MCP key>"
```

Claude Code plugin with the server connection and the `penpot` skill:

```bash
export PENPOT_MCP_URL=https://penpot.example.com/mcp/stream
export PENPOT_MCP_KEY=<MCP key>
claude plugin marketplace add pdasilem/penpot-mcp-server-unofficial
claude plugin install penpot@penpot-mcp
```

Other clients: configure a Streamable HTTP MCP server with the same URL.

Tools are grouped into `read`, `edit`, `manage` (projects, files, versions, webhooks) and `export`. `PENPOT_MCP_TOOLSETS` sets the groups enabled at start; agents switch groups with `list_toolsets` and `set_toolset`. A switch applies to every session of the server until it restarts.

## Reverse proxy in front of Penpot

The MCP key travels in the URL, as Penpot's own MCP integration requires. Penpot's nginx writes request URLs, including the key, to the frontend container log; restrict access to that log. In the proxy that terminates TLS in front of Penpot, log `/mcp/` requests without the query string and limit the request rate:

```nginx
log_format penpot_mcp '$remote_addr [$time_local] "$request_method $uri $server_protocol" $status $body_bytes_sent';
limit_req_zone $binary_remote_addr zone=penpot_mcp:10m rate=10r/s;

location /mcp/ {
    access_log /var/log/nginx/penpot-mcp.log penpot_mcp;
    limit_req zone=penpot_mcp burst=40 nodelay;
    proxy_pass http://<penpot frontend>;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 3600s;
}
```

## Configuration

| Variable | Required | Default | Description |
|---|---|---|---|
| `PENPOT_BASE_URL` | yes | | Penpot frontend URL reachable from the server, e.g. `http://penpot-frontend:8080` |
| `PENPOT_ACCESS_TOKEN` | yes | | Access token for the RPC API |
| `PENPOT_EMAIL` | yes | | Account email, for the presence tool |
| `PENPOT_PASSWORD` | yes | | Account password, for the presence tool |
| `PENPOT_MCP_KEY` | yes | | MCP key; clients and the plugin must present it as `userToken` |
| `MCP_HOST` | no | `127.0.0.1` (`0.0.0.0` in the image) | MCP listen address |
| `MCP_PORT` | no | `4401` | MCP port, path `/mcp` |
| `WS_HOST` | no | `127.0.0.1` (`0.0.0.0` in the image) | Plugin WebSocket listen address |
| `WS_PORT` | no | `4402` | Plugin WebSocket port, path `/mcp/ws` |
| `VERSION_CHECK_INTERVAL` | no | `300` | Seconds between Penpot version checks |
| `LOG_LEVEL` | no | `info` | `trace`, `debug`, `info`, `warn`, `error` |
| `PENPOT_MCP_TOOLSETS` | no | `read,edit` | Tool groups enabled at start: `read`, `edit`, `manage`, `export`; `read` is always enabled |

## Versions

The server version is the supported Penpot version plus a fourth number for fixes of this server: server `X.Y.Z.N` works with Penpot `X.Y.Z`. `<version>` in this document is that server version: take the latest [release](https://github.com/pdasilem/penpot-mcp-server-unofficial/releases) whose first three numbers match your Penpot version.

Releases are tagged `v<version>`; the image `ghcr.io/pdasilem/penpot-mcp-server-unofficial:<version>` and the Claude Code plugin carry the same version. To build the image from source instead: `docker build -t ghcr.io/pdasilem/penpot-mcp-server-unofficial:<version> .`

## Upgrading Penpot

The server is built against one Penpot version. After upgrading Penpot, update the server:

1. Change the tag and sha of `penpot/common` in `deps.edn`, and in `src/penpot/mcp/penpot/version.clj` set `supported` to the new Penpot version and `fix-release` to 0.
2. Set the new server version as the image tag in `docker-compose.penpot.yml` and as `version` in `claude-plugin/.claude-plugin/plugin.json`; the unit tests check both.
3. Run the unit tests. The token table test compares `src/penpot/mcp/tools/token_rules.clj` with the token properties of the new Penpot frontend; update the table when it fails.
4. Run the integration tests against the new Penpot version.
5. Compare the bundled plugin protocol (`mcp/packages/common/src/types.ts` in the Penpot repository) and the Plugin API methods the tools use with the new version.

## Development

Requirements: JDK 25, Clojure CLI, Docker; Node.js for the editor tests.

```bash
clojure -T:build compile-common
clojure -M:test unit
clojure -T:build uber
docker build -t penpot-mcp:dev .
```

Integration tests run against a Penpot instance of the supported version with this server deployed as its `penpot-mcp` service:

```bash
(cd test/e2e && npm install)
PENPOT_IT_ENV=/path/to/test.env clojure -M:test integration
```

`test.env` holds `PENPOT_ACCESS_TOKEN`, `PENPOT_EMAIL`, `PENPOT_PASSWORD` and `PENPOT_MCP_KEY` of a test account with MCP enabled. Optional: `PENPOT_IT_BASE_URL` (RPC, default `http://127.0.0.1:9001`), `PENPOT_IT_PUBLIC_URL` (browser, default `http://localhost:9001`), `PENPOT_IT_MCP_URL` (MCP endpoint, default `http://localhost:9001/mcp/stream`), `CHROME` (browser executable).

Lint and format: `clj-kondo --lint src test build.clj`, `clojure -M:fmt check src test build.clj`.

## License

[Mozilla Public License 2.0](LICENSE), the same as Penpot.
