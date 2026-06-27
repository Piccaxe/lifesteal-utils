# Piccaxe's Lifesteal Utils (Fabric, Minecraft 1.21.11)

A **client-side** quality-of-life mod for Lifesteal SMP. Works on any server
(vanilla/Realms included) since nothing is sent to the server.

## Features

| Feature | What it does |
|---|---|
| **Clean Heart HUD** | Numeric `❤ 15 / 20` that stays readable past 10 hearts. Green when full, yellow when hurt, red when low. |
| **Totem counter** | Shows totems in your inventory with the item icon; flashes red when at/below the warning threshold. |
| **Player proximity alert** | Sound + centered banner when another player *enters* your radius (default 64 blocks). Fires on entry, not every tick. |
| **Player notifier** | Wider early warning when a player **enters your render distance** (loads into the world). Channels: chat message, sound, on-screen banner, and Discord webhook — each toggleable. Discord channel off by default. |
| **Health bars** | Color-coded health bar + HP number floating above each living entity within range, through walls. Shows the **real synced health** (and max-health, so lifesteal heart counts are correct) for both mobs and players. Optionally (`healthBarDamageEstimate`) a player's bar is pulled down to a damage-dealt estimate (`~`) when that's lower than the reported value — useful if a server under-reports player health. Range, players-only, and the estimate are configurable. |
| **Coordinates** | `XYZ` + facing (N/E/S/W). |
| **Potion HUD** | Compact list of your active effects with level + time remaining (green = beneficial, red = harmful, ∞ for infinite). Draggable. Off by default. |
| **Inventory HUD** | Shows your main inventory (27 slots) with item icons + counts, so you can see your stash without opening it. Draggable. Off by default. |
| **Compass / Direction HUD** | A scrolling compass strip showing your heading (cardinal + degrees) with a center marker, drawn through the HUD system so it's draggable. Customizable: **minimal mode** (heading text only), **15° tick marks**, a **background box**, **width**, and **colors** (main / marker / north). Off by default. See the **Compass** screen in settings. |
| **Death waypoint** | Records where you last died and shows it with live distance; says the dimension if you're elsewhere. Survives relogs. |
| **Auto-reconnect** | After a disconnect/kick, counts down and rejoins the last server (configurable delay + attempt cap). Off by default. |
| **Visual tweaks** | Fullbright (see in the dark) and No Hurt-Cam (kills the damage screen-shake). |
| **Anti-Invis** | Reveals invisible players and mobs by drawing them **semi-transparent** (~50%), with an **" (invis)" tag added to their name** so you know they're invisible. Off by default. |
| **No Fog** | Removes fog (and its tint) independently for **water** (see clearly underwater), **lava** (see through it), and **biome/atmospheric** distance haze. Each toggles separately; all off by default. |
| **Armor swapper** | Auto-swaps armor sets by health, matching pieces by their **lore/name**. Below your low-HP threshold it equips a "defense" set; once you recover above the high-HP threshold it swaps back to your "normal" set. Each set has a keyword (matches any piece) plus optional per-slot overrides. Pieces are moved from your inventory like an auto-totem (only when no GUI is open). Off by default. Configure in **U → Tuning → Armor Swap…** or `/piccaxeutils armorswap`. |
| **Anti-Trickster** | Auto-undoes a server-side hotbar scramble. While no screen is open, if your hotbar becomes a pure reorder of its previous contents (a scramble), it instantly swaps everything back. Real changes (using/picking up items) are left alone. |
| **Anti-Sign** | Makes signs fully **click-through** while on: the crosshair passes through to the block behind, so you interact with what's behind a sign and never select/open it. Off by default. |
| **Armor stand bypass** | Makes armor stands click-through so you can interact past/through them. Off by default. |
| **Nether portal bypass** | Lets you keep **GUIs and chat open while standing in a nether portal** (vanilla force-closes them every tick) and removes the purple overlay/nausea. The teleport itself is server-side and unaffected. Off by default. |
| **Player outliner** | Colored glow outline on other players by their nametag/team color: **green = teammate, blue/aqua = ally, red = enemy**. Per-player manual overrides supported. Off by default. |
| **Trap outliner** | ESP boxes (through walls) around common trap parts within a radius — **pistons & sticky pistons, pressure plates, string (tripwire + hooks), and armor stands**. Toggle with a keybind or the settings button; radius and color configurable. Off by default. |
| **Loot chest outliner** | Draws colored boxes around nearby **ender chests** (loot chests), **visible through walls (ESP)**, with a distance label, an optional straight tracer, and an optional **walking-route path tracer** (A* around obstacles, no mining; handles water and ladders/climbables). Scans loaded chunks within a configurable radius. On by default. |
| **Hotbar editor** | A visual screen with two modes. **Swap Items:** click two slots to physically swap them (real, server-synced). **Remap Keys:** pick which physical slot each number key 1-9 selects, so you can reorganize and keep your muscle memory — the selection/highlight/packet all reflect the real slot (no desync, just key rebinding). Open with the keybind or `/piccaxeutils hotbar`; also `/piccaxeutils hotbar remap on`, `hotbar key <1-9> <1-9>`, `hotbar remap reset`. |
| **Auto-eat** | Eats a configured food (default golden apples) when your health drops to/below a threshold, then restores your slot. `/piccaxeutils autoeat on`, `autoeat hp <n>`, `autoeat item <keyword>`. Off by default. *(combat macro)* |
| **Screenshot on kill** | Auto-captures a screenshot when you get a kill and posts it to your death/kill Discord webhook. `/piccaxeutils killshot on` (needs `discord assign deathkill <webhook>`). Off by default. |
| **Chat tweaks** | Optional `[HH:mm]` timestamps and anti-spam (drops duplicate messages). `/piccaxeutils chat timestamps on`, `chat antispam on`. Off by default. |
| **Auto-totem** | Swaps a Totem of Undying into your offhand the instant it's gone. Off by default. *(combat macro)* |
| **Auto-tool** | While mining, switches to the fastest tool in your hotbar for that block. Off by default. |
| **Hitmarkers** | Crosshair X + click sound when you land a hit. Off by default. |
| **Combat tag HUD** | "⚔ In combat Ns" timer (refreshed when you deal/take damage) so you know when it's safe to log. Draggable. Off by default. |
| **Armor HUD** | Your 4 armor pieces with durability bars + a flashing "!" when a piece is low. Draggable. Off by default. |
| **FPS / Ping HUD** | Your FPS and server ping. Draggable. Off by default. |
| **Zoom** | Hold the Zoom key (default **C**, rebindable) to zoom in. |
| **Auto-clip** | Presses your recording app's clip hotkey when you get a kill (optionally on death too), so plays are saved automatically. Works with NVIDIA ShadowPlay (default `ALT+F10`), OBS replay buffer, Medal, etc. — set the hotkey to match. Off by default; configure with `/piccaxeutils autoclip on`, `autoclip hotkey <keys>`, `autoclip death on`, `autoclip test`. |
| **Death / Kill relay** | Auto-posts your **deaths** (💀) and **kills** (⚔️) to Discord by reading the server's death messages — only lines that involve you. Each direction toggles separately; optional ping on death. Off by default; assign a webhook with `discord assign deathkill <name>`. |
| **Heart tracker** | Lifesteal max-heart counter: announces in chat when you **gain/lose hearts**, keeps a session net total, shows a draggable **♥ hearts (±net)** HUD, and can **ping Discord on heart loss**. Off by default. |
| **Discord chat relay** | Forwards selected chat to a Discord channel via a webhook you provide — **team chat, whispers/DMs, mentions of you, and custom keywords** (each toggleable). Off by default; needs a webhook URL. |

