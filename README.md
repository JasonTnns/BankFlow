# BankFlow

A demo mobile banking app: Android client (Kotlin, Jetpack Compose) and a Node.js backend (SQLite).
The backend is the source of truth for every balance. The app never computes money itself.

## Features (Release 1)

- Register, log in, log out; encrypted session that survives restarts; automatic token refresh
- Dashboard with balances and recent activity
- Account history: cursor pagination, search, filters (type, category, dates, amounts)
- Transaction details
- Transfers to other users or between your own accounts, with recipient confirmation,
  duplicate-transfer protection, and honest handling of timeouts

## Architecture

| Layer | Contents |
|---|---|
| `presentation` | Compose screens, MVI ViewModels (state in a `StateFlow`, one-shot effects in a `Channel`) |
| `domain` | Models, `AppError`/`AppResult`, repository interfaces, use cases (pure Kotlin) |
| `data` | Ktor client (bearer auth with refresh rotation), DTO mappers, encrypted session store |
| DI | Koin Annotations, validated at build time |

Money is always an integer number of cents. The backend stores it in a double-entry ledger; debit
and credit commit in one transaction, and a client-generated idempotency key makes retries safe.

## Run it

**Backend** (Node 20+), from the backend folder:

```bash
npm install
npm run seed -- --reset    # demo data: alice@example.com and bob@example.com, password Passw0rd123
npm start                  # http://localhost:3000, API docs at /docs
npm test
```

**Android** (JDK 17): open the project in Android Studio and run on an emulator. The debug build
talks to `http://10.0.2.2:3000/api/v1/`. On a physical phone, change `API_BASE_URL` in
`app/build.gradle.kts` to your PC's LAN address.

```bash
./gradlew testDebugUnitTest          # JVM unit tests
./gradlew connectedDebugAndroidTest  # Compose UI tests (needs an emulator)
```

## Security notes

- Passwords are hashed with bcrypt; refresh tokens are stored only as SHA-256 hashes and rotate on
  every use. Reusing an old one revokes every session of that user.
- Android stores the session encrypted with an AES-GCM key held in the Android Keystore.
- HTTP logging never includes request bodies. Backups are disabled. Cleartext HTTP is allowed in
  debug builds only.
- This is a demo: there is no real money, KYC, or production hardening.
