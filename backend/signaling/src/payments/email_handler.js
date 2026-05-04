/**
 * Email delivery for activation codes.
 * Primary: Brevo (BREVO_API_KEY) — HTTP REST API, no SMTP needed
 * Fallback: Resend (RESEND_API_KEY)
 *
 * Sender: noreply@stealthx.tech (domain must be verified in both dashboards).
 */

// --- Email HTML Template ---

function generateEmailHTML(code, tier) {
  const tierName = tier === "premium" ? "Premium" : "Pro";
  return `
<div style="background:#0a0a12;color:#e0e0e0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Arial,sans-serif;padding:40px 20px;max-width:640px;margin:0 auto;">
  <div style="text-align:center;margin-bottom:32px;">
    <div style="font-size:34px;font-weight:800;color:#ff4444;letter-spacing:-1px;">SecureCall</div>
    <div style="font-size:13px;color:#666;margin-top:4px;letter-spacing:2px;text-transform:uppercase;">Encrypted Voice &middot; Zero Metadata</div>
  </div>

  <div style="background:#11131a;border:1px solid #1f2330;border-radius:12px;padding:32px;margin-bottom:24px;">
    <h2 style="color:#00E676;margin:0 0 8px 0;font-size:22px;">Payment Confirmed</h2>
    <p style="color:#aaa;margin:0 0 24px 0;font-size:15px;">Thank you for your purchase. Your ${tierName} activation code is ready below.</p>

    <div style="background:#0a0a12;border:2px solid #ff4444;border-radius:8px;padding:28px;text-align:center;margin:8px 0 0 0;">
      <div style="font-size:11px;color:#888;text-transform:uppercase;letter-spacing:3px;margin-bottom:12px;">Activation Code</div>
      <code style="font-size:26px;color:#ff4444;letter-spacing:3px;font-weight:700;font-family:'Courier New',monospace;">${code}</code>
      <div style="font-size:11px;color:#666;margin-top:14px;">Select and copy &mdash; tap to highlight on mobile</div>
    </div>
  </div>

  <div style="background:#11131a;border:1px solid #1f2330;border-radius:12px;padding:28px;margin-bottom:24px;">
    <h3 style="color:#FFD700;font-size:13px;text-transform:uppercase;letter-spacing:2px;margin:0 0 16px 0;">How to Activate</h3>
    <ol style="color:#ccc;line-height:1.9;padding-left:22px;margin:0;font-size:15px;">
      <li>Open <strong style="color:#fff;">SecureCall</strong> on your Android device</li>
      <li>Go to <strong style="color:#fff;">Settings &rarr; Konto</strong></li>
      <li>Tap <strong style="color:#fff;">Activation Code</strong></li>
      <li>Enter the code above and tap <strong style="color:#fff;">Activate</strong></li>
      <li>Restart the app &mdash; ${tierName} features unlock instantly</li>
    </ol>
  </div>

  <div style="background:#11131a;border:1px solid #1f2330;border-radius:12px;padding:28px;margin-bottom:24px;text-align:center;">
    <h3 style="color:#FFD700;font-size:13px;text-transform:uppercase;letter-spacing:2px;margin:0 0 16px 0;">Don't have the app yet?</h3>
    <div style="margin:0;">
      <a href="https://play.google.com/store/apps/details?id=com.securecall.app.free"
         style="display:inline-block;background:#ff4444;color:#fff;text-decoration:none;padding:14px 28px;border-radius:8px;font-weight:600;margin:6px;font-size:14px;">
        Google Play Store
      </a>
      <a href="https://github.com/NeaBouli/stealth/releases/latest"
         style="display:inline-block;background:rgba(255,255,255,0.08);color:#ccc;text-decoration:none;padding:14px 28px;border-radius:8px;font-weight:600;margin:6px;font-size:14px;border:1px solid rgba(255,255,255,0.12);">
        Direct APK Download
      </a>
    </div>
  </div>

  <div style="background:rgba(255,152,0,0.08);border:1px solid rgba(255,152,0,0.25);border-radius:12px;padding:20px;margin-bottom:24px;">
    <p style="color:#FF9800;font-size:13px;margin:0;line-height:1.8;">
      <strong>Important &mdash; Please read:</strong><br>
      &bull; This code activates on up to <strong>2 devices</strong><br>
      &bull; No replacement after both slots are used<br>
      &bull; Keep this email safe &mdash; <strong>it cannot be resent</strong><br>
      &bull; Due to our commitment to anonymity, we cannot recover lost codes
    </p>
  </div>

  <div style="text-align:center;margin:24px 0;font-size:13px;">
    <a href="https://stealthx.tech/wiki/user-manual.html" style="color:#ff4444;text-decoration:none;font-weight:600;">User Manual</a>
    <span style="color:#333;margin:0 10px;">|</span>
    <a href="https://stealthx.tech/terms.html" style="color:#888;text-decoration:none;">Terms of Service</a>
    <span style="color:#333;margin:0 10px;">|</span>
    <a href="mailto:support@stealthx.tech" style="color:#888;text-decoration:none;">support@stealthx.tech</a>
  </div>

  <hr style="border:none;border-top:1px solid #1a1a24;margin:28px 0 16px 0;">
  <p style="color:#444;font-size:11px;text-align:center;line-height:1.7;margin:0;">
    Vendetta Labs &mdash; Kalamata, Greece (EU)<br>
    <a href="https://stealthx.tech" style="color:#555;text-decoration:none;">stealthx.tech</a><br>
    No personal data stored &middot; This email address is not retained after delivery
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
      sender: { name: "SecureCall", email: "noreply@stealthx.tech" },
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
  console.log("[EMAIL] sendActivationCode:", toEmail.substring(0, 3) + "***", code.substring(0, 4) + "****", tier);

  const tierName = tier === "premium" ? "Premium" : "Pro";
  const subject = `Your SecureCall ${tierName} Activation Code`;
  const html = generateEmailHTML(code, tier);

  // 1. Try Brevo (primary)
  if (process.env.BREVO_API_KEY) {
    try {
      const result = await sendWithBrevo(toEmail, subject, html);
      console.log("[EMAIL] Sent via Brevo to:", toEmail.substring(0, 3) + "***", "messageId:", result?.messageId || "ok");
      return true;
    } catch (err) {
      console.error("[EMAIL] Brevo failed:", err.message, "— trying Resend...");
    }
  } else {
    console.log("[EMAIL] BREVO_API_KEY not set — skipping Brevo");
  }

  // 2. Fallback: Resend
  if (process.env.RESEND_API_KEY) {
    try {
      const result = await sendWithResend(toEmail, subject, html);
      console.log("[EMAIL] Sent via Resend to:", toEmail.substring(0, 3) + "***", "id:", result?.data?.id || "ok");
      return true;
    } catch (err) {
      console.error("[EMAIL] Resend failed:", err.message);
    }
  } else {
    console.log("[EMAIL] RESEND_API_KEY not set — skipping Resend");
  }

  console.error("[EMAIL] ALL providers failed for:", toEmail.substring(0, 3) + "***", "code:", code.substring(0, 4) + "****");
  return false;
}

module.exports = { sendActivationCode, generateEmailHTML };
