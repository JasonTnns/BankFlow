# Privacy and data safety

BankFlow is a demo app with no real money. These notes describe what the code does, so a store listing and
a privacy policy can be written truthfully.

## Data the server stores

| Data | Why | Where | Kept until |
|---|---|---|---|
| Email, full name | Sign in, show your name | Server database | Account deletion (replaced by a placeholder) |
| Password | Sign in | Server, only as a bcrypt hash | Account deletion |
| Theme, transfer limit | Settings | Server | Account deletion |
| Accounts, balances, transactions, counterparty names | The service itself | Server, in an append-only ledger | Kept in anonymised form (see below) |
| Saved recipients (nickname, account number) | Convenience | Server | Account deletion |
| Sessions: hashed refresh token, device model name, times | Stay signed in | Server | Logout, expiry (30 days), or account deletion |

The access log (production) holds method, path, status and duration only: no IP address, user, token,
query string or body.

## Data on the device

| Data | Protection |
|---|---|
| Session tokens, last dashboard and history (offline copy) | Encrypted (AES-256-GCM). The key is wrapped by the Android Keystore; with app unlock on, by a key that needs your fingerprint, face or device PIN |
| App unlock on/off | Plain preference |
| Language, theme | Stored on the server / follows the system |

Backups are disabled, screenshots and the recent-apps preview are blocked while signed in, and the offline
copy is wiped on logout.

## Not collected

No analytics, advertising, crash-reporting or other third-party SDKs. No location, contacts, camera,
microphone, files or advertising id. Biometrics are checked by Android; the app never receives them.

## Deletion

Profile > Delete account (password required). The server removes the login, sessions and saved recipients,
closes the accounts, pays out any balance (in this demo back to the bank), and replaces your name and email
with placeholders. The transaction ledger is financial record-keeping and is retained, without your name:
other users' history shows "Deleted user".

## Suggested Google Play "Data safety" answers

- Data collected: **Personal info** (name, email) and **Financial info** (account balances and transaction
  history: "user payment info" is not collected, "other financial info" is).
- Purpose: App functionality, account management. Not for advertising, analytics or personalisation.
- Shared with third parties: **No.**
- Encrypted in transit: **Yes** (HTTPS only in release builds).
- Users can request deletion: **Yes, in the app** (and state the retained-ledger exception in the policy).
- Data collected is **required** (the app cannot work without an account).

## Before going live (a real service, not this demo)

A real deployment needs a privacy policy URL, a named controller and contact, a retention schedule agreed
with your regulator, breach procedures, and a legal review of the retained ledger.
