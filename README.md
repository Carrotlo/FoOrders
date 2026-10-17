# FoOrders

[![Modrinth](https://img.shields.io/badge/Download-Modrinth-1bd96a?logo=modrinth&logoColor=white)](https://modrinth.com/plugin/foorders) [![Versions](https://img.shields.io/badge/Supported_versions-Modrinth-1bd96a)](https://modrinth.com/plugin/foorders/versions) [![Discord](https://img.shields.io/badge/Support-Discord-5865f2?logo=discord&logoColor=white)](https://discord.gg/kEF77QCfu7)

Player-created item orders with delivery, payments, history, and GUIs.

*Developed by Carrotio.*

## Features

- Lets players create item orders backed by Vault money.
- Provides GUI flows for item selection, pricing, delivery, claims, and management.
- Supports search, personal orders, history, and administrator review.
- Includes item and name blacklists plus optional Discord webhooks.
- Keeps GUI, dialog, message, and sound behavior configurable.

## Download and dependencies

Download the latest compatible version from [Modrinth](https://modrinth.com/plugin/foorders). Check the [versions page](https://modrinth.com/plugin/foorders/versions) for supported Minecraft versions and server platforms.

- **Required:** `Vault`
- **Optional integrations:** None

## Support

For help or issue reports, join the [Carrotio Discord server](https://discord.gg/kEF77QCfu7).

<details>
<summary><strong>Commands and permissions</strong></summary>

### Commands

- `/order [<search>]` — Opens the orders menu Aliases: `/orders`.
- `/orderadmin <reload|version|editor|history <player>>` — Admin command for FoOrders Aliases: `/ordersadmin`.

### Permissions

- `foorders.use` — Allows using the /order menu Default: `true`.
- `foorders.admin` — Allows using FoOrders admin commands and editor Default: `op`.
- `foorders.receive-created-order-announcements` — Allows seeing chat announcements when new orders are created Default: `true`.
- `foorders.maxorders.*` — Allows setting max order limits via foorders.maxorders.<amount> Default: `op`.

</details>

<details>
<summary><strong>PlaceholderAPI</strong></summary>

This plugin does not register public PlaceholderAPI placeholders.

</details>

<details>
<summary><strong>Configuration</strong></summary>

- `config.yml` — Main behavior and feature settings.
- `messages.yml` — Player-facing messages and interface text.
- `sounds.yml` — Configurable interaction sounds.
- `dialogs/*.yml` — Native dialog layouts and text.

For amount, price, and search input labels, edit `target-label` in `dialogs/amount.yml`, `dialogs/money.yml`, or `dialogs/search.yml`. The bundled native field label and first chat-prompt line use `{target}`, so this one value changes both. You can still write independent text in `field-label` or `fallback.lines`. Older `messages.yml:chat-input.targets.*` entries are legacy; customized values are carried into unchanged dialog target labels during the v34 upgrade, and v37 connects unchanged older field/prompt defaults to `{target}` without replacing owner-written text. Those message keys are no longer read at runtime.

Those three Paper text dialogs also expose `columns` (1 or 2) for the action-button layout. v36 adds the new field to older owner dialog files once; removing it on the current schema disables that native dialog instead of regenerating a hardcoded layout. Chat input remains available.

The custom-item picker displays template lore only when `guis/item-select.yml:items.custom-entry.lore` includes `{template_lore}`. An empty or removed lore list remains empty on upgrade. v35 also restores earlier empty/removed lists that v30 had turned into that token, but only when the pre-v30 backup proves the original value and the current list still exactly matches the generated line.
- `guis/*.yml` — GUI layouts, slots, labels, and lore.

Main config areas: `max-order-per-player`, `order-menu-refresh-cooldown-ms`, `order-tax`, `file-logging`, `native-dialogs`, `announce-created-orders-in-chat`, `history`, `discord-webhook`, `blacklist-items`, `blacklist-names`.

</details>

<details>
<summary><strong>Developer API</strong></summary>

This plugin does not expose a public developer API.

</details>
