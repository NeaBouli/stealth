/**
 * Email delivery for activation codes.
 * Primary: Resend (RESEND_API_KEY)
 * Fallback: Brevo (BREVO_API_KEY)
 *
 * Sender: noreply@stealthx.tech (domain must be verified in both dashboards).
 */

// --- Email HTML Template ---

function generateEmailHTML(code, tier) {
  const tierName = tier === "premium" ? "Premium" : "Pro";
  return `
<div style="background:#0a0a12;color:#e0e0e0;font-family:'Courier New',monospace;padding:40px;max-width:600px;margin:0 auto;">
  <div style="text-align:center;margin-bottom:24px;">
    <span style="font-size:32px;font-weight:bold;color:#ff4444;">SecureCall</span>
    <span style="font-size:14px;color:#666;display:block;">Encrypted Voice</span>
  </div>

  <h2 style="color:#00E676;margin-bottom:8px;">Payment Confirmed</h2>
  <p style="color:#aaa;margin-bottom:24px;">Your ${tierName} activation code is ready.</p>

  <div style="background:#111;border:2px solid #ff4444;border-radius:8px;padding:24px;text-align:center;margin:24px 0;">
    <div style="font-size:12px;color:#888;text-transform:uppercase;letter-spacing:2px;margin-bottom:8px;">Activation Code</div>
    <code style="font-size:28px;color:#ff4444;letter-spacing:4px;font-weight:bold;">${code}</code>
  </div>

  <h3 style="color:#FFD700;font-size:14px;text-transform:uppercase;letter-spacing:1px;margin-bottom:12px;">How to Activate</h3>
  <ol style="color:#ccc;line-height:2;padding-left:20px;">
    <li>Open <strong>SecureCall</strong> on your Android device</li>
    <li>Go to <strong>Settings</strong> (gear icon)</li>
    <li>Scroll to <strong>Activation Code</strong></li>
    <li>Enter the code above and tap <strong>Activate</strong></li>
    <li>${tierName} features unlock instantly</li>
  </ol>

  <div style="background:rgba(255,152,0,0.1);border:1px solid rgba(255,152,0,0.3);border-radius:8px;padding:16px;margin:24px 0;">
    <p style="color:#FF9800;font-size:13px;margin:0;">
      <strong>Important:</strong><br>
      &#8226; This code activates on up to <strong>2 devices</strong>.<br>
      &#8226; No replacement after both slots are used.<br>
      &#8226; Keep this email safe — it cannot be resent.<br>
      &#8226; Due to our commitment to anonymity, we cannot recover lost codes.
    </p>
  </div>

  <div style="text-align:center;margin:24px 0;">
    <a href="https://stealthx.tech/wiki/user-manual.html" style="color:#ff4444;text-decoration:none;font-weight:bold;">User Manual &#8594;</a>
    &nbsp;&nbsp;|&nbsp;&nbsp;
    <a href="https://stealthx.tech/terms.html" style="color:#888;text-decoration:none;">Terms of Service</a>
  </div>

  <hr style="border:none;border-top:1px solid #222;margin:24px 0;">
  <p style="color:#444;font-size:11px;text-align:center;">
    Vendetta Labs &mdash; Kalamata, Greece (EU) | <a href="https://stealthx.tech" style="color:#555;">stealthx.tech</a><br>
    No personal data stored. This email address is not retained after delivery.
  </p>
</div>`;
}

// --- Resend Provider ---

async function sendWithResend(to, subject, html) {
  const { Resend } = require("resend");
  const resend = new Resend(process.env.RESEND_API_KEY);
  const result = await resend.emails.send({
    from: "SecureCall <noreply@stealthx.tech>",
    to,
    subject,
    html
  });
  if (result.error) throw new Error(result.error.message || JSON.stringify(result.error));
  return result;
}

// --- Brevo Provider (HTTP REST API — no SMTP needed) ---

async function sendWithBrevo(to, subject, html) {
  const apiKey = process.env.BREVO_API_KEY;
  if (!apiKey) throw new Error("BREVO_API_KEY not set");

  const response = await fetch("https://api.brevo.com/v3/smtp/email", {
    method: "POST",
    headers: {
      "api-key": apiKey,
      "Content-Type": "application/json",
      "Accept": "application/json"
    },
    body: JSON.stringify({
      sender: { name: "SecureCall", email: process.env.BREVO_SMTP_USER || "noreply@stealthx.tech" },
      to: [{ email: to }],
      subject: subject,
      htmlContent: html
    })
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Brevo API ${response.status}: ${body}`);
  }

  return await response.json();
}

// --- Main Send Function (Resend → Brevo fallback) ---

async function sendActivationCode(toEmail, code, tier) {
  console.log("[EMAIL] sendActivationCode:", toEmail, code, tier);

  const tierName = tier === "premium" ? "Premium" : "Pro";
  const subject = `Your SecureCall ${tierName} Activation Code`;
  const html = generateEmailHTML(code, tier);

  // 1. Try Resend
  if (process.env.RESEND_API_KEY) {
    try {
      const result = await sendWithResend(toEmail, subject, html);
      console.log("[EMAIL] Sent via Resend to:", toEmail, "id:", result?.data?.id || "ok");
      return true;
    } catch (err) {
      console.error("[EMAIL] Resend failed:", err.message, "— trying Brevo...");
    }
  } else {
    console.log("[EMAIL] RESEND_API_KEY not set — skipping Resend");
  }

  // 2. Fallback: Brevo
  if (process.env.BREVO_API_KEY) {
    try {
      const result = await sendWithBrevo(toEmail, subject, html);
      console.log("[EMAIL] Sent via Brevo to:", toEmail, "messageId:", result?.messageId || "ok");
      return true;
    } catch (err) {
      console.error("[EMAIL] Brevo failed:", err.message);
    }
  } else {
    console.log("[EMAIL] BREVO_API_KEY not set — skipping Brevo");
  }

  console.error("[EMAIL] ALL providers failed for:", toEmail, "code:", code);
  return false;
}

module.exports = { sendActivationCode, generateEmailHTML };
