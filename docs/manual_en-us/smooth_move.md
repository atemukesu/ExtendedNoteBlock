# Smooth Move Command

`/smoothmove` is a powerful utility command that allows administrators to make players or entities move in a specified direction with smooth speed.

## Command Syntax

### 1. Start Moving
```mcfunction
/smoothmove <target> <direction> <speed> [duration]
```
- **target**: The entity selector to move (e.g., `@s`, `@p`, `@e[type=zombie]`).
- **direction**:
  - `north`, `south`, `east`, `west`: Cardinal directions.
  - `x`, `-x`, `y`, `-y`, `z`, `-z`: Axis directions.
  - `forward`: The direction the player is currently facing horizontally.
  - `look`: The precise direction the player is looking (including vertical component).
- **speed**: The distance moved per Tick (1 represents 20 blocks/second, assuming TPS=20).
- **duration** (Optional): The number of Ticks the movement lasts. If omitted, it will continue moving at this speed indefinitely.

### 2. Stop Moving
```mcfunction
/smoothmove stop <target>
```
- Immediately forces the specified entity to stop smooth movement.

## Core Features

1. **NoClip**:
   - During smooth movement, the entity enters a `noClip` state (similar to Spectator Mode), allowing it to pass through blocks without getting stuck or colliding.
2. **Server Control**:
   - The movement trajectory is forcibly synchronized by the server, eliminating jitter (Rubberbanding) on the client side caused by network latency or collision detection.
3. **Safety Protection**:
   - Fall damage for the entity is reset to 0 during movement, preventing death upon landing after long-distance travel.
   - After movement ends or stops, the entity resumes normal collision detection.
