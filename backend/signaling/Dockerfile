FROM node:18-alpine AS base

WORKDIR /app

# dumb-init: PID 1 signal handling; su-exec: runtime privilege drop after volume chown
RUN apk add --no-cache dumb-init su-exec

# Copy package files first for better layer caching
COPY package.json package-lock.json* ./

# Production dependencies only
RUN npm ci --omit=dev --ignore-scripts && npm cache clean --force

# Copy application code
COPY src/ ./src/

# Copy seed data (activation codes, etc.) — runtime writes go to volume
COPY data/ ./data/

# Non-root user for security
RUN addgroup -g 1001 -S securecall && \
    adduser -S securecall -u 1001 -G securecall

# Seed data owned by securecall in the image layer.
# entrypoint.sh re-chowns at runtime to handle Railway volume mounts (root-owned).
RUN chown -R securecall:securecall /app/data

# Entrypoint runs as root, chowns volume, drops to securecall via su-exec
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Health check (runs after entrypoint drops to securecall)
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/ || exit 1

EXPOSE 8080

ENTRYPOINT ["/app/entrypoint.sh", "dumb-init", "--"]
CMD ["node", "src/server.js"]
