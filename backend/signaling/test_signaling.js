#!/usr/bin/env node

/**
 * Signaling Server — Integration Test
 *
 * Testet: REGISTER, CALL_INVITE Forwarding, CALL_ACCEPT Forwarding,
 *         CALL_END Forwarding, Binary Audio Forwarding, Error Cases,
 *         ICE Servers API, WEBRTC_OFFER/ANSWER, ICE_CANDIDATE Forwarding.
 *
 * Nutzung:
 *   1. Server starten:  npm start
 *   2. Test ausfuehren: node test_signaling.js
 */

const WebSocket = require("ws");
const http = require("http");

const SERVER_URL = "ws://localhost:8080/signal";
const HTTP_BASE = "http://localhost:8080";

// --- Helper: HTTP GET ---
function httpGet(path) {
  return new Promise((resolve, reject) => {
    http.get(`${HTTP_BASE}${path}`, (res) => {
      let body = "";
      res.on("data", (chunk) => body += chunk);
      res.on("end", () => {
        try {
          resolve(JSON.parse(body));
        } catch {
          reject(new Error("Invalid JSON from " + path));
        }
      });
    }).on("error", reject);
  });
}

let passed = 0;
let failed = 0;

function assert(condition, testName) {
  if (condition) {
    console.log(`  PASS: ${testName}`);
    passed++;
  } else {
    console.log(`  FAIL: ${testName}`);
    failed++;
  }
}

// --- Helper: WebSocket Client mit Message-Queue ---
function createClient(name) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(SERVER_URL);
    const messages = [];
    const waiters = [];

    ws.on("open", () => {
      resolve({
        name,
        ws,
        send(obj) {
          ws.send(JSON.stringify(obj));
        },
        sendBinary(buf) {
          ws.send(buf, { binary: true });
        },
        // Warte auf naechste JSON-Nachricht
        waitForMessage(timeoutMs = 2000) {
          return new Promise((res, rej) => {
            if (messages.length > 0) {
              return res(messages.shift());
            }
            const timer = setTimeout(() => {
              rej(new Error(`[${name}] timeout waiting for message`));
            }, timeoutMs);
            waiters.push((msg) => {
              clearTimeout(timer);
              res(msg);
            });
          });
        },
        // Warte auf naechste Binary-Nachricht
        waitForBinary(timeoutMs = 2000) {
          return new Promise((res, rej) => {
            if (messages.length > 0) {
              const m = messages.shift();
              if (Buffer.isBuffer(m)) return res(m);
            }
            const timer = setTimeout(() => {
              rej(new Error(`[${name}] timeout waiting for binary`));
            }, timeoutMs);
            waiters.push((msg) => {
              clearTimeout(timer);
              res(msg);
            });
          });
        },
        close() {
          ws.close();
        }
      });
    });

    ws.on("message", (data, isBinary) => {
      let parsed;
      if (isBinary) {
        parsed = Buffer.from(data);
      } else {
        try {
          parsed = JSON.parse(data.toString());
        } catch {
          parsed = data.toString();
        }
      }

      if (waiters.length > 0) {
        const waiter = waiters.shift();
        waiter(parsed);
      } else {
        messages.push(parsed);
      }
    });

    ws.on("error", reject);
  });
}

// ==========================================
// Test Suite
// ==========================================

