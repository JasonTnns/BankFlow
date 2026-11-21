# Release checklist

## One-time setup
- [ ] Upload keystore created, backed up in two places, passwords in a password manager (`keytool` command in the docs)
- [ ] `~/.gradle/gradle.properties` has `bankflow.keystore.*`, `bankflow.key.*` and `bankflow.apiBaseUrl`
- [ ] Play Console app created, **Play App Signing enrolled**
- [ ] GitHub `release` environment with the four secrets and required reviewers
- [ ] Privacy policy published at a URL; Data safety form answered from PRIVACY_AND_DATA_SAFETY.md

## Backend (production)
- [ ] `NODE_ENV=production`, `JWT_SECRET` (48+ random hex chars), `TRUST_PROXY_HOPS=1`, `DB_PATH` on a persistent volume
- [ ] TLS terminated by a reverse proxy that sets `X-Forwarded-Proto` and forwards only to the app
- [ ] `ALLOW_SEED` unset, `DOCS_ENABLED` unset, `CORS_ORIGINS` unset (the app needs none)
- [ ] Single instance per database file (SQLite), restarts automatically on crash
- [ ] Nightly `npm run backup`, copied off the machine; **restore tested once**
- [ ] `npm test` green, then `npm run smoke -- https://staging...` green against staging

## Build
- [ ] `./gradlew :app:testDebugUnitTest` green (includes release-hardening, contrast, strings, RTL guards)
- [ ] `./gradlew :app:connectedDebugAndroidTest` green on an emulator
- [ ] Baseline profile regenerated if navigation or first screens changed
- [ ] CI "Release bundle" run produced the `.aab` and `mapping.txt`
- [ ] `apksigner` / Play pre-launch report show the expected signing certificate

## Manual pass on the release build (physical device, staging server)
- [ ] Fresh install: register, welcome balance, dashboard
- [ ] Transfer with timeout in the middle (airplane mode on Confirm, then Try again): one transfer
- [ ] App unlock on, swipe away, reopen: lock screen first
- [ ] Offline: saved data shown with its age
- [ ] Arabic: layout mirrors, Arabic-digit typing works
- [ ] TalkBack pass; Accessibility Scanner pass on every screen; largest font size
- [ ] Delete account end to end
- [ ] No screenshots possible while signed in (expected)

## After release
- [ ] Upload `mapping.txt` for the version to Play Console
- [ ] Watch vitals (crashes, ANRs) and backend logs for 4xx/5xx
- [ ] Keep the previous `.aab` and the matching backend version available for rollback