## Auto-updater

Checks your GitHub repo's **latest release** on launch; if it's newer than the installed jar it downloads the release's `.jar` and **swaps it in when you close the game** (a running jar can't replace itself on Windows, so the swap runs on exit). Friends just install once and get future updates automatically.

Setup:
1. Put this mod in a public GitHub repo and publish releases that **attach the built jar** (tag like `v1.0.1`). Bump the version in `gradle.properties` so the installed jar reports the new number.
2. In game: `/piccaxeutils update repo <owner/repo>` (once; it's saved to config, so it ships with the configs you share).
3. `/piccaxeutils update on|off` to enable/disable, `update apply on|off` (off = just notify with a link), `update check` to check now.

> The updater only contacts the repo you set, over HTTPS. The on-exit swap spawns a short OS command to move the new jar into place; some antivirus may flag that step.

## Configuring

Three ways, all interchangeable — settings persist to
`.minecraft/config/piccaxes-lifesteal-utils.json`.

- **Settings screen:** press **U** (rebindable in Controls → *Piccaxe's Lifesteal Utils*).
- **Keybinds:** *Open Settings* (U), *Open HUD Editor* (unbound), *Toggle All Features* (unbound), *Toggle Fullbright* (unbound), *Toggle Anti-Trickster* (unbound), *Toggle Trap Outlines* (unbound). Bind them in Options → Controls → *Piccaxe's Lifesteal Utils*.
- **Move / resize / align the HUD:** open the **HUD Editor** (keybind, or `/piccaxeutils hudedit`): **drag** to move (snaps to screen edges/centers **and to other HUD elements'** edges/centers, cyan guides; hold **Shift** or toggle the **Snap** button / `/piccaxeutils hudsnap off` to place freely), **scroll** to resize (shows ×N), **right-click** to cycle alignment (`[L]`/`[C]`/`[R]` — right-aligned grows leftward). "Reset Positions" keeps scales/alignment. Commands: `/piccaxeutils hudscale <element|all> <0.25-4>` (`all` = master multiplier), `/piccaxeutils hudalign <element> <left|center|right>`.
- **Commands:** `/piccaxeutils` (alias `/piccaxe`):
  - `/piccaxeutils` — open the settings GUI
  - `/piccaxeutils status` — show all toggle states
  - `/piccaxeutils on|off|toggle` — master switch
  - `/piccaxeutils <feature> on|off|toggle` — where `<feature>` is `heart`, `totem`,
    `proximity`, `coords`, `death`, `reconnect`, `fullbright`, `hurtcam`, `trickster`,
    `antisign`, `armorstand`, `portal`, `antiinvis`
  - `/piccaxeutils armorswap` — open the armor-swapper screen; `armorswap on|off|toggle`, `armorswap lowhp <n>`/`highhp <n>`, `armorswap defense|normal keyword|helmet|chest|legs|boots <text>`
  - `/piccaxeutils nofog water|lava|biome on|off|toggle` — remove fog underwater / in lava / in the distance
  - `/piccaxeutils direction on|off|toggle` — the compass HUD (drag to position in the HUD editor)
    - `direction width <40-400>`, `direction minimal|ticks|background on|off|toggle`
    - `direction color|marker|north <hex>` — set the strip/heading, center-marker, and north colors (e.g. `FF5555`)
  - `/piccaxeutils notifier on|off` — player notifier (render-distance entry)
  - `/piccaxeutils notifier chat|sound|banner|discord on|off` — per-channel
  - `/piccaxeutils ignored` — open the **Ignored Players** screen: add a name to the notifier, proximity, and every webhook rule's ignore list at once; per-row toggles for Notifier (N) / Proximity (P) / Rules (R count/total); remove everywhere. Also at U → Tuning → Lists → Ignored Players.
  - `/piccaxeutils notifier ignore add|remove <player>` / `notifier ignore list` — mute specific players
  - `/piccaxeutils proximity ignore add|remove <player>` / `proximity ignore list` — mute specific players
  - `/piccaxeutils proximity discord on|off` — Discord ping when a watchlisted player enters proximity
  - `/piccaxeutils proximity watch add|remove <player>` / `proximity watch list` — who triggers the ping
  - `/piccaxeutils proximity ping set <text>` / `proximity ping clear` — the ping (e.g. `@here`, `@everyone`, `<@USERID>`, `<@&ROLEID>`)
  - `/piccaxeutils healthbars on|off|toggle` — health bars above entities
  - `/piccaxeutils healthbars range <4-128>` / `healthbars playersonly on|off`
  - `/piccaxeutils outline on|off|toggle` — the player outliner
  - `/piccaxeutils outline set <player> <teammate|ally|enemy|none>` — manual override
  - `/piccaxeutils outline clear <player>` / `/piccaxeutils outline list`
  - `/piccaxeutils traps on|off|toggle` — trap outliner (pistons, plates, string, armor stands); `traps radius <4-64>`, `traps color <hex>` (also bindable to a key: *Toggle Trap Outlines* in Controls)
  - `/piccaxeutils lootchests on|off|toggle` — ender-chest outliner
  - `/piccaxeutils lootchests radius <8-256>` — scan radius in blocks
  - `/piccaxeutils lootchests label on|off` — distance label on each chest
  - `/piccaxeutils lootchests tracer on|off` — straight line from your view to each chest
  - `/piccaxeutils lootchests path on|off` — walking-route path tracer (A* around obstacles)
  - `/piccaxeutils lootchests pathmode nearest|looking|all` — which chest(s) the route goes to
  - `/piccaxeutils discord on|off` — the chat relay
  - `/piccaxeutils discord webhook add <name> <url>` — define a named webhook
  - `/piccaxeutils discord webhook remove|test <name>` / `webhook username <name> <text>` / `webhook list`
  - `/piccaxeutils discord webhook cooldown <name> <seconds>` — min seconds between sends (0 = off; anti-spam)
  - `/piccaxeutils discord assign chat|notifier|proximity|deathkill|hearts <name>` — route a category to a webhook
  - `/piccaxeutils deathkill on|off|toggle` — death/kill relay; `deathkill deaths|kills on|off`, `deathkill ping <text>`
  - `/piccaxeutils hearts on|off|toggle` — heart tracker; `hearts hud|chat|discord on|off`, `hearts ping <text>`
  - `/piccaxeutils potionhud on|off|toggle` — active-effects HUD; `/piccaxeutils invhud on|off|toggle` — inventory HUD (drag both in the HUD editor)
  - `/piccaxeutils discord test` — test the chat-assigned webhook
  - `/piccaxeutils discord team|whispers|mentions|keywords on|off|toggle` — per-filter
  - `/piccaxeutils discord keyword add|remove <word>` / `discord keyword list`
  - **Custom keyword→webhook rules** (route any keyword to any webhook):
    - `/piccaxeutils discord rule add <webhook> <keyword…>` — forward messages containing `<keyword>` to `<webhook>`
    - `/piccaxeutils discord rule label <#> <text>` — prefix the forwarded message
    - `/piccaxeutils discord rule ping <#> <@here|@everyone|<@id>>` — ping with the message
    - `/piccaxeutils discord rule serveronly <#>` — only forward **server/system messages** (no player chat); with a blank keyword this forwards *all* server messages
    - `/piccaxeutils discord rule ignore add|remove <#> <player|text>` / `rule ignore list <#>` — skip messages. Entries that name an **online player** are matched by *authorship* (only their own messages are skipped, not lines that merely mention them); other entries match as plain text anywhere.
    - `/piccaxeutils discord rule toggle|remove <#>` / `discord rule list`
    - `/piccaxeutils discord rule share` — copy all your rules (and the webhooks they use) to your clipboard as a share code
    - `/piccaxeutils discord rule import [code]` — import a friend's share code (from the clipboard if no code is given). Duplicates and same-named webhooks are skipped. **The code contains webhook URLs**, so only share with people you trust.

### Discord setup (multiple webhooks, one per category)

1. In Discord: **Channel → Edit → Integrations → Webhooks → New Webhook → Copy URL** (make as many as you want, e.g. one per channel).
2. In game, define them and assign per category:
   - `/piccaxeutils discord webhook add chatlog <url>`
   - `/piccaxeutils discord webhook add alerts <url2>`
   - `/piccaxeutils discord assign chat chatlog`
   - `/piccaxeutils discord assign notifier alerts`
   - `/piccaxeutils discord assign proximity alerts`
3. `/piccaxeutils discord on` for the chat relay; tune `team/whispers/mentions/keywords`. Test any webhook with `/piccaxeutils discord webhook test <name>`.

Your old single webhook is auto-migrated to a webhook named **default**, assigned to all three categories, so nothing breaks.

> **Privacy:** this sends the matching chat lines (which may include other players'
> messages) to your Discord webhook — an external service. It's off until you set a URL,
> and forwarded text can't ping anyone (`@everyone`/role/user pings are stripped).
> Whisper/team detection is pattern-based; if your server's format differs, edit
> `whisperPatterns` / `teamChatPatterns` in `config/piccaxes-lifesteal-utils.json`.
  - `/piccaxeutils cleardeath` — forget the death waypoint
  - `/piccaxeutils players` (or `/shard`) — list your shard's players from the Tab list, filtered to those with a **real ping (1ms+)**, alphabetized with ping and a count. (The custom Tab is network-wide; cross-shard players show 0 ping since this backend isn't really connected to them, so ping ≥ 1 isolates your shard.)

## Building

Needs **JDK 21**. The Gradle wrapper pins everything else (Gradle 9.5.0, Loom 1.17.12).

```
./gradlew build
```

Output: `build/libs/piccaxes-lifesteal-utils-1.0.0.jar`.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) for **1.21.11**.
2. Put **Fabric API** (1.21.11) and `piccaxes-lifesteal-utils-1.0.0.jar` in `.minecraft/mods`.
3. Launch the Fabric 1.21.11 profile.

## Notes

- Other players' exact health/hearts is **not** sent to your client by vanilla, so
  there's no "enemy heart" overlay — only your own hearts are shown.
- Fullbright works by overriding the lightmap gamma; it's a client-side brightness
  change, not an X-ray.
