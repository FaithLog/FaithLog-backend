# Design System - FaithLog

## Product Context

- **What this is:** FaithLog is a mobile-first campus operations app for church communities. It helps members complete weekly devotion checks, worship and meeting polls, coffee duty, charges, payment status, and reminders.
- **Who it is for:** Regular members, campus operators, coffee duty members, and service admins.
- **Project type:** React Native mobile app with admin-heavy operational flows.
- **Design posture:** FaithLog should feel like a warm campus operations notebook that quietly organizes what each person needs to do.

This is not a church poster, devotional content app, or generic SaaS dashboard. It is a practical weekly rhythm tool. Warm, calm, precise.

### MVP Scope Note

- Meal or lunch planning is excluded from MVP.
- Food-related flows can be added later as a separate expansion.
- Current poll examples should focus on worship, meeting schedules, duties, and operational decisions.

## Memorable Thing

When someone sees FaithLog for the first time, they should remember:

> "This app makes weekly campus responsibilities feel calm and obvious."

Every visual decision should serve that line.

## Design Shotgun Directions

These are the three explored directions. Use Direction A as the source of truth unless the user explicitly chooses another path.

### A. Warm Campus Notebook

- Pastel cream background, sage actions, peach and butter accents.
- Feels like a clean weekly planner, not a corporate admin tool.
- Best fit for the product because members and leaders need low-friction repetition every week.

### B. Quiet Admin Console

- More neutral, denser, closer to a professional operations dashboard.
- Stronger for admin screens, weaker for member warmth.
- Useful as a constraint for tables, charge lists, and dashboard density.

### C. Soft Community App

- More rounded, friendly, and emotionally warm.
- Good for onboarding and member home screens.
- Risk: can become too cute, which weakens trust for payments, fines, and admin actions.

**Decision:** Direction A wins, with B's density rules for admin screens and a small amount of C's softness for member-facing states.

## Aesthetic Direction

- **Direction:** Warm Campus Notebook
- **Mood:** Calm, accountable, gentle, organized.
- **Decoration level:** Intentional, not expressive.
- **Visual metaphor:** A weekly planner on warm paper, with small operational signals layered on top.

Avoid:

- Church poster aesthetics.
- Overly cute diary app styling.
- Purple-blue gradients.
- Generic SaaS hero layouts.
- Centered everything.
- Bubble-radius cards everywhere.
- Decorative illustrations that do not help the task.

## Color System

Pastels are for surfaces, grouping, and emotional warmth. Text and primary actions must stay deep enough for real mobile readability.

### Core Palette

| Token | Hex | Usage |
| --- | --- | --- |
| `cream-50` | `#FFF9F0` | App background |
| `cream-100` | `#F7EFE2` | Section background |
| `paper` | `#FFFFFF` | Cards, sheets, inputs |
| `ink` | `#24342D` | Primary text |
| `ink-muted` | `#66746C` | Secondary text |
| `line` | `#E6DCCF` | Borders, dividers |
| `sage-600` | `#315B46` | Primary action, active nav |
| `sage-500` | `#5F9276` | Buttons, progress, selected states |
| `sage-100` | `#DDECE2` | Soft selected background |
| `peach-400` | `#E9A28F` | Warm secondary accent |
| `peach-100` | `#F8DFD6` | Gentle alert or highlight |
| `butter-300` | `#F4D58D` | Weekly highlight, pending tasks |
| `sky-300` | `#A9D5DC` | Informational accent |
| `lavender-200` | `#D8C8E8` | Rare tertiary accent only |

### Semantic Colors

| Token | Hex | Usage |
| --- | --- | --- |
| `success` | `#3F7D5A` | Submitted, paid, complete |
| `warning` | `#B7791F` | Pending, due soon, partial |
| `danger` | `#B5534A` | Missing, unpaid, failed |
| `info` | `#447B86` | Poll open, neutral information |

### Color Rules

- Never place pastel text on pastel backgrounds.
- Use deep text on pastel fills.
- Use `sage-600` for the primary action, not peach.
- Use peach for warmth and emphasis, not destructive states.
- Use red/danger sparingly. Missing devotion and unpaid charge are important, but the app should not shame users.
- Admin dashboards may use more white and line colors to improve scanning.

## Typography

### Fonts

- **Display:** Fraunces
  - Use for app name, section openers, and rare emotional headings.
  - Do not use for dense labels, tables, or list rows.
- **Korean UI:** Noto Sans KR
  - Use for Korean app text, labels, inputs, buttons, lists, and empty states.
  - This is the implementation font for the current Figma refresh because it renders Korean cleanly and is available in the file.
- **Latin UI:** Source Sans 3
  - Use for English helper text, product notes, and mixed-language labels when needed.
  - Clean enough for operations, warm enough for a community app.
- **Data:** Geist
  - Use for money, counts, dates, percentages, and admin metrics.
  - Must use tabular numbers where possible.

