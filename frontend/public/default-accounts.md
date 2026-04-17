# Default Mock Accounts

These accounts are for **frontend mock mode** (no backend/database required).

## Quick Accounts

| Role | Email | Password |
| --- | --- | --- |
| SUPER_ADMIN | super@filestorage.local | 123456 |
| ADMIN_TENANT | admin@filestorage.local | 123456 |
| MEMBER | user@filestorage.local | 123456 |

## How role is resolved in mock mode

Current mock auth infers role from email:

- Email contains `super` -> `SUPER_ADMIN`
- Email contains `admin` -> `ADMIN_TENANT`
- Otherwise -> `MEMBER`

## Notes

- This file is intentionally in `public` so it does not affect application logic.
- You can open it at:
  - `/default-accounts.md`
- Mock auth can be disabled by setting:
  - `NEXT_PUBLIC_MOCK_AUTH=false`
