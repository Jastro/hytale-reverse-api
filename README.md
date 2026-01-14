# Hytale Server Plugin API Guide

**Status:** Community Reverse-Engineered Documentation
**Game Version:** Early Access (2026)
**Last Updated:** 2026-01-14

> This guide documents the Hytale Server Plugin API through reverse engineering. Contributions welcome!

---

## Table of Contents

1. [Getting Started](#getting-started)
2. [Plugin Structure](#plugin-structure)
3. [Plugin Lifecycle](#plugin-lifecycle)
4. [Commands](#commands)
5. [Player API](#player-api)
6. [World & Blocks](#world--blocks)
7. [BlockEntity (Physics Entities)](#blockentity-physics-entities)
8. [ECS Architecture](#ecs-architecture)
9. [Mount System](#mount-system)
10. [Build & Deploy](#build--deploy)
11. [Reverse Engineering Guide](#reverse-engineering-guide)
12. [Important Discoveries & Gotchas](#important-discoveries--gotchas)
13. [Research in Progress](#research-in-progress)

---

## Getting Started

### Requirements

- Java 17+
- Gradle
- `hytale-server.jar` (from game installation)

### Minimal Plugin

```java
public class MyPlugin extends JavaPlugin {
    public MyPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        getCommandRegistry().registerCommand(new MyCommand());
    }
}
```

---

## Plugin Structure

### Directory Layout

```
plugin-name/
├── src/main/java/com/yourpkg/
│   ├── YourPlugin.java
│   └── commands/
│       └── YourCommand.java
├── src/main/resources/
│   └── manifest.json
├── build.gradle.kts
└── libs/
    └── hytale-server.jar
```

### manifest.json

```json
{
  "Group": "YourGroup",
  "Name": "Plugin Name",
  "Version": "0.1.0",
  "Description": "Plugin description",
  "Authors": [{"Name": "Your Name", "Email": "", "Url": ""}],
  "Main": "com.yourpkg.YourPlugin",
  "ServerVersion": "*",
  "Dependencies": {},
  "OptionalDependencies": {},
  "DisabledByDefault": false
}
```

### Installation Path

```
Linux (Flatpak): ~/.var/app/com.hypixel.HytaleLauncher/data/Hytale/UserData/Mods/
```

---

## Plugin Lifecycle

### Execution Order

```
Constructor → preLoad() → [Enable] → setup() → start() → ... → shutdown()
```

### Main Class

```java
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public class YourPlugin extends JavaPlugin {

    public YourPlugin(JavaPluginInit init) {
        super(init);
        // Plugin loaded, but NOT enabled yet
    }

    @Override
    protected void setup() {
        // Plugin is now enabled - register commands here
        getCommandRegistry().registerCommand(new YourCommand());
    }

    @Override
    protected void start() {
        // Called after setup, server is ready
    }

    @Override
    protected void shutdown() {
        // Cleanup when server stops
    }
}
```

---

## Commands

### AbstractPlayerCommand

Base class for commands that require a player context.

```java
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class MyCommand extends AbstractPlayerCommand {

    public MyCommand() {
        super("commandname", "Command description");
    }

    @Override
    protected void execute(
            CommandContext context,
            Store<EntityStore> entityStore,
            Ref<EntityStore> entityRef,
            PlayerRef player,
            World world
    ) {
        // Command logic here
    }
}
```

### CommandContext

| Method | Returns | Description |
|--------|---------|-------------|
| `getInputString()` | `String` | Full command input including command name |

### Parsing Arguments

```java
// Input: "/mycommand arg1 arg2"
String input = context.getInputString();
String args = input.replace("/mycommand", "").replace("mycommand", "").trim();
// args = "arg1 arg2"
```

---

## Player API

### PlayerRef

| Method | Returns | Description |
|--------|---------|-------------|
| `getUsername()` | `String` | Player's username |
| `getUuid()` | `UUID` | Player's unique ID |
| `getTransform()` | `Transform` | Position and rotation |
| `getReference()` | `Ref<EntityStore>` | Entity reference for ECS operations |
| `sendMessage(Message)` | `void` | Send chat message to player |

### Getting Player Position

```java
Vector3d pos = player.getTransform().getPosition();
double x = pos.x;
double y = pos.y;
double z = pos.z;
```

### Message System

```java
import com.hypixel.hytale.server.core.Message;
import java.awt.Color;

// Simple message
player.sendMessage(Message.raw("Hello!"));

// Styled message
player.sendMessage(Message.raw("Success!")
    .color(Color.GREEN)
    .bold(true)
    .italic(false));

// Custom RGB
player.sendMessage(Message.raw("Custom color")
    .color(new Color(255, 170, 0)));
```

| Method | Parameter | Description |
|--------|-----------|-------------|
| `Message.raw(String)` | text | Create message |
| `.color(Color)` | java.awt.Color | Set text color |
| `.bold(boolean)` | true/false | Bold text |
| `.italic(boolean)` | true/false | Italic text |
| `.monospace(boolean)` | true/false | Monospace font |

---

## World & Blocks

### World

| Method | Parameters | Description |
|--------|------------|-------------|
| `setBlock(int, int, int, String)` | x, y, z, blockTypeKey | Place or remove a block |

### Placing Blocks

```java
// Place a block
world.setBlock(x, y, z, "Soil_Pebbles_Frozen");

// Remove a block
world.setBlock(x, y, z, "air");
```

### Known Block Type IDs

| Block ID | Description |
|----------|-------------|
| `Soil_Pebbles_Frozen` | Stone-like decorative block |
| `Deco_Chair_Scrap` | Goblin Throne (sittable) |
| `air` | Empty block (removes existing) |

---

## BlockEntity (Physics Entities)

BlockEntity creates blocks as physics-enabled entities that can move, have forces applied, and respond to gravity.

### Creating a BlockEntity

```java
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.AddReason;

// Get TimeResource from entity store (IMPORTANT - see Discoveries section)
TimeResource timeResource = entityStore.getResource(TimeResource.getResourceType());

// Create position
Vector3d position = new Vector3d(x, y, z);

// Assemble the BlockEntity
Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(
    timeResource,
    "Soil_Pebbles_Frozen",  // Block type
    position
);

// Add to world
Ref<EntityStore> ref = entityStore.addEntity(holder, AddReason.SPAWN);

// Get component for physics operations
BlockEntity block = entityStore.getComponent(ref, BlockEntity.getComponentType());
```

### BlockEntity Methods

| Method | Parameters | Description |
|--------|------------|-------------|
| `assembleDefaultBlockEntity(TimeResource, String, Vector3d)` | time, blockType, position | Create a new BlockEntity |
| `addForce(float, float, float)` | x, y, z | Apply physics force |
| `addForce(Vector3d)` | force vector | Apply physics force |
| `getSimplePhysicsProvider()` | - | Get physics controller |
| `getBlockTypeKey()` | - | Get block type ID |

### Applying Forces

```java
// Upward force
block.addForce(0.0f, 2.0f, 0.0f);

// Downward force
block.addForce(0.0f, -1.0f, 0.0f);

// Horizontal force
block.addForce(2.0f, 0.0f, 0.0f);

// Hover (counter gravity)
block.addForce(0.0f, 0.5f, 0.0f);
```

### Continuous Force (Smooth Movement)

```java
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

private static final ScheduledExecutorService scheduler =
    Executors.newScheduledThreadPool(1);
private static ScheduledFuture<?> task;

// Start continuous force application
task = scheduler.scheduleAtFixedRate(() -> {
    block.addForce(0.0f, 2.0f, 0.0f);
}, 0, 100, TimeUnit.MILLISECONDS);

// Stop
if (task != null) {
    task.cancel(false);
    task = null;
}
```

### SimplePhysicsProvider

Access via `blockEntity.getSimplePhysicsProvider()`:

| Method | Parameters | Description |
|--------|------------|-------------|
| `setProvideCharacterCollisions(boolean)` | true/false | Enable/disable player collision |
| `setGravity(double, BoundingBox)` | gravity, bounds | Set gravity strength |
| `setBounciness(double)` | factor | Set bounce factor |
| `setVelocity(Vector3d)` | velocity | Set direct velocity |
| `addVelocity(float, float, float)` | x, y, z | Add to velocity |
| `isOnGround()` | - | Check if resting on ground |
| `isResting()` | - | Check if at rest |

---

## ECS Architecture

Hytale uses the Flecs Entity Component System.

### Core Concepts

| Concept | Class | Description |
|---------|-------|-------------|
| Entity | `Ref<EntityStore>` | Reference to an entity |
| Component | `Component<EntityStore>` | Data attached to entities |
| Holder | `Holder<EntityStore>` | Entity data before spawning |
| Store | `Store<EntityStore>` | Container for all entities |
| Resource | Various | Global singleton data |

### Common Operations

```java
// Add entity to world
Ref<EntityStore> ref = entityStore.addEntity(holder, AddReason.SPAWN);

// Get component from entity
BlockEntity block = entityStore.getComponent(ref, BlockEntity.getComponentType());

// Get global resource
TimeResource time = entityStore.getResource(TimeResource.getResourceType());
```

### AddReason Enum

| Value | Usage |
|-------|-------|
| `SPAWN` | New entity creation |

---

## Mount System

Hytale has a complete mount system in `com.hypixel.hytale.builtin.mounts.*`

### Key Classes

| Class | Purpose |
|-------|---------|
| `MountedComponent` | Added to player when mounted |
| `MountedByComponent` | Added to entity being mounted |
| `NPCMountComponent` | Makes an NPC/entity mountable |
| `BlockMountComponent` | For static block seats (chairs, beds) |
| `BlockMountAPI` | API for mounting on static blocks |

### MountController Enum

| Value | Description |
|-------|-------------|
| `Minecart` | Minecart-style control |
| `BlockMount` | Block seat control |

### BlockMountType Enum

| Value | Description |
|-------|-------------|
| `Seat` | Chairs, thrones |
| `Bed` | Beds |

### BlockMountAPI

```java
// Mount player on a static world block
BlockMountAPI.mountOnBlock(
    playerEntityRef,      // Ref<EntityStore>
    commandBuffer,        // CommandBuffer<EntityStore>
    blockPosition,        // Vector3i
    playerPosition        // Vector3f
);
```

### Related Network Packets

| Packet | Description |
|--------|-------------|
| `MountNPC` | Mount on NPC entity |
| `DismountNPC` | Dismount from NPC |
| `MountMovement` | Sync mount movement |

---

## Build & Deploy

### build.gradle.kts Example

```kotlin
plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(files("libs/hytale-server.jar"))
}

tasks.shadowJar {
    archiveBaseName.set("YourPlugin")
    archiveVersion.set("0.1.0")
}
```

### Commands

```bash
# Compile
./gradlew shadowJar

# Deploy (Linux Flatpak)
cp build/libs/YourPlugin-0.1.0.jar \
   ~/.var/app/com.hypixel.HytaleLauncher/data/Hytale/UserData/Mods/

# One-liner
./gradlew shadowJar && cp build/libs/*.jar ~/.var/app/com.hypixel.HytaleLauncher/data/Hytale/UserData/Mods/
```

---

## Reverse Engineering Guide

### Tools

The `hytale-server.jar` contains all server-side classes. Use these commands to explore:

### List All Classes in a Package

```bash
jar tf libs/hytale-server.jar | grep "com/hypixel/hytale/server/core/command"
```

### View Class Methods and Fields

```bash
# Public members only
javap -cp libs/hytale-server.jar -public com.hypixel.hytale.server.core.entity.entities.BlockEntity

# All members including private
javap -cp libs/hytale-server.jar -private com.hypixel.hytale.server.core.entity.entities.BlockEntity

# With method signatures
javap -cp libs/hytale-server.jar -s com.hypixel.hytale.server.core.entity.entities.BlockEntity
```

### Search for Classes by Name

```bash
# Find all classes containing "mount"
jar tf libs/hytale-server.jar | grep -i "mount"

# Find all classes containing "physics"
jar tf libs/hytale-server.jar | grep -i "physics"
```

### Search for Specific Patterns

```bash
# Find command-related classes
jar tf libs/hytale-server.jar | grep -iE "(command|cmd)"

# Find entity-related classes
jar tf libs/hytale-server.jar | grep -iE "entity" | grep -v "test"
```

### Decompile for Full Source (requires external tools)

```bash
# Using fernflower
java -jar fernflower.jar libs/hytale-server.jar output/

# Using CFR
java -jar cfr.jar libs/hytale-server.jar --outputdir output/
```

### Tips

1. Start with `jar tf` to find class names
2. Use `javap` to see method signatures
3. Look for `CODEC` fields - they indicate serializable components
4. Look for `getComponentType()` static methods - they indicate ECS components
5. Check for inner classes (indicated by `$` in class names)

---

## Important Discoveries & Gotchas

### TimeResource Must Come from EntityStore

**Problem:** Creating `new TimeResource()` causes BlockEntity to be invisible or crash.

**Solution:** Always get TimeResource from the world's entity store:

```java
// WRONG - causes issues
TimeResource timeResource = new TimeResource();

// CORRECT - works properly
TimeResource timeResource = entityStore.getResource(TimeResource.getResourceType());
```

### Command Registration Timing

**Problem:** Commands registered in constructor don't work, error "plugin null is not enabled".

**Solution:** Register commands in `setup()`, not in constructor:

```java
// WRONG - constructor
public MyPlugin(JavaPluginInit init) {
    super(init);
    getCommandRegistry().registerCommand(new MyCommand()); // Won't work!
}

// CORRECT - setup method
@Override
protected void setup() {
    getCommandRegistry().registerCommand(new MyCommand()); // Works!
}
```

### Don't Call super.preLoad()

**Problem:** Calling `super.preLoad()` causes NullPointerException.

**Solution:** Don't override `preLoad()`, or if you must, don't call super:

```java
// WRONG
@Override
protected void preLoad() {
    super.preLoad(); // NullPointerException!
}

// CORRECT - either don't override, or:
@Override
protected void preLoad() {
    // Don't call super
}
```

### Vector3d Fields vs Methods

**Problem:** `pos.x()` doesn't exist, causes compilation error.

**Solution:** Vector3d uses fields, not getter methods:

```java
Vector3d pos = player.getTransform().getPosition();

// WRONG
double x = pos.x(); // Method doesn't exist

// CORRECT
double x = pos.x;   // Direct field access
```

### Block Type "air" is Lowercase

**Problem:** Block type IDs are case-sensitive.

**Solution:** Use lowercase "air" to remove blocks:

```java
// WRONG
world.setBlock(x, y, z, "Air");
world.setBlock(x, y, z, "AIR");

// CORRECT
world.setBlock(x, y, z, "air");
```

### Command Argument Parsing

**Problem:** Built-in argument parsing may not work as expected.

**Solution:** Parse `getInputString()` manually:

```java
String rawInput = context.getInputString();
String args = rawInput.replace("/commandname", "")
                      .replace("commandname", "")
                      .trim()
                      .toLowerCase();
```

### Logging

**Problem:** `HytaleLogger.info()` may not be accessible.

**Solution:** Use standard Java logging:

```java
System.out.println("[MyPlugin] Message here");
```

---

## Research in Progress

These features have been identified but not fully documented:

### BlockEntity Collision with Players

- `SimplePhysicsProvider.setProvideCharacterCollisions(true)` may enable solid collisions
- Status: **Testing**

### Mounting Players on Moving Entities

- `MountedComponent` can be added to players
- `MountedByComponent` tracks passengers
- `NPCMountComponent` may work with BlockEntity
- Challenge: Need `CommandBuffer` to add components dynamically
- Status: **Investigating**

### Network Packets

- `MountNPC` packet can force-mount players
- May be usable via `player.getPacketHandler()`
- Status: **Investigating**

---

## Common Issues Reference

| Problem | Solution |
|---------|----------|
| Command not found | Register in `setup()`, not constructor |
| "plugin null is not enabled" | Use `setup()` method for registration |
| NullPointerException in preLoad | Don't call `super.preLoad()` |
| BlockEntity invisible/crash | Get TimeResource from `entityStore.getResource()` |
| Can't remove blocks | Use `"air"` (lowercase) as block type |
| Command args not parsing | Parse `context.getInputString()` manually |
| Vector3d.x() error | Use `pos.x` field, not `pos.x()` method |
| Logging not working | Use `System.out.println()` |

---

## Contributing

Found something new? Please contribute!

1. Document your discovery
2. Include working code examples
3. Note any gotchas or requirements
4. Submit a PR

---

## License

This documentation is community-created through reverse engineering for educational purposes.

---

*Last updated: 2026-01-14*