async function runTests() {
  console.log("\n=== SecureCall Signaling Server Tests ===\n");

  // -----------------------------------------
  // Test 1: REGISTER
  // -----------------------------------------
  console.log("--- Test 1: REGISTER ---");
  const alice = await createClient("Alice");
  const bob = await createClient("Bob");

  alice.send({ type: "REGISTER", clientId: "alice" });
  const regA = await alice.waitForMessage();
  assert(regA.type === "REGISTERED", "Alice receives REGISTERED");
  assert(regA.clientId === "alice", "Alice clientId is correct");

  bob.send({ type: "REGISTER", clientId: "bob" });
  const regB = await bob.waitForMessage();
  assert(regB.type === "REGISTERED", "Bob receives REGISTERED");
  assert(regB.clientId === "bob", "Bob clientId is correct");

  // -----------------------------------------
  // Test 2: REGISTER Fehler — ohne clientId
  // -----------------------------------------
  console.log("\n--- Test 2: REGISTER ohne clientId ---");
  const anon = await createClient("Anon");
  anon.send({ type: "REGISTER" });
  const regErr = await anon.waitForMessage();
  assert(regErr.type === "ERROR", "Error returned for missing clientId");
  assert(regErr.error === "missing_client_id", "Correct error code");
  anon.close();

  // -----------------------------------------
  // Test 3: REGISTER Fehler — ID bereits vergeben
  // -----------------------------------------
  console.log("\n--- Test 3: REGISTER doppelte ID ---");
  const imposter = await createClient("Imposter");
  imposter.send({ type: "REGISTER", clientId: "alice" });
  const regDup = await imposter.waitForMessage();
  assert(regDup.type === "ERROR", "Error returned for duplicate clientId");
  assert(regDup.error === "client_id_taken", "Correct error code for duplicate");
  imposter.close();

  // -----------------------------------------
  // Test 4: CALL_INVITE ohne REGISTER
  // -----------------------------------------
  console.log("\n--- Test 4: CALL_INVITE ohne REGISTER ---");
  const unreg = await createClient("Unreg");
  unreg.send({ type: "CALL_INVITE", to: "bob" });
  const unregErr = await unreg.waitForMessage();
  assert(unregErr.type === "ERROR", "Error returned for unregistered INVITE");
  assert(unregErr.error === "not_registered", "Correct error code");
  unreg.close();

  // -----------------------------------------
  // Test 5: CALL_INVITE Forwarding
  // -----------------------------------------
  console.log("\n--- Test 5: CALL_INVITE Forwarding ---");
  alice.send({ type: "CALL_INVITE", to: "bob" });

  // Alice bekommt ACK
  const inviteAck = await alice.waitForMessage();
  assert(inviteAck.type === "CALL_INVITE_ACK", "Alice receives CALL_INVITE_ACK");
  assert(inviteAck.ok === true, "INVITE ACK is ok");
  assert(typeof inviteAck.sessionId === "string", "SessionId is present");
  assert(inviteAck.from === "alice", "ACK from is alice");
  assert(inviteAck.to === "bob", "ACK to is bob");

  const sessionId = inviteAck.sessionId;

  // Bob bekommt den INVITE
  const bobInvite = await bob.waitForMessage();
  assert(bobInvite.type === "CALL_INVITE", "Bob receives forwarded CALL_INVITE");
  assert(bobInvite.sessionId === sessionId, "Same sessionId");
  assert(bobInvite.from === "alice", "Invite from alice");
  assert(bobInvite.to === "bob", "Invite to bob");

  // -----------------------------------------
  // Test 6: CALL_INVITE an nicht-existierenden Peer
  // -----------------------------------------
  console.log("\n--- Test 6: CALL_INVITE an Peer der nicht existiert ---");
  alice.send({ type: "CALL_INVITE", to: "charlie" });
  const noCharlie = await alice.waitForMessage();
  assert(noCharlie.type === "ERROR", "Error for missing peer");
  assert(noCharlie.error === "peer_not_found", "Correct error code");

  // -----------------------------------------
  // Test 7: CALL_ACCEPT Forwarding
  // -----------------------------------------
  console.log("\n--- Test 7: CALL_ACCEPT Forwarding ---");
  bob.send({ type: "CALL_ACCEPT", sessionId });

  // Bob bekommt ACK
  const acceptAckBob = await bob.waitForMessage();
  assert(acceptAckBob.type === "CALL_ACCEPT_ACK", "Bob receives CALL_ACCEPT_ACK");
  assert(acceptAckBob.ok === true, "ACCEPT ACK is ok");

  // Alice bekommt den ACCEPT
  const aliceAccept = await alice.waitForMessage();
  assert(aliceAccept.type === "CALL_ACCEPT", "Alice receives forwarded CALL_ACCEPT");
  assert(aliceAccept.sessionId === sessionId, "Same sessionId");
  assert(aliceAccept.from === "bob", "Accept from bob");

  // -----------------------------------------
  // Test 8: Binary Audio Forwarding
  // -----------------------------------------
  console.log("\n--- Test 8: Binary Audio Forwarding ---");
  const audioData = Buffer.from([0x01, 0x02, 0x03, 0x04, 0xAA, 0xBB]);
  alice.sendBinary(audioData);

  const bobAudio = await bob.waitForBinary();
  assert(Buffer.isBuffer(bobAudio), "Bob receives binary data");
  assert(bobAudio.equals(audioData), "Audio data matches");

  // Auch in die andere Richtung
  const audioData2 = Buffer.from([0xFF, 0xFE, 0xFD]);
  bob.sendBinary(audioData2);

  const aliceAudio = await alice.waitForBinary();
  assert(Buffer.isBuffer(aliceAudio), "Alice receives binary data");
  assert(aliceAudio.equals(audioData2), "Reverse audio data matches");

  // -----------------------------------------
  // Test 9: CALL_END Forwarding
  // -----------------------------------------
  console.log("\n--- Test 9: CALL_END Forwarding ---");
  alice.send({ type: "CALL_END", sessionId });

  // Alice bekommt ACK
  const endAck = await alice.waitForMessage();
  assert(endAck.type === "CALL_END_ACK", "Alice receives CALL_END_ACK");
  assert(endAck.ok === true, "END ACK is ok");

  // Bob bekommt den END
  const bobEnd = await bob.waitForMessage();
  assert(bobEnd.type === "CALL_END", "Bob receives forwarded CALL_END");
  assert(bobEnd.sessionId === sessionId, "Same sessionId in END");
  assert(bobEnd.from === "alice", "END from alice");

  // -----------------------------------------
  // Test 10: Disconnect Cleanup
  // -----------------------------------------
  console.log("\n--- Test 10: Disconnect Cleanup ---");

  // Neuen Call starten
  alice.send({ type: "CALL_INVITE", to: "bob" });
  const inv2Ack = await alice.waitForMessage();
  const session2 = inv2Ack.sessionId;
  await bob.waitForMessage(); // Bob bekommt INVITE

  bob.send({ type: "CALL_ACCEPT", sessionId: session2 });
  await bob.waitForMessage(); // Bob ACK
  await alice.waitForMessage(); // Alice ACCEPT

  // Alice disconnected — Bob sollte CALL_END bekommen
  alice.close();
  const bobDisconnect = await bob.waitForMessage(3000);
  assert(bobDisconnect.type === "CALL_END", "Bob gets CALL_END on peer disconnect");
  assert(bobDisconnect.reason === "peer_disconnected", "Reason is peer_disconnected");

  // -----------------------------------------
  // Test 11: GET /ice-servers
  // -----------------------------------------
  console.log("\n--- Test 11: GET /ice-servers ---");
  const iceRes = await httpGet("/ice-servers");
  assert(Array.isArray(iceRes.iceServers), "iceServers is an array");
  assert(iceRes.iceServers.length >= 2, "At least 2 ICE servers");
  assert(iceRes.iceServers[0].urls.startsWith("stun:"), "First entry is STUN");
  assert(iceRes.iceServers[1].urls.startsWith("turn:"), "Second entry is TURN");
  assert(typeof iceRes.iceServers[1].username === "string", "TURN has username");
  assert(typeof iceRes.iceServers[1].credential === "string", "TURN has credential");

  // -----------------------------------------
  // Test 12: WEBRTC_OFFER Forwarding
  // -----------------------------------------
  console.log("\n--- Test 12: WEBRTC_OFFER Forwarding ---");

  // Neuen Call aufbauen (alice reconnect nötig nach disconnect in Test 10)
  const alice2 = await createClient("Alice2");
  alice2.send({ type: "REGISTER", clientId: "alice2" });
  await alice2.waitForMessage(); // REGISTERED

  const bob2 = await createClient("Bob2");
  bob2.send({ type: "REGISTER", clientId: "bob2" });
  await bob2.waitForMessage(); // REGISTERED

  alice2.send({ type: "CALL_INVITE", to: "bob2" });
  const inv3Ack = await alice2.waitForMessage();
  const session3 = inv3Ack.sessionId;
  await bob2.waitForMessage(); // Bob bekommt INVITE

  bob2.send({ type: "CALL_ACCEPT", sessionId: session3 });
  await bob2.waitForMessage(); // Bob ACK
  await alice2.waitForMessage(); // Alice ACCEPT

  // Jetzt SDP Offer senden
  const fakeSdp = "v=0\r\no=- 123 1 IN IP4 127.0.0.1\r\ns=SecureCall\r\n";
  alice2.send({ type: "WEBRTC_OFFER", sessionId: session3, sdp: fakeSdp });

  // Alice bekommt ACK
  const offerAck = await alice2.waitForMessage();
  assert(offerAck.type === "WEBRTC_OFFER_ACK", "Alice receives WEBRTC_OFFER_ACK");
  assert(offerAck.ok === true, "OFFER ACK is ok");

  // Bob bekommt den Offer
  const bobOffer = await bob2.waitForMessage();
  assert(bobOffer.type === "WEBRTC_OFFER", "Bob receives forwarded WEBRTC_OFFER");
  assert(bobOffer.sessionId === session3, "Same sessionId in OFFER");
  assert(bobOffer.from === "alice2", "OFFER from alice2");
  assert(bobOffer.sdp === fakeSdp, "SDP data matches");

  // -----------------------------------------
  // Test 13: WEBRTC_ANSWER Forwarding
  // -----------------------------------------
  console.log("\n--- Test 13: WEBRTC_ANSWER Forwarding ---");
  const answerSdp = "v=0\r\no=- 456 1 IN IP4 127.0.0.1\r\ns=SecureCall-Answer\r\n";
  bob2.send({ type: "WEBRTC_ANSWER", sessionId: session3, sdp: answerSdp });

  // Bob bekommt ACK
  const answerAck = await bob2.waitForMessage();
  assert(answerAck.type === "WEBRTC_ANSWER_ACK", "Bob receives WEBRTC_ANSWER_ACK");
  assert(answerAck.ok === true, "ANSWER ACK is ok");

  // Alice bekommt den Answer
  const aliceAnswer = await alice2.waitForMessage();
  assert(aliceAnswer.type === "WEBRTC_ANSWER", "Alice receives forwarded WEBRTC_ANSWER");
  assert(aliceAnswer.sessionId === session3, "Same sessionId in ANSWER");
  assert(aliceAnswer.from === "bob2", "ANSWER from bob2");
  assert(aliceAnswer.sdp === answerSdp, "Answer SDP data matches");

  // -----------------------------------------
  // Test 14: ICE_CANDIDATE Forwarding (bidirektional)
  // -----------------------------------------
  console.log("\n--- Test 14: ICE_CANDIDATE Forwarding ---");
  const iceCandidate1 = { candidate: "candidate:1 1 udp 2130706431 192.168.1.1 5000 typ host", sdpMid: "0", sdpMLineIndex: 0 };
  alice2.send({ type: "ICE_CANDIDATE", sessionId: session3, candidate: iceCandidate1 });

  // Alice bekommt ACK
  const iceAck1 = await alice2.waitForMessage();
  assert(iceAck1.type === "ICE_CANDIDATE_ACK", "Alice receives ICE_CANDIDATE_ACK");

  // Bob bekommt den Kandidaten
  const bobIce = await bob2.waitForMessage();
  assert(bobIce.type === "ICE_CANDIDATE", "Bob receives forwarded ICE_CANDIDATE");
  assert(bobIce.from === "alice2", "ICE from alice2");
  assert(bobIce.candidate.candidate === iceCandidate1.candidate, "ICE candidate data matches");

  // Auch Bob -> Alice
  const iceCandidate2 = { candidate: "candidate:2 1 udp 1694498815 10.0.0.1 6000 typ srflx", sdpMid: "0", sdpMLineIndex: 0 };
  bob2.send({ type: "ICE_CANDIDATE", sessionId: session3, candidate: iceCandidate2 });

  const iceAck2 = await bob2.waitForMessage();
  assert(iceAck2.type === "ICE_CANDIDATE_ACK", "Bob receives ICE_CANDIDATE_ACK");

  const aliceIce = await alice2.waitForMessage();
  assert(aliceIce.type === "ICE_CANDIDATE", "Alice receives forwarded ICE_CANDIDATE");
  assert(aliceIce.from === "bob2", "ICE from bob2");
  assert(aliceIce.candidate.candidate === iceCandidate2.candidate, "Reverse ICE candidate matches");

  // -----------------------------------------
  // Test 15: WEBRTC_OFFER ohne Session — Fehler
  // -----------------------------------------
  console.log("\n--- Test 15: WEBRTC_OFFER ohne gueltige Session ---");
  alice2.send({ type: "WEBRTC_OFFER", sessionId: "nonexistent-session", sdp: "fake" });
  const noSession = await alice2.waitForMessage();
  assert(noSession.type === "ERROR", "Error for invalid session");
  assert(noSession.error === "session_not_found", "Correct error code");

  // -----------------------------------------
  // Test 16: WEBRTC_OFFER ohne SDP — Fehler
  // -----------------------------------------
  console.log("\n--- Test 16: WEBRTC_OFFER ohne SDP ---");
  alice2.send({ type: "WEBRTC_OFFER", sessionId: session3 });
  const noSdp = await alice2.waitForMessage();
  assert(noSdp.type === "ERROR", "Error for missing SDP");
  assert(noSdp.error === "missing_sdp", "Correct error code for missing SDP");

  // -----------------------------------------
  // Test 17: GHOST_ACK enthaelt iceServers
  // -----------------------------------------
  console.log("\n--- Test 17: GHOST_ACK enthaelt iceServers ---");
  alice2.send({ type: "GHOST_PREPARE", sessionId: session3 });
  const ghostAck = await alice2.waitForMessage();
  assert(ghostAck.type === "GHOST_ACK", "Receives GHOST_ACK");
  assert(Array.isArray(ghostAck.iceServers), "GHOST_ACK contains iceServers");
  assert(ghostAck.iceServers[0].urls.startsWith("stun:"), "iceServers has STUN entry");

  // Cleanup
  alice2.close();
  bob2.close();

  // -----------------------------------------
  // Ergebnis
  // -----------------------------------------

  console.log(`\n=== Ergebnis: ${passed} passed, ${failed} failed ===`);

  if (failed > 0) {
    process.exit(1);
  } else {
    console.log("Alle Tests bestanden!\n");
    process.exit(0);
  }
}

runTests().catch((err) => {
  console.error("\nTest-Fehler:", err.message);
  process.exit(1);
});
