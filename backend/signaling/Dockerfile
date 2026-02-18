FROM node:18-alpine AS base

WORKDIR /app

# Install dumb-init for proper signal handling
RUN apk add --no-cache dumb-init

# Copy package files first for better layer caching
COPY package.json package-lock.json* ./

# Production dependencies only
RUN npm ci --omit=dev && npm cache clean --force

# Copy application code
COPY src/ ./src/

# Non-root user for security
RUN addgroup -g 1001 -S securecall && \
    adduser -S securecall -u 1001 -G securecall

USER securecall

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/ || exit 1

EXPOSE 8080

# Use dumb-init to handle PID 1 and signal forwarding
ENTRYPOINT ["dumb-init", "--"]
CMD ["node", "src/server.js"]
