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

## Configuring

Three ways, all interchangeable — settings persist to
`.minecraft/config/piccaxes-lifesteal-utils.json`.

- **Settings screen:** press **U** (rebindable in Controls → *Piccaxe's Lifesteal Utils*).
- **Keybinds:** *Open Settings* (U), *Toggle All Features* (unbound), *Toggle Fullbright* (unbound).
- **Commands:** `/lsutils` (alias `/piccaxe`):
  - `/lsutils` or `/lsutils status` — show all toggle states
  - `/lsutils settings` — open the GUI
  - `/lsutils on|off|toggle` — master switch
  - `/lsutils <feature> on|off|toggle` — where `<feature>` is `heart`, `totem`,
    `proximity`, `coords`, `death`, `reconnect`, `fullbright`, `hurtcam`
  - `/lsutils cleardeath` — forget the death waypoint

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
