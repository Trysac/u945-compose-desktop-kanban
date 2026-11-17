# Project record

## Objective

Kanban board core with keyboard-only drag and drop and screen reader announcements, WIP limits, undo and redo, and crash-safe persistence in a versioned line format.

## Scope

- In scope: The UI-free domain model, keyboard drag controller, undo/redo history, and crash-safe persistence.
- Out of scope: The Compose UI, mouse drag, multiple boards, sync or sharing, labels, and due dates.

## Timeline

| Date | Event |
|---|---|
| 2026 | Repository created from the factory utility template. |

## Effort metrics

| Metric | Value |
|---|---|
| Tokens (input / output) | 1939112 / 34543 |
| Hours (human) | pending measurement |
| Cost (USD) | 0.8208 |

## Factory run u945 (measured from API usage)

| Date (UTC) | Phase | Model | Batch | Input | Output | Cache write | Cache read | Cost (USD) |
|---|---|---|---|---|---|---|---|---|
| 2026-11-17 | generation | google/gemini-4-argon | yes | 6490 | 18063 | 0 | 0 | 0.3872 |
| 2026-11-17 | fix-1 | google/gemini-3.8-flash | no | 142428 | 10351 | 0 | 796782 | 0.2054 |
| 2026-11-17 | fix-2 | google/gemini-3.8-flash | no | 67362 | 3527 | 0 | 446500 | 0.0972 |
| 2026-11-17 | audit | google/gemini-3.8-flash | no | 125132 | 1285 | 0 | 332897 | 0.1236 |
| 2026-11-17 | docs | google/gemini-3.1-flash-lite | no | 21521 | 1317 | 0 | 0 | 0.0074 |

Total: $0.8208. Prices: vertex line, as measured by its adapter. Providers serve the current version of each model id, so the date tells which version answered; versions, launch stages and price sources per line are in the factory's model register (docs/MODELS.md).
