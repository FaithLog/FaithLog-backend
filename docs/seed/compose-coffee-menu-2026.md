# Compose Coffee Menu Seed 2026

Issue #37 uses `src/main/resources/seed/compose-coffee-menu-2026.csv` as the reusable seed source for `coffee_menu_catalog`.

- Approved by user: 2026-06-19
- Source basis: user-provided "컴포즈커피 메뉴 가격 2026년 최신 버전" text list
- Verification note: official Compose Coffee website access was blocked during implementation, so this is recorded as a user-approved override rather than independently verified official data.
- Re-seed behavior: the application seed runner reads the CSV and upserts menu rows by `menu_code`.

Important price split:

- `AMERICANO_HOT` = `아메리카노`, 1,500 KRW
- `AMERICANO_ICE` = `아이스 아메리카노`, 1,800 KRW
