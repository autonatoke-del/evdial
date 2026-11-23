# Security policy

Do not open a public issue containing SIP passwords, personal numbers, customer contacts, call logs, photos or private keys.

Report security issues privately to the repository owner via GitHub security advisories if available. Do not send account secrets in plain text. Reproduce issues against a disposable test account and redact logs.

EVDial has broad Android permissions because it can be a default dialer. Grant only the permissions necessary for the enabled features. Website preview APKs verify metadata and original signing identity, while the Android installer performs final signature validation; no silent install is supported.
