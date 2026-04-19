# SecureCall — Developer Workflows

## Bug Report Triage (User Reports via Wiki)
User reports come through the form at stealthx.tech/wiki/bug-report.html
and are automatically created as GitHub Issues with the label `user-report`.

**Read reports:**
```
gh issue list --label user-report --state open
```

**Read a single issue:**
```
gh issue view <number>
```

**Close issue after fix:**
```
gh issue close <number> --comment "Fixed in v..."
```

**Endpoint:** https://protective-healing-production.up.railway.app/api/report
**Form:** https://stealthx.tech/wiki/bug-report.html

---

## Beta Testing
Tester status and changelog: stealthx.tech/wiki/beta-testing.html
Target: 15 testers (current: 14)