### Type Scale

| Role | Size | Weight | Line Height |
| --- | ---: | ---: | ---: |
| Display | 32 | 650 | 38 |
| Screen title | 24 | 650 | 30 |
| Section title | 18 | 650 | 24 |
| Card title | 16 | 650 | 22 |
| Body | 15 | 400 | 22 |
| Body small | 14 | 400 | 20 |
| Label | 13 | 600 | 18 |
| Caption | 12 | 500 | 16 |
| Metric | 28 | 650 | 34 |

### Typography Rules

- Korean text should be direct and short.
- Do not write explanatory blocks inside the app.
- Prefer labels like `이번 주`, `미제출`, `미납`, `투표 중`, `납부했어요`.
- Use numbers as anchors. `6명 미제출`, `3건 미납`, `80% 제출`.
- Member-facing copy should be warm. Admin copy should be precise.

## Spacing

- **Base unit:** 4px
- **Density:** Comfortable for member screens, compact for admin screens.

| Token | Value |
| --- | ---: |
| `space-1` | 4 |
| `space-2` | 8 |
| `space-3` | 12 |
| `space-4` | 16 |
| `space-5` | 20 |
| `space-6` | 24 |
| `space-8` | 32 |
| `space-10` | 40 |
| `space-12` | 48 |

### Spacing Rules

- Mobile screen horizontal padding: 20px.
- Card internal padding: 16px for member screens, 12px for dense admin rows.
- List row height: 56px minimum.
- Touch targets: 44px minimum.
- Keep admin summaries dense but not cramped.

## Layout

### Approach

Hybrid:

- Member screens use a calm planner layout.
- Admin screens use a grid-disciplined operations layout.
- Both share the same color, spacing, and component primitives.

### Screen Structure

Every main mobile screen should follow this order:

1. Current campus and week context.
2. Primary status or next action.
3. Task groups.
4. History or secondary details.

Do not start with navigation chrome or marketing copy. The app is for repeated weekly use.

### Navigation

- Bottom tabs for member app:
  - Home
  - Devotion
  - Polls
  - Charges
  - My
- Admin entry can be a role-gated switch or tab item.
- Current tab must be visually obvious without relying only on color.

## Component System

### App Header

Use:

- Campus name
- Week label
- Optional notification icon

Example:

```text
분당 1캠
6월 2주차
```

Avoid large branded headers after onboarding. Users are here to finish tasks.

### Task Card

Use for the member home screen.

States:

- `complete`: sage soft background, check icon, calm title.
- `pending`: butter soft background, clear action.
- `missing`: peach soft background, direct but gentle wording.
- `disabled`: cream background, muted text.

Structure:

```text
[status icon] 이번 주 경건생활
3일 체크됨 · 제출 전
[제출하기]
```

### Weekly Devotion Grid

Use a 7-day horizontal row.

Each day cell:

- Day label.
- Three small check indicators for 큐티, 기도, 말씀.
- Selected day uses sage outline and white fill.
- Missing day uses quiet border, not alarm styling.

Do not make this look like a habit tracker game. No streak flames, badges, or ranking.

### Poll Card

Structure:

```text
수요예배 참석 투표
오늘 18:00 마감 · 24/30 응답
[참석] [불참] [미정]
```

Rules:

- Options should look tappable without hover.
- Selected option uses sage fill.
- Deadline should be visible but not loud.

### Charge Item

Structure:

```text
2026년 6월 2주차 벌금
큐티 2회 부족, 지각 5분
2,500원
[납부했어요]
```

Rules:

- Amount uses Geist.
- Account snapshot appears in a collapsed details area or bottom sheet.
- `납부했어요` appears only for `UNPAID`.
- Paid state should feel resolved, not celebratory.

### Admin Metric Strip

Use 3 to 4 compact metrics.

Examples:

- `24/30 제출`
- `6명 미제출`
- `5명 미응답`
- `54,000원 미납`

Metric colors:

- Good: sage.
- Pending: warning amber.
- Needs action: danger red-brown.

### Admin List

Use dense rows, not big cards.

Row structure:

```text
김OO
미제출 · MEMBER
[알림]
```

Rules:

- One clear action per row.
- Use filters at top: `전체`, `미제출`, `미응답`, `미납`.
- Do not hide operational actions inside kebab menus unless there are more than 3 actions.

### Empty State

Tone:

- Member: warm and brief.
- Admin: precise and reassuring.

Examples:

- `이번 주 할 일이 없어요.`
- `미제출자가 없습니다.`
- `진행 중인 투표가 없습니다.`

Do not add long encouragement text. Nice, but not useful.

## Screen Recipes

### Member Home

Purpose: Show what I need to do this week.

Layout:

1. Header: campus, week, notification.
2. Primary summary: `이번 주 할 일 3개`.
3. Task cards:
   - 경건생활 제출.
   - 진행 중 투표.
   - 미납 청구.
