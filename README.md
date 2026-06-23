# Piccaxe's Lifesteal Utils (Fabric, Minecraft 1.21.11)

A **client-side** quality-of-life mod for Lifesteal SMP. Works on any server
(vanilla/Realms included) since nothing is sent to the server.

## Features

| Feature | What it does |
|---|---|
| **Clean Heart HUD** | Numeric `❤ 15 / 20` that stays readable past 10 hearts. Green when full, yellow when hurt, red when low. |
| **Totem counter** | Shows totems in your inventory with the item icon; flashes red when at/below the warning threshold. |
| **Player proximity alert** | Sound + centered banner when another player *enters* your radius (default 64 blocks). Fires on entry, not every tick. |
| **Coordinates** | `XYZ` + facing (N/E/S/W). |
| **Death waypoint** | Records where you last died and shows it with live distance; says the dimension if you're elsewhere. Survives relogs. |
| **Auto-reconnect** | After a disconnect/kick, counts down and rejoins the last server (configurable delay + attempt cap). Off by default. |
| **Visual tweaks** | Fullbright (see in the dark) and No Hurt-Cam (kills the damage screen-shake). |
| **Anti-Trickster** | Auto-undoes a server-side hotbar scramble. While no screen is open, if your hotbar becomes a pure reorder of its previous contents (a scramble), it instantly swaps everything back. Real changes (using/picking up items) are left alone. |
| **Player outliner** | Colored glow outline on other players by their nametag/team color: **green = teammate, blue/aqua = ally, red = enemy**. Per-player manual overrides supported. Off by default. |
| **Discord chat relay** | Forwards selected chat to a Discord channel via a webhook you provide — **team chat, whispers/DMs, mentions of you, and custom keywords** (each toggleable). Off by default; needs a webhook URL. |

## Configuring

Three ways, all interchangeable — settings persist to
`.minecraft/config/piccaxes-lifesteal-utils.json`.

- **Settings screen:** press **U** (rebindable in Controls → *Piccaxe's Lifesteal Utils*).
- **Keybinds:** *Open Settings* (U), *Toggle All Features* (unbound), *Toggle Fullbright* (unbound), *Toggle Anti-Trickster* (unbound). Bind them in Options → Controls → *Piccaxe's Lifesteal Utils*.
- **Commands:** `/piccaxeutils` (alias `/piccaxe`):
  - `/piccaxeutils` — open the settings GUI
  - `/piccaxeutils status` — show all toggle states
  - `/piccaxeutils on|off|toggle` — master switch
  - `/piccaxeutils <feature> on|off|toggle` — where `<feature>` is `heart`, `totem`,
    `proximity`, `coords`, `death`, `reconnect`, `fullbright`, `hurtcam`, `trickster`
  - `/piccaxeutils outline on|off|toggle` — the player outliner
  - `/piccaxeutils outline set <player> <teammate|ally|enemy|none>` — manual override
  - `/piccaxeutils outline clear <player>` / `/piccaxeutils outline list`
  - `/piccaxeutils discord on|off` — the chat relay
  - `/piccaxeutils discord url <webhook-url>` — set your Discord webhook
  - `/piccaxeutils discord test` — send a test message
  - `/piccaxeutils discord team|whispers|mentions|keywords on|off|toggle` — per-filter
  - `/piccaxeutils discord keyword add|remove <word>` / `discord keyword list`

### Discord chat relay setup

1. In your Discord server: **Channel → Edit → Integrations → Webhooks → New Webhook → Copy URL**.
2. In game: `/piccaxeutils discord url <paste>` then `/piccaxeutils discord on`, and `/piccaxeutils discord test`.
3. Tune which message types forward with the `team/whispers/mentions/keywords` toggles.

> **Privacy:** this sends the matching chat lines (which may include other players'
> messages) to your Discord webhook — an external service. It's off until you set a URL,
> and forwarded text can't ping anyone (`@everyone`/role/user pings are stripped).
> Whisper/team detection is pattern-based; if your server's format differs, edit
> `whisperPatterns` / `teamChatPatterns` in `config/piccaxes-lifesteal-utils.json`.
  - `/piccaxeutils cleardeath` — forget the death waypoint

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
