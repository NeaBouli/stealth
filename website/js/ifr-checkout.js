(function () {
  "use strict";

  var root = document.querySelector("[data-ifr-checkout]");
  if (!root) return;

  var API = "https://api.stealthx.tech";
  var WC_PROJECT_ID = "32f56abaa4b1d7f59fb1571c0c0a551f";
  var WC_ESM_URL = "https://esm.sh/@walletconnect/ethereum-provider@2.17.3";
  var RPC_URL = "https://eth.llamarpc.com";
  // Canonical central shop entry. The signed eligibility travels only in the URL
  // fragment, which browsers never send to a server or in a Referer header.
  var SHOP_URL = "https://vlabs.gr/en/shop?focus=security";
  var HANDOFF_KEY = "stealthx-ifr";
  var PRODUCT_IDS = {
    pro_lifetime: "stealthx-securecall-pro-lifetime",
    premium_lifetime: "stealthx-securecall-premium-lifetime"
  };
  var provider = null;
  var address = "";
  var walletActionPending = false;

  var connectButton = root.querySelector("[data-ifr-connect]");
  var disconnectButton = root.querySelector("[data-ifr-disconnect]");
  var addressField = root.querySelector("[data-ifr-address]");
  var status = root.querySelector("[data-ifr-status]");
  var checkoutButtons = root.querySelectorAll("[data-ifr-tier]");

  var checkoutEnabled = root.dataset.ifrEnabled === "true";
  if (!checkoutEnabled) {
    if (connectButton) connectButton.disabled = true;
    if (disconnectButton) disconnectButton.disabled = true;
    checkoutButtons.forEach(function (button) { button.disabled = true; });
    if (status) status.textContent = "IFR discount verification and checkout are currently disabled pending payment and fiscal approval.";
    return;
  }

  function setStatus(message) {
    if (status) status.textContent = message;
  }

  function setWalletActionPending(pending) {
    walletActionPending = pending;
    if (connectButton) connectButton.disabled = pending;
    if (disconnectButton) disconnectButton.disabled = pending || !address;
    checkoutButtons.forEach(function (button) { button.disabled = pending; });
  }

  setWalletActionPending(false);
  setStatus("Connect a browser wallet to verify IFR eligibility.");

  function eligibilityError(data) {
    var error = data && data.error ? data.error : "";
    if (error === "ifr_eligibility_unavailable") return "IFR holder verification is currently unavailable. No discount was requested.";
    if (error === "ifr_provider_unavailable") return "The read-only IFR balance check is unavailable right now. Try again later; no discount was requested.";
    if (error === "ifr_not_eligible") return "This wallet does not currently hold IFR. You can still buy at the regular price in the shop.";
    if (error === "invalid_product") return "This product is not available for the IFR holder price.";
    if (error === "invalid_wallet") return "The connected wallet address is not valid. Reconnect and try again.";
    if (error === "wallet_signature_required" || error === "wallet_signature_invalid") return "The wallet signature could not be verified. Reconnect and try again.";
    if (error === "wallet_challenge_expired") return "Wallet verification expired. Start again.";
    if (error === "invalid_wallet_challenge" || error === "wallet_challenge_mismatch") return "Wallet verification did not match this product. Start again.";
    if (error === "origin_not_allowed") return "Verification is only available on the official StealthX website.";
    if (error === "rate_limited") return "Too many attempts. Wait a few minutes and try again.";
    return "IFR holder verification is currently unavailable. No discount was requested.";
  }

  function base64Url(text) {
    var bytes = new TextEncoder().encode(text);
    var binary = "";
    bytes.forEach(function (byte) { binary += String.fromCharCode(byte); });
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  }

  function handoffUrl(eligibility) {
    return SHOP_URL + "#" + HANDOFF_KEY + "=" + base64Url(JSON.stringify(eligibility));
  }

  async function fetchJson(url, options) {
    var controller = new AbortController();
    var timeout = window.setTimeout(function () { controller.abort(); }, 15000);
    try {
      options.signal = controller.signal;
      var response = await fetch(url, options);
      var data = await response.json().catch(function () { return {}; });
      return { response: response, data: data };
    } catch (error) {
      if (error && error.name === "AbortError") throw new Error("The checkout server did not respond in time. Try again.");
      throw error;
    } finally {
      window.clearTimeout(timeout);
    }
  }

  function canRetryPersonalSign(error) {
    if (!error || error.code === 4001) return false;
    if (error.code === -32602) return true;
    var message = String(error.message || "").toLowerCase();
    return message.indexOf("invalid params") !== -1 ||
      message.indexOf("invalid argument") !== -1 ||
      message.indexOf("parameter order") !== -1;
  }

  function injectedProviders() {
    var list = [];
    if (window.ethereum && Array.isArray(window.ethereum.providers)) list = list.concat(window.ethereum.providers);
    if (window.ethereum) list.push(window.ethereum);
    if (window.phantom && window.phantom.ethereum) list.push(window.phantom.ethereum);
    return list.filter(function (candidate, index, all) {
      return candidate && candidate.request && all.indexOf(candidate) === index;
    });
  }

  function preferredProvider() {
    var providers = injectedProviders();
    return providers.find(function (candidate) { return candidate.isMetaMask; }) ||
      providers.find(function (candidate) { return candidate.isPhantom; }) ||
      providers.find(function (candidate) { return candidate.isCoinbaseWallet; }) ||
      providers[0] || null;
  }

  async function switchMainnet(candidate) {
    try {
      await candidate.request({ method: "wallet_switchEthereumChain", params: [{ chainId: "0x1" }] });
    } catch (_) {
      // The balance check is server-side on Mainnet; unsupported switching is not fatal.
    }
  }

  async function connectInjected() {
    var candidate = preferredProvider();
    if (!candidate) return null;
    var accounts = await candidate.request({ method: "eth_requestAccounts" });
    if (!accounts || !accounts[0]) throw new Error("No wallet account was returned.");
    await switchMainnet(candidate);
    provider = candidate;
    return accounts[0];
  }

  async function connectWalletConnect() {
    setStatus("Opening the wallet selector...");
    var module = await import(WC_ESM_URL);
    var EthereumProvider = module.EthereumProvider || module.default;
    if (!EthereumProvider) throw new Error("WalletConnect is unavailable.");
    var candidate = await EthereumProvider.init({
      projectId: WC_PROJECT_ID,
      optionalChains: [1],
      showQrModal: true,
      rpcMap: { 1: RPC_URL },
      methods: ["eth_requestAccounts", "personal_sign", "wallet_switchEthereumChain"],
      events: ["accountsChanged", "chainChanged", "disconnect"],
      metadata: {
        name: root.dataset.ifrProduct + " IFR Discount",
        description: "Browser-only IFR holder verification for checkout.",
        url: window.location.origin,
        icons: [window.location.origin + "/favicon.ico"]
      },
      qrModalOptions: { themeMode: "dark" }
    });
    var accounts = await candidate.enable();
    if (!accounts || !accounts[0]) throw new Error("No wallet account was returned.");
    await switchMainnet(candidate);
    provider = candidate;
    return accounts[0];
  }

  function renderConnected() {
    if (addressField) addressField.value = address;
    if (connectButton) {
      connectButton.textContent = "Wallet connected";
    }
    setStatus("Connected: " + address.slice(0, 6) + "..." + address.slice(-4) + ". Select a discount checkout to verify the IFR balance.");
  }

  async function connect() {
    if (provider && address) return address;
    setStatus("Connecting browser wallet...");
    try {
      address = await connectInjected();
    } catch (error) {
      if (error && error.code === 4001) throw error;
    }
    if (!address) {
      try {
        address = await connectWalletConnect();
      } catch (error) {
        if (error && error.code === 4001) throw error;
        throw new Error("No browser wallet is available. Install or unlock a browser wallet, or buy at the regular price in the shop.");
      }
    }
    renderConnected();
    return address;
  }

  async function disconnect() {
    var current = provider;
    provider = null;
    address = "";
    if (addressField) addressField.value = "";
    if (connectButton) connectButton.textContent = "Connect wallet";
    try {
      if (current && current.disconnect) await current.disconnect();
    } catch (_) {}
    setStatus("Wallet disconnected. No wallet state is stored in the Android app.");
  }

  async function holderEligibility(productId) {
    var walletAddress = await connect();
    var challengeResult = await fetchJson(API + "/vlabs/ifr-eligibility/challenge", {
      method: "POST",
      credentials: "omit",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ productId: productId, walletAddress: walletAddress })
    });
    var challenge = challengeResult.data;
    if (!challengeResult.response.ok || !challenge.message || !challenge.nonce) throw new Error(eligibilityError(challenge));
    setStatus("Sign the message to prove wallet ownership. This cannot move tokens.");
    var signature;
    try {
      signature = await provider.request({ method: "personal_sign", params: [challenge.message, walletAddress] });
    } catch (error) {
      if (!canRetryPersonalSign(error)) throw error;
      signature = await provider.request({ method: "personal_sign", params: [walletAddress, challenge.message] });
    }
    setStatus("Checking the IFR balance read-only...");
    var result = await fetchJson(API + "/vlabs/ifr-eligibility", {
      method: "POST",
      credentials: "omit",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ productId: productId, walletAddress: walletAddress, nonce: challenge.nonce, signature: signature })
    });
    if (!result.response.ok || !result.data.eligibility) throw new Error(eligibilityError(result.data));
    return result.data.eligibility;
  }

  async function checkout(button) {
    if (walletActionPending) return;
    var original = button.textContent;
    var productId = PRODUCT_IDS[button.dataset.ifrTier];
    if (!productId) {
      setStatus(eligibilityError({ error: "invalid_product" }));
      return;
    }
    setWalletActionPending(true);
    button.textContent = "Verifying...";
    try {
      var eligibility = await holderEligibility(productId);
      setStatus("Holder verified for this product. Opening the central shop; the final price is confirmed there before payment.");
      window.location.assign(handoffUrl(eligibility));
    } catch (error) {
      if (error && error.code === 4001) setStatus("Signature rejected. No discount was requested.");
      else setStatus(error && error.message ? error.message : eligibilityError(null));
      button.textContent = original;
      setWalletActionPending(false);
    }
  }

  if (connectButton) connectButton.addEventListener("click", function () {
    if (walletActionPending) return;
    setWalletActionPending(true);
    connect().catch(function (error) {
      connectButton.textContent = "Connect wallet";
      setStatus(error && error.message ? error.message : "Wallet connection failed.");
    }).finally(function () { setWalletActionPending(false); });
  });
  if (disconnectButton) disconnectButton.addEventListener("click", function () {
    if (walletActionPending) return;
    setWalletActionPending(true);
    disconnect().finally(function () { setWalletActionPending(false); });
  });
  checkoutButtons.forEach(function (button) {
    button.addEventListener("click", function () { checkout(button); });
  });
})();
