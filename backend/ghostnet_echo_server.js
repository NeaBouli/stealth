/**
 * GhostNet Echo Server
 *
 * - Listens on ws://0.0.0.0:8080 (or GHOSTNET_ECHO_PORT)
 * - Echos back any message (text or binary)
 * - If binary >= 12 bytes, parses FrameV1 header and logs it.
 */

const WebSocket = require('ws');

const PORT = process.env.GHOSTNET_ECHO_PORT
  ? parseInt(process.env.GHOSTNET_ECHO_PORT, 10)
  : 8080;

function parseFrameV1Header(buf) {
  if (!Buffer.isBuffer(buf) || buf.length < 12) {
    return null;
  }

  const version   = buf.readUInt8(0);
  const type      = buf.readUInt8(1);
  const flags     = buf.readUInt8(2);
  const keyId     = buf.readUInt8(3);
  const sessionId = buf.readUInt32LE(4);
  const length    = buf.readUInt32LE(8);

  return { version, type, flags, keyId, sessionId, length };
}

const server = new WebSocket.Server({ port: PORT });

console.log(`[GHOSTNET-ECHO] listening on port ${PORT}`);

server.on('connection', (ws, req) => {
  const remote = req.socket && req.socket.remoteAddress
    ? req.socket.remoteAddress
    : 'unknown';

  console.log(`[GHOSTNET-ECHO] client connected from ${remote}`);

  ws.on('message', (data, isBinary) => {
    if (isBinary || Buffer.isBuffer(data)) {
      const buf = Buffer.isBuffer(data) ? data : Buffer.from(data);
      console.log(`[GHOSTNET-ECHO] received binary frame (${buf.length} bytes)`);

      const header = parseFrameV1Header(buf);
      if (header) {
        console.log(
          `[GHOSTNET-ECHO] FrameV1 header: ` +
          `version=${header.version}, ` +
          `type=${header.type}, ` +
          `flags=${header.flags}, ` +
          `keyId=${header.keyId}, ` +
          `sessionId=${header.sessionId}, ` +
          `length=${header.length}`
        );
      } else {
        console.log('[GHOSTNET-ECHO] binary frame too short for FrameV1 header');
      }

      // Echo back
      ws.send(buf, { binary: true });
    } else {
      console.log(`[GHOSTNET-ECHO] received text: ${data.toString()}`);
      ws.send(data.toString());
    }
  });

  ws.on('close', (code, reason) => {
    console.log(`[GHOSTNET-ECHO] client disconnected: code=${code}, reason=${reason}`);
  });

  ws.on('error', (err) => {
    console.error('[GHOSTNET-ECHO] ws error:', err);
  });
});

server.on('error', (err) => {
  console.error('[GHOSTNET-ECHO] server error:', err);
});
