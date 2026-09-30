id: SC-OCT01-SETTINGS
goal: Correct the physical-smoke Settings header touch-target and mixed-locale defects with a minimal mapped UI patch.
scope: Settings section header sizing and section-title string resources only; no behavior/business logic changes.
instructions:
  - Read AGENTS.md, architecture map, physical-smoke report/evidence and frontend-visual-release-gate skill; identify Settings UI node before editing.
  - Tab S4 measured 10 clickable Settings section rows at 37dp high. Ensure a stable minimum 48dp touch target using the existing layout/theme pattern.
  - Resolve mixed DE/EN section titles through existing localization resources; do not force a language or broadly rewrite unrelated copy.
  - Inspect available disk first. Do not start Gradle/builds below 15GiB available. No ADB: device lane is finished for this block.
  - Run focused static/resource checks and any low-footprint regression verification. Do not claim visual/physical revalidation if a new APK cannot be built.
  - No FLAG_SECURE changes. No unrelated UI, permissions, network, entitlement, production, pushes or other agents.
  - Commit patch plus concise sanitized report. Clearly mark physical/visual gate pending when blocked by storage and retain exact next validation cases.
report: .fleet/reports/SC-OCT01-SETTINGS.md
