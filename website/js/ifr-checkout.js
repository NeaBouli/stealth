(function () {
  "use strict";

  var root = document.querySelector("[data-ifr-checkout]");
  if (!root) return;

  var API = "https://api.stealthx.tech";
  var WC_PROJECT_ID = "32f56abaa4b1d7f59fb1571c0c0a551f";
  var WC_ESM_URL = "https://esm.sh/@walletconnect/ethereum-provider@2.17.3";
  var RPC_URL = "https://eth.llamarpc.com";
  var provider = null;
  var address = "";

  var connectButton = root.querySelector("[data-ifr-connect]");
  var disconnectButton = root.querySelector("[data-ifr-disconnect]");
  var addressField = root.querySelector("[data-ifr-address]");
  var status = root.querySelector("[data-ifr-status]");
  var checkoutButtons = root.querySelectorAll("[data-ifr-tier]");

  function setStatus(message) {
    if (status) status.textContent = message;
  }

  function balanceText(value) {
    return value ? " Balance: " + value + " IFR." : "";
  }

  function checkoutError(data) {
    var error = data && data.error ? data.error : "Checkout unavailable";
    if (error === "checkout_moved_to_vlabs") return "Discount checkout is temporarily unavailable while the fiscal checkout is completed.";
    if (error === "invalid_tier") return "This product is not available for IFR discount checkout.";
    if (error === "ifr_not_eligible") return "This wallet does not hold enough IFR for the selected discount." + balanceText(data.balanceAmount);
    if (error === "ifr_tier_too_low") return "The connected wallet does not meet this product's IFR threshold." + balanceText(data.balanceAmount);
    if (error === "wallet_signature_required") return "Sign the wallet message before checkout can open.";
    if (error === "wallet_signature_invalid") return "The wallet signature could not be verified. Reconnect and try again.";
    if (error === "wallet_challenge_expired") return "Wallet verification expired. Start the checkout again.";
    if (error === "invalid_wallet_challenge" || error === "wallet_challenge_mismatch") return "Wallet verification did not match this checkout. Reconnect and try again.";
    if (error === "rate_limited") return "Too many attempts. Wait a few minutes and try again.";
    return error;
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
    if (connectButton) connectButton.textContent = "Wallet connected";
    if (disconnectButton) disconnectButton.disabled = false;
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
    if (!address) address = await connectWalletConnect();
    renderConnected();
    return address;
  }

  async function disconnect() {
    var current = provider;
    provider = null;
    address = "";
    if (addressField) addressField.value = "";
    if (connectButton) connectButton.textContent = "Connect wallet";
    if (disconnectButton) disconnectButton.disabled = true;
    try {
      if (current && current.disconnect) await current.disconnect();
    } catch (_) {}
    setStatus("Wallet disconnected. No wallet state is stored in the Android app.");
  }

  async function walletProof(tier) {
    var walletAddress = await connect();
    var response = await fetch(API + "/stripe/ifr-discount-challenge", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ tier: tier, walletAddress: walletAddress })
    });
    var challenge = await response.json().catch(function () { return {}; });
    if (!response.ok || !challenge.message || !challenge.nonce) throw new Error(checkoutError(challenge));
    setStatus("Sign the message to prove wallet ownership. This cannot move tokens.");
    var signature;
    try {
      signature = await provider.request({ method: "personal_sign", params: [challenge.message, walletAddress] });
    } catch (_) {
      signature = await provider.request({ method: "personal_sign", params: [walletAddress, challenge.message] });
    }
    return { walletAddress: walletAddress, walletNonce: challenge.nonce, walletSignature: signature };
  }

  async function checkout(button) {
    var original = button.textContent;
    button.disabled = true;
    button.textContent = "Verifying...";
    try {
      var payload = { tier: button.dataset.ifrTier, ifrDiscount: true };
      Object.assign(payload, await walletProof(payload.tier));
      setStatus("Checking IFR balance on Ethereum Mainnet...");
      var response = await fetch(API + "/stripe/create-dynamic-checkout", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });
      var data = await response.json().catch(function () { return {}; });
      if (!response.ok || !data.url) throw new Error(checkoutError(data));
      setStatus("Eligible" + balanceText(data.ifrBalanceAmount) + " Opening Stripe with the 50% discount...");
      window.location.assign(data.url);
    } catch (error) {
      setStatus(error && error.message ? error.message : "Checkout unavailable");
      button.disabled = false;
      button.textContent = original;
    }
  }

  if (connectButton) connectButton.addEventListener("click", function () {
    connectButton.disabled = true;
    connect().catch(function (error) {
      connectButton.disabled = false;
      connectButton.textContent = "Connect wallet";
      setStatus(error && error.message ? error.message : "Wallet connection failed.");
    });
  });
  if (disconnectButton) disconnectButton.addEventListener("click", disconnect);
  checkoutButtons.forEach(function (button) {
    button.addEventListener("click", function () { checkout(button); });
  });
})();
