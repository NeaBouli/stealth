# SecureCall — Developer Workflows

## Bug Report Triage (User Reports via Wiki)
User-Reports kommen über das Formular auf stealthx.tech/wiki/bug-report.html
und landen automatisch als GitHub Issues mit Label `user-report`.

**Reports auslesen:**
```
gh issue list --label user-report --state open
```

**Einzelnes Issue lesen:**
```
gh issue view <nummer>
```

**Issue schließen nach Fix:**
```
gh issue close <nummer> --comment "Fixed in v..."
```

**Endpoint:** https://protective-healing-production.up.railway.app/api/report
**Formular:** https://stealthx.tech/wiki/bug-report.html

---

## Beta Testing
Tester-Status und Changelog: stealthx.tech/wiki/beta-testing.html
Ziel: 15 Tester (aktuell: 14)
