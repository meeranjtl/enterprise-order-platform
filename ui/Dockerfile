# syntax=docker/dockerfile:1
# Build stage
FROM node:22-alpine AS builder
WORKDIR /app

# Layer 1 — lockfile only, so dependency install stays cached until it changes.
COPY ui/package.json ui/package-lock.json* ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci

# Layer 2 — sources.
COPY ui/ ./

# Baked in at build time — Vite inlines env vars into the static bundle, so
# this can't be swapped at container-run time like a JVM service's env vars.
# Default matches every other dev/docker-compose setup in this repo: the
# browser (not this container) calls the gateway, and the gateway is always
# published on the host at :8080 regardless of which container it runs in.
ARG VITE_API_URL=http://localhost:8080
ENV VITE_API_URL=$VITE_API_URL

RUN npm run build

# Runtime stage — nginx serving the static build, SPA fallback via nginx.conf.
# nginx-unprivileged (not the stock image) so this container runs non-root
# like every other service's Dockerfile, without needing extra capabilities
# to bind port 3000.
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY ui/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 3000

# 127.0.0.1, not localhost: this image resolves "localhost" to ::1 (IPv6)
# only, while nginx (see nginx.conf) binds 0.0.0.0 (IPv4) — wget against
# "localhost" gets connection-refused even though the server is healthy.
HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=20s \
    CMD wget -qO- http://127.0.0.1:3000/ || exit 1
