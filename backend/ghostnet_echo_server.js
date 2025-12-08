'use strict';

const WebSocket = require('ws');

const PORT = 8080;

const wss = new WebSocket.Server({ port: PORT }, () => {
  console.log('[GHOSTNET-ECHO] listening on ws://0.0.0.0:' + PORT);
});

wss.on('connection', (ws, req) => {
  const addr = req.socket.remoteAddress + ':' + req.socket.remotePort;
  console.log('[GHOSTNET-ECHO] client connected from ' + addr);

  ws.on('message', (data) => {
    const text = data.toString();
    console.log('[GHOSTNET-ECHO] message: ' + text);
    // Echo zurück zum Client
    ws.send(text);
  });

  ws.on('close', () => {
    console.log('[GHOSTNET-ECHO] client disconnected');
  });

  ws.on('error', (err) => {
    console.error('[GHOSTNET-ECHO] error:', err);
  });
});
