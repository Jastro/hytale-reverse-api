# Changelog

## [0.1.0] - 2026-01-16

### Added
- New Sky Explorers (SE) command system with subcommands:
  - `/se create` - Create a ship from gold block corners
  - `/se fly` - Start flying the ship
  - `/se stop` - Stop and land the ship
  - `/se rotate [degrees]` - Rotate the ship (default 90°)
- Players can now stand on the ship while flying
- Smooth altitude limit with gradual slowdown (no abrupt stops)
- Adaptive landing speed (fast descent when high, gentle near ground)

### Changed
- Renamed mod from "SkyWars" to "Sky Explorers" to avoid confusion with the game mode
- Increased horizontal flight speed (2.5x faster)
- Faster acceleration on takeoff

### Known Issues
- Player may fall off ship in certain conditions
- Smoke particles may not be visible
