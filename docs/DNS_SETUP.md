# DNS Setup for stealthx.tech

## At your domain registrar, create these DNS records:

### A Records (GitHub Pages IPs)
```
185.199.108.153
185.199.109.153
185.199.110.153
185.199.111.153
```

### CNAME Record
```
www → neabouli.github.io
```

## In GitHub Repository Settings:
1. Go to Settings → Pages
2. Custom Domain → enter `stealthx.tech`
3. Wait for DNS propagation (~24h)
4. Enable "Enforce HTTPS" checkbox

## Verification
After DNS propagates:
- https://stealthx.tech should show the landing page
- https://www.stealthx.tech should redirect to apex
- HTTPS should be enforced automatically by GitHub

## Email (ProtonMail)
- kaspartisan@proton.me is the contact email
- Consider adding MX/SPF/DKIM records if using stealthx.tech email later
