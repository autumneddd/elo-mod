# Elo Mod (Fabric 1.21.11)

A Fabric server-side Minecraft mod for version 1.21.11 that implements an Elo rating system for PvP combat.

## Features

- **Base Elo:** Every player starts at **1000** Elo.
- **PvP Only:** Elo is only updated on player-versus-player kills (including projectile hits and fall/hazard damage credited to an attacker).
- **No Natural Cause Penalties:** Deaths to fall damage, fire/lava, mobs, drowning, `/kill`, or suicide cause no Elo loss.
- **Omni Calculator Formula:** Rating adjustments match the [Omni Elo Calculator](https://www.omnicalculator.com/sports/elo) using $K = 32$:
  $$\text{expected} = \frac{1}{10^{(\text{opponent} - \text{player})/400} + 1}$$
  $$\Delta\text{Elo} = K \times (\text{score} - \text{expected})$$
  Ratings are rounded to one decimal place internally.
- **In-Game Display:**
  - **Nametags:** Shown above players' heads as `1000 | autumneddd` (Elo in gold).
  - **Tab List:** Shown next to player names in the player list using scoreboard team prefixes.
- **Persistence:** Saved automatically in `world/elo.json` across server restarts.

## Commands

- `/elo` - View your own Elo, kills, and deaths
- `/elo <player>` - View another player's stats
- `/elo top` - Leaderboard of top 10 players by Elo
- `/elo set <player> <value>` - Set a player's Elo (requires OP / permission level 2)
- `/elo reset <player>` - Reset a player to base 1000 Elo (requires OP / permission level 2)

## Building

```bash
./gradlew build
```

The compiled jar will be at `build/libs/elo-1.0.1.jar`.
