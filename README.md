# XyCore 0.3.0

XyCore is a Paper 1.12.2 build 1620 core plugin for an RPG/MMO server stack. It is intended for the RPG main server only; the Velocity login server does not need XyCore.

## What Core Owns

- Player data sessions and module data storage for XyJob, XyForge and later Xy plugins.
- YML, SQLite and MySQL/MariaDB storage, with HikariCP for JDBC backends.
- Vault economy bridge.
- PlaceholderAPI expansion and dynamic placeholder namespaces.
- AttributePlus read bridge and direct attribute source write/remove bridge.
- DragonCore GUI, key, variable and client packet bridge.
- Vanilla and MythicMobs item provider bridge.
- Built-in optional modules controlled from `config.yml`.

Gameplay systems such as job leveling, forging recipes, profession perks and rune carving should live in their own plugins. XyCore provides stable services and module infrastructure.

## Module System

Main switches live in `plugins/XyCore/config.yml`:

```yaml
modules:
  lore-command-bind: true
  world-protect: false
  kit: false
  nickname: false
  script: false
```

When a module is enabled, XyCore creates its config under `plugins/XyCore/modules/`. Disabling a module stops its listener/task lifecycle but does not delete its config.

Current module configs:

```text
plugins/XyCore/modules/LoreCommandBind.yml
plugins/XyCore/modules/world-protect.yml
plugins/XyCore/modules/world-permission.yml
```

Use `/xycore modules` to view module states.

Module config files are created only when the module is enabled in `config.yml`. When a module is turned off and `/xycore reload` is executed, XyCore unloads that module and deletes its module config file. Server shutdown unloads modules but does not delete config files.

## LoreCommandBind

LoreCommandBind was rewritten in 0.3.0. It keeps the good parts of FishLore: simple lore matching, left/right click triggers, cooldown, item consume, PlaceholderAPI conditions and AttributePlus temporary attributes. It changes the unsafe parts: commands are only loaded from server config, `op:` style execution is blocked by default, and dangerous console commands are filtered.

Example:

```yaml
rules:
  forge_menu:
    enabled: true
    match:
      lore: '&e右键打开锻造台'
      mode: CONTAINS
    actions:
      - RIGHT_CLICK_AIR
      - RIGHT_CLICK_BLOCK
    execute:
      executor: PLAYER
      commands:
        - 'xyforge open {player}'
      cooldown-ms: 500
      consume: false
```

Supported command prefixes:

```text
player:command
console:command
op:command
[player] command
[console] command
[op] command
```

`op:` and `[op]` only work when `security.allow-temporary-op: true`. Keep it disabled unless you fully trust every rule in the file.

Old 0.2.x `plugins/XyCore/LoreCommandBind.yml` is migrated to `plugins/XyCore/modules/LoreCommandBind.yml` the first time the module creates its new config.

For easier migration from FishLore, `LoreCommandBind.yml` also accepts root-level rule sections such as `example1:` when no `rules:` or `binds:` section exists.

## WorldProtect

WorldProtect absorbs the useful part of AntiBuildX: per-world protection for build actions and hanging entities. The config uses readable names instead of short flags.

It can block:

- Block break and place.
- Bucket fill and empty, including water/lava/fish buckets handled by Bukkit events.
- Painting and item frame place/break.
- Item frame interaction.
- Armor stand place/break/interaction.
- Optional farmland trampling.

Example:

```yaml
worlds:
  world:
    enabled: true
    deny:
      block-break: true
      block-place: true
      bucket-fill: true
      bucket-empty: true
      hanging-break: true
      hanging-place: true
      item-frame-interact: true
      armor-stand-break: true
      armor-stand-place: true
      armor-stand-interact: true
```

Global bypass permission:

```text
xycore.worldprotect.bypass
```

Each world can also define its own `bypass-permission`.

WorldProtect messages use the global prefix from `config.yml -> messages.prefix` by default. For example, the default block break denial displays as:

```text
[XyCore]该世界无法破坏方块
```

The module can disable or override this prefix in `modules/world-protect.yml` with `settings.use-prefix` and `settings.prefix`.

## WorldPermission

WorldPermission gives temporary Bukkit permissions while a player is standing in configured worlds. It is designed for setups where EssentialsX or the permission plugin already does the real command permission checks.

Recommended pattern:

```text
Do not give players essentials.tpa globally.
Grant essentials.tpa only in the home world through WorldPermission.
Do not grant it in dungeon worlds.
```

Example:

```yaml
worlds:
  home_world:
    enabled: true
    grant-permissions:
      - essentials.tpa
      - essentials.tpahere
      - essentials.sethome
      - essentials.home

  dungeon_world:
    enabled: true
    grant-permissions: []
```

When the player leaves `home_world`, XyCore removes the temporary permission attachment. The module also supports `deny-permissions` for special cases, but the cleaner setup is to avoid global grants and only grant permissions in worlds where they should work.

## Soft Dependencies

Vault, PlaceholderAPI, MythicMobs, AttributePlus and DragonCore are soft dependencies. Missing soft dependencies do not stop XyCore from starting; unavailable bridges return safe empty implementations.

## Commands

```text
/xycore status
/xycore modules
/xycore reload
/xycore save [all|player]
/xycore info <player>
```

`/xycore reload` reloads Core config and reloadable Xy extensions. It does not run Bukkit `/reload`.

## Version Notes

### 0.3.0

- Added built-in module manager with clean enable, reload and disable lifecycle.
- Moved feature configs to `plugins/XyCore/modules/`.
- Module configs are now generated only while their module is enabled and deleted when the module is turned off by config reload.
- Rewrote LoreCommandBind as a module and fixed the 0.2.x usability issue.
- Added left/right click trigger control, PlaceholderAPI conditions, temporary AttributePlus source effects and safer command execution.
- Added WorldProtect module for per-world build, bucket, hanging entity, item frame and armor stand protection.
- Added WorldPermission module for per-world temporary permission grants, suitable for EssentialsX permissions such as `essentials.tpa`.
- Added `/xycore modules`.

### 0.2.0

- Added DragonCore bridge.
- Added AttributePlus direct attribute source write/remove bridge.
- Added official PlaceholderAPI expansion.
- Added HikariCP-backed JDBC storage.
- Added first LoreCommandBind implementation.

### 0.1.0

- Added player data sessions and module storage.
- Added YML, SQLite and MySQL/MariaDB storage abstraction.
- Added Vault economy bridge.
- Added basic item provider and MythicMobs soft bridge.

## Build

The source includes the local compile-time jars needed for Paper 1.12.2 and HikariCP:

```text
gradlew.bat clean build
```

Output:

```text
build/libs/XyCore-0.3.0.jar
```