4. Recent status list.

Must answer in 3 seconds:

- Did I submit devotion?
- Do I have a poll to answer?
- Do I owe money?

### Devotion Screen

Purpose: Check daily devotion and submit weekly record.

Layout:

1. Week selector.
2. 7-day grid.
3. Selected day checklist.
4. Saturday late minutes input.
5. Penalty preview.
6. Submit button.

Primary CTA:

- `이번 주 제출하기`

### Polls Screen

Purpose: Respond to active polls quickly.

Layout:

1. Active poll list.
2. Poll detail sheet.
3. Option buttons.
4. Response status.
5. Deadline.

Coffee poll rules:

- Show price directly on each option.
- Zero-price option should be clear: `안 마셔요`.
- Charge creation should be explained by UI structure, not a paragraph.

### Charges Screen

Purpose: Understand what I owe and mark payment complete.

Layout:

1. Summary chips: total, unpaid, paid.
2. Category filter: all, penalty, coffee.
3. Charge list.
4. Account detail bottom sheet.
5. Paid action.

### Admin Dashboard

Purpose: Find who needs action this week.

Layout:

1. Week selector.
2. Metric strip.
3. Action groups:
   - Devotion missing.
   - Poll missing.
   - Unpaid charges.
4. Send reminder action.

Do not use oversized hero cards here. Admin users need scan speed.

## Border Radius

| Token | Value | Usage |
| --- | ---: | --- |
| `radius-sm` | 4 | Inputs, chips, table cells |
| `radius-md` | 8 | Cards, buttons, sheets |
| `radius-lg` | 12 | Large panels only |
| `radius-full` | 999 | Pills, avatars |

Rule: Default card radius is 8px. Avoid rounded bubble UI.

## Icons

Use simple line icons. Prefer lucide equivalents when implementing.

Suggested icons:

- Home: `Home`
- Devotion: `BookOpenCheck`
- Polls: `Vote`
- Charges: `ReceiptText`
- My: `User`
- Admin: `LayoutDashboard`
- Reminder: `Bell`
- Paid: `CheckCircle2`
- Missing: `AlertCircle`
- Coffee: `Coffee`

Icons should support scanning. Do not use icons as decoration.

## Motion

- **Approach:** Minimal functional.
- **Durations:** 120ms for press feedback, 180ms for sheet open, 220ms for screen transitions.
- **Easing:** ease-out for enter, ease-in for exit.

Use motion for:

- Press feedback.
- Checkbox transitions.
- Bottom sheet open/close.
- Submission success.

Do not use:

- Bouncy animations.
- Confetti.
- Long onboarding animation.
- Scroll-driven decorative motion.

## Accessibility

- Normal text must meet WCAG AA contrast.
- Pastel fills require deep text.
- Touch targets must be at least 44px.
- Do not communicate status by color alone.
- Every selected state needs shape, icon, or text.
- Dynamic type should not break cards or buttons.
- Long Korean names must truncate gracefully with full name available in detail.

## Figma Rewrite Brief

If rebuilding the current Figma screens, keep only the feature structure:

- Member home tasks.
- Devotion weekly check.
- Poll response.
- Charge/payment.
- Admin dashboard.
- Missing member lists.

Replace the visual layer with:

- Warm cream app background.
- Sage primary actions.
- Dense but quiet admin lists.
- 8px cards.
- Fraunces only for expressive headings.
- Noto Sans KR for Korean UI.
- Source Sans 3 for Latin UI and product notes.
- Geist for numbers and money.

The Figma should not inherit old colors, old card styles, or decorative choices from the current rough design unless explicitly approved.

## Do / Don't

### Do

- Make the next action obvious.
- Use calm warm backgrounds.
- Let numbers and status labels carry admin screens.
- Keep member screens friendly and brief.
- Keep admin screens dense and scannable.

### Don't

- Make it look like a sermon slide.
- Make it look like a generic SaaS dashboard.
- Use pastel text.
- Use big decorative gradients.
- Use large marketing-like hero sections.
- Over-round every component.
- Hide core actions behind menus.

## Decisions Log

| Date | Decision | Rationale |
| --- | --- | --- |
| 2026-06-12 | Use Warm Campus Notebook as the primary direction | Fits "quietly organizes weekly responsibilities" while staying warm and usable |
| 2026-06-12 | Use pastel colors only for surfaces and status grouping | Keeps warmth without sacrificing readability |
| 2026-06-12 | Use Fraunces, Source Sans 3, and Geist | Gives FaithLog a distinct face while preserving operational clarity |
| 2026-06-12 | Add Noto Sans KR as Korean UI font | Matches the Figma refresh and improves Korean interface rendering |
| 2026-06-12 | Use 8px default card radius | Avoids overly cute mobile UI while still feeling soft |
| 2026-06-12 | Admin screens use lists over large cards | Operators need scanning speed more than visual drama |
