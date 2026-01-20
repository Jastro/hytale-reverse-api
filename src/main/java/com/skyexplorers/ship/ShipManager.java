package com.skyexplorers.ship;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.hitboxcollision.HitboxCollisionConfig;
import com.hypixel.hytale.server.core.modules.entity.hitboxcollision.HitboxCollision;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Manages ship creation and flight
 */
public class ShipManager {

    private static Ship currentShip = null;

    // Store references for flight control (needed by listeners)
    private static Store<EntityStore> currentEntityStore = null;
    private static World currentWorld = null;

    // Flight loop state
    private static boolean flightLoopRunning = false;
    private static boolean descentRunning = false;

    // Rotation state
    private static boolean rotationRunning = false;
    private static Vector3d targetRotationDirection = null;

    // Smoke particle settings
    private static final String SMOKE_PARTICLE_ID = "Smoke_Black";
    private static final int SMOKE_INTERVAL_TICKS = 3; // Spawn smoke every N ticks
    private static int smokeTickCounter = 0;
    private static final Random random = new Random();

    // Gold block ID for corner detection
    public static final String CORNER_BLOCK_ID = "Rock_Gold_Brick";

    // Ship structure block ID - only this block type will be converted to entities
    public static final String SHIP_BLOCK_ID = "Rock_Chalk_Brick";

    public static Ship getCurrentShip() {
        return currentShip;
    }

    /**
     * Scan area around player for gold blocks marking corners
     * Returns list of corner positions found
     */
    public static List<Vector3i> findCorners(World world, Vector3d playerPos, int scanRadius) {
        List<Vector3i> corners = new ArrayList<>();

        int playerX = (int) Math.floor(playerPos.x);
        int playerY = (int) Math.floor(playerPos.y);
        int playerZ = (int) Math.floor(playerPos.z);

        System.out.println("[Ship] Scanning for corners around player at: " + playerX + "," + playerY + "," + playerZ);
        System.out.println("[Ship] Looking for block ID: '" + CORNER_BLOCK_ID + "'");

        int blocksScanned = 0;
        int nonAirFound = 0;

        // Scan in a cube around player - use World.getBlockType directly
        for (int y = playerY - scanRadius; y <= playerY + scanRadius; y++) {
            for (int x = playerX - scanRadius; x <= playerX + scanRadius; x++) {
                for (int z = playerZ - scanRadius; z <= playerZ + scanRadius; z++) {
                    try {
                        // Use World's direct getBlockType method
                        BlockType blockType = world.getBlockType(x, y, z);
                        blocksScanned++;

                        if (blockType != null) {
                            String blockId = blockType.getId();
                            if (blockId != null && !blockId.isEmpty() && !blockId.equalsIgnoreCase("air")) {
                                nonAirFound++;
                                // Only log first 20 non-air blocks to avoid spam
                                if (nonAirFound <= 20) {
                                    System.out.println("[Ship] Found block: '" + blockId + "' at " + x + "," + y + "," + z);
                                }
                                if (CORNER_BLOCK_ID.equals(blockId)) {
                                    corners.add(new Vector3i(x, y, z));
                                    System.out.println("[Ship] >>> CORNER MATCH at: " + x + ", " + y + ", " + z);
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[Ship] Error at " + x + "," + y + "," + z + ": " + e.getMessage());
                    }
                }
            }
        }

        System.out.println("[Ship] Scan complete. Blocks scanned: " + blocksScanned + ", Non-air found: " + nonAirFound);
        System.out.println("[Ship] Corners found: " + corners.size());

        return corners;
    }

    /**
     * Create ship from 4 corners (finds min/max bounds)
     */
    public static Ship createShip(
            World world,
            Store<EntityStore> entityStore,
            List<Vector3i> corners,
            Vector3d flightDirection
    ) {
        if (corners.size() < 4) {
            System.out.println("[Ship] Need 4 corners, found: " + corners.size());
            return null;
        }

        // Find bounding box from corners
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (Vector3i corner : corners) {
            minX = Math.min(minX, corner.x);
            minY = Math.min(minY, corner.y);
            minZ = Math.min(minZ, corner.z);
            maxX = Math.max(maxX, corner.x);
            maxY = Math.max(maxY, corner.y);
            maxZ = Math.max(maxZ, corner.z);
        }

        // Use same Y for all corners (flat base)
        // Extend upward to capture blocks on top
        int height = 10; // Scan up to 10 blocks above corners

        Vector3i minCorner = new Vector3i(minX, minY, minZ);
        Vector3i maxCorner = new Vector3i(maxX, minY + height, maxZ);

        System.out.println("[Ship] Bounds: " + minCorner + " to " + maxCorner);

        Ship ship = new Ship(minCorner, maxCorner);
        ship.setFlightDirection(flightDirection);

        // Get TimeResource for creating BlockEntities
        TimeResource timeResource = entityStore.getResource(TimeResource.getResourceType());
        if (timeResource == null) {
            System.out.println("[Ship] TimeResource is null!");
            return null;
        }

        // Get HardCollision config for solid collision (allows players to stand on ship)
        HitboxCollisionConfig collisionConfig = HitboxCollisionConfig.getAssetMap().getAsset("HardCollision");
        if (collisionConfig == null) {
            System.out.println("[Ship] WARNING: HardCollision config not found! Ship will not have solid collision.");
        } else {
            System.out.println("[Ship] HardCollision config loaded: " + collisionConfig.getId() + " (type: " + collisionConfig.getCollisionType() + ")");
        }

        // First pass: collect all blocks to convert and remove them from world
        List<String> blockIds = new ArrayList<>();
        List<Vector3d> blockPositions = new ArrayList<>();

        for (int y = minY; y <= maxCorner.y; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    try {
                        BlockType blockType = world.getBlockType(x, y, z);
                        if (blockType != null) {
                            String blockId = blockType.getId();

                            // Include ship structure blocks and gold corners
                            if (!SHIP_BLOCK_ID.equals(blockId) &&
                                !CORNER_BLOCK_ID.equals(blockId)) {
                                continue;
                            }

                            // Store block info
                            blockIds.add(blockId);
                            // Position exactly at block center (add 0.5 to center in block)
                            blockPositions.add(new Vector3d(x + 0.5, y, z + 0.5));

                            // Remove original block FIRST
                            world.setBlock(x, y, z, "empty");
                        }
                    } catch (Exception e) {
                        System.err.println("[Ship] Error reading block at " + x + "," + y + "," + z + ": " + e.getMessage());
                    }
                }
            }
        }

        System.out.println("[Ship] Removed " + blockIds.size() + " blocks from world");

        // Second pass: create BlockEntities at the exact positions
        int convertedCount = 0;
        int collisionAdded = 0;
        for (int i = 0; i < blockIds.size(); i++) {
            try {
                String blockId = blockIds.get(i);
                Vector3d blockPos = blockPositions.get(i);

                // Create BlockEntity at exact position
                Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(
                    timeResource, blockId, blockPos
                );

                // Add HitboxCollision component BEFORE spawning (community solution!)
                if (collisionConfig != null) {
                    HitboxCollision collision = new HitboxCollision(collisionConfig);
                    holder.addComponent(HitboxCollision.getComponentType(), collision);
                    collisionAdded++;
                }

                Ref<EntityStore> ref = entityStore.addEntity(holder, AddReason.SPAWN);

                // Get the BlockEntity component for direct access (addForce)
                BlockEntity blockEntity = entityStore.getComponent(ref, BlockEntity.getComponentType());

                // NOTE: Do NOT use setProvideCharacterCollisions(true)!
                // It causes the physics provider to stop when colliding with players.
                // HitboxCollision component already provides solid collision.

                ship.addBlock(ref, blockEntity, blockId, blockPos);

                convertedCount++;
            } catch (Exception e) {
                System.err.println("[Ship] Error creating BlockEntity: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("[Ship] Converted " + convertedCount + " blocks to entities");
        System.out.println("[Ship] HitboxCollision added to " + collisionAdded + " blocks");

        // Set starting Y for altitude tracking
        if (!ship.getBlockPositions().isEmpty()) {
            ship.setStartingY(ship.getBlockPositions().get(0).y);
        }

        // Store references for flight control
        currentEntityStore = entityStore;
        currentWorld = world;

        currentShip = ship;
        return ship;
    }

    // Getters for stored references
    public static Store<EntityStore> getEntityStore() {
        return currentEntityStore;
    }

    public static World getWorld() {
        return currentWorld;
    }

    // Reusable thread for flight loop
    private static Thread flightThread = null;

    /**
     * Start the flight loop (called by controller item or torch)
     */
    public static void startFlightLoop() {
        Ship ship = currentShip;
        if (ship == null || currentEntityStore == null) {
            System.out.println("[Ship] Cannot start flight - no ship or entityStore");
            return;
        }

        // Stop any existing loops
        stopFlightLoop();
        stopDescent();

        // Reset forces for fresh takeoff
        ship.resetForces();

        // CRITICAL: Get actual current Y position from block entities
        // The tracked altitude can be stale after landing
        double actualY = getActualShipY(ship);
        System.out.println("[Ship] Actual ship Y from blocks: " + actualY + " (tracked was: " + ship.getCurrentAltitude() + ")");

        // Update altitude tracking to actual position
        ship.setCurrentAltitude(actualY);
        ship.setStartingY(actualY);

        ship.setFlying(true);
        flightLoopRunning = true;

        System.out.println("[Ship] Starting flight from altitude: " + ship.getStartingY());

        // IMPORTANT: Apply initial upward impulse to "wake up" physics for blocks at rest
        // When blocks are on ground and stationary, setVelocity alone may not work
        wakeUpShipPhysics(ship);

        // Create and start flight thread
        flightThread = new Thread(() -> {
            System.out.println("[Ship] Flight loop started");
            while (flightLoopRunning) {
                try {
                    Ship currentShipRef = currentShip;
                    if (currentShipRef != null && currentEntityStore != null && currentShipRef.isFlying()) {
                        moveShip(currentEntityStore, currentShipRef);
                    } else if (currentShipRef != null && !currentShipRef.isFlying()) {
                        // Ship stopped flying, exit loop
                        break;
                    }
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // Thread interrupted, exit
                    break;
                } catch (Exception e) {
                    System.err.println("[Ship] Flight loop error: " + e.getMessage());
                }
            }
            flightLoopRunning = false;
            System.out.println("[Ship] Flight loop ended");
        }, "ShipFlightLoop");
        flightThread.setDaemon(true);
        flightThread.start();
    }


    /**
     * Stop the flight loop and begin descent
     */
    public static void stopFlightLoop() {
        flightLoopRunning = false;
        if (flightThread != null) {
            flightThread.interrupt();
            flightThread = null;
        }
    }

    // Reusable thread for descent loop
    private static Thread descentThread = null;

    /**
     * Start descent loop (gentle landing)
     * Adaptive speed: fast when high above ground, slow when near ground
     * Uses real ground detection for skylands support
     */
    public static void startDescentLoop() {
        Ship ship = currentShip;
        if (ship == null) return;

        // Stop flight but keep the loop running for smooth transition
        stopFlightLoop();

        if (descentRunning) return;
        descentRunning = true;

        // Descent parameters
        final double DECELERATION = 0.03;           // How fast horizontal speed reduces
        final double MAX_DESCENT_SPEED = -0.8;      // Fast descent when high
        final double MIN_DESCENT_SPEED = -0.1;      // Slow descent near ground
        final double SLOW_DOWN_HEIGHT = 10.0;       // Start slowing down 10 blocks above ground
        final double MIN_SPEED = 0.05;              // Stop when below this

        descentThread = new Thread(() -> {
            System.out.println("[Ship] Descent started - adaptive speed with ground detection");

            Ship currentShipRef = currentShip;
            if (currentShipRef == null) {
                descentRunning = false;
                return;
            }

            // Start with current forces
            double currentHorizontal = currentShipRef.getCurrentHorizontalForce();
            currentShipRef.setFlying(false);

            int groundedCount = 0;
            final int GROUNDED_THRESHOLD = 10;

            while (descentRunning) {
                try {
                    currentShipRef = currentShip;
                    if (currentShipRef == null) break;

                    // If ship started flying again, stop descent immediately
                    if (currentShipRef.isFlying()) {
                        System.out.println("[Ship] Descent interrupted - ship is flying again");
                        break;
                    }

                    // Gradually reduce horizontal speed
                    currentHorizontal = Math.max(0, currentHorizontal - DECELERATION);

                    Vector3d dir = currentShipRef.getFlightDirection();
                    double vx = dir.x * currentHorizontal;
                    double vz = dir.z * currentHorizontal;

                    // Find height above ground by checking blocks below ship
                    double heightAboveGround = findHeightAboveGround(currentShipRef);

                    // Check if all blocks are on ground
                    boolean allOnGround = true;

                    for (BlockEntity blockEntity : currentShipRef.getBlockEntities()) {
                        try {
                            if (blockEntity != null) {
                                var physics = blockEntity.getSimplePhysicsProvider();
                                if (physics != null && !physics.isOnGround()) {
                                    allOnGround = false;
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            // Ignore
                        }
                    }

                    // Calculate adaptive descent speed based on height above ground
                    double vy;
                    if (allOnGround) {
                        vy = 0; // Don't push down if on ground
                        groundedCount++;
                    } else {
                        // Adaptive speed: fast when high, slow when near ground
                        if (heightAboveGround > SLOW_DOWN_HEIGHT) {
                            // High above ground - fast descent
                            vy = MAX_DESCENT_SPEED;
                        } else if (heightAboveGround > 1) {
                            // Approaching ground - interpolate speed
                            double t = (heightAboveGround - 1) / (SLOW_DOWN_HEIGHT - 1);
                            vy = MIN_DESCENT_SPEED + (MAX_DESCENT_SPEED - MIN_DESCENT_SPEED) * t;
                        } else {
                            // Very close to ground - gentle
                            vy = MIN_DESCENT_SPEED;
                        }
                        groundedCount = 0;
                    }

                    // Double-check we should still be descending before applying velocity
                    if (!descentRunning || currentShipRef.isFlying()) {
                        System.out.println("[Ship] Descent cancelled before applying velocity");
                        break;
                    }

                    // Apply velocity to all blocks
                    Vector3d velocity = new Vector3d(vx, vy, vz);
                    for (BlockEntity blockEntity : currentShipRef.getBlockEntities()) {
                        try {
                            if (blockEntity != null) {
                                var physics = blockEntity.getSimplePhysicsProvider();
                                if (physics != null) {
                                    physics.setVelocity(velocity);
                                }
                            }
                        } catch (Exception e) {
                            // Ignore
                        }
                    }

                    // Check if fully stopped
                    if (allOnGround && groundedCount >= GROUNDED_THRESHOLD && currentHorizontal < MIN_SPEED) {
                        // Final stop - zero all velocities
                        for (BlockEntity blockEntity : currentShipRef.getBlockEntities()) {
                            try {
                                if (blockEntity != null) {
                                    var physics = blockEntity.getSimplePhysicsProvider();
                                    if (physics != null) {
                                        physics.setVelocity(new Vector3d(0, 0, 0));
                                    }
                                }
                            } catch (Exception e) {
                                // Ignore
                            }
                        }
                        System.out.println("[Ship] Descent complete - ship landed");
                        descentRunning = false;
                        break;
                    }

                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // Thread interrupted, exit
                    break;
                } catch (Exception e) {
                    System.err.println("[Ship] Descent error: " + e.getMessage());
                }
            }
            descentRunning = false;
            System.out.println("[Ship] Descent ended");
        }, "ShipDescentLoop");
        descentThread.setDaemon(true);
        descentThread.start();
    }

    /**
     * Wake up ship physics by applying an initial upward velocity
     * This is needed when blocks are at rest on the ground
     */
    private static void wakeUpShipPhysics(Ship ship) {
        if (ship == null) return;

        System.out.println("[Ship] Waking up physics for " + ship.getBlockEntities().size() + " blocks");

        // Apply a strong upward velocity to lift blocks off ground immediately
        Vector3d liftOffVelocity = new Vector3d(0, 2.0, 0);  // Strong initial lift

        int wokenUp = 0;
        int onGround = 0;
        for (BlockEntity blockEntity : ship.getBlockEntities()) {
            try {
                if (blockEntity != null) {
                    var physics = blockEntity.getSimplePhysicsProvider();
                    if (physics != null) {
                        if (physics.isOnGround()) {
                            onGround++;
                        }
                        // Set strong upward velocity to break free from ground
                        physics.setVelocity(liftOffVelocity);
                        wokenUp++;
                    }
                }
            } catch (Exception e) {
                System.err.println("[Ship] Wake up error: " + e.getMessage());
            }
        }
        System.out.println("[Ship] Applied lift-off velocity to " + wokenUp + " blocks (" + onGround + " were on ground)");
    }

    /**
     * Get the actual Y position of the ship from its BlockEntities
     * This is needed because tracked altitude can become stale after landing
     */
    private static double getActualShipY(Ship ship) {
        if (ship == null || ship.getBlockRefs().isEmpty() || currentEntityStore == null) {
            return ship != null ? ship.getCurrentAltitude() : 0;
        }

        double totalY = 0;
        int count = 0;

        for (Ref<EntityStore> ref : ship.getBlockRefs()) {
            try {
                if (ref != null) {
                    TransformComponent transform = currentEntityStore.getComponent(ref, TransformComponent.getComponentType());
                    if (transform != null) {
                        Vector3d pos = transform.getPosition();
                        if (pos != null) {
                            totalY += pos.y;
                            count++;
                        }
                    }
                }
            } catch (Exception e) {
                // Entity ref might be invalid
            }
        }

        if (count > 0) {
            return totalY / count;
        }
        return ship.getCurrentAltitude();
    }

    /**
     * Find the minimum height above ground for the ship
     * Scans downward from ship's lowest blocks to find solid ground
     */
    private static double findHeightAboveGround(Ship ship) {
        if (currentWorld == null || ship == null) return 100.0;

        double minHeight = 100.0; // Default high value
        Vector3i minCorner = ship.getMinCorner();
        Vector3i maxCorner = ship.getMaxCorner();

        // Get current ship Y from tracked altitude
        double shipY = ship.getCurrentAltitude();

        // Check a few points under the ship
        int[][] checkPoints = {
            {minCorner.x, minCorner.z},
            {maxCorner.x, minCorner.z},
            {minCorner.x, maxCorner.z},
            {maxCorner.x, maxCorner.z},
            {(minCorner.x + maxCorner.x) / 2, (minCorner.z + maxCorner.z) / 2}
        };

        for (int[] point : checkPoints) {
            int x = point[0];
            int z = point[1];

            // Scan downward from ship position
            for (int y = (int) shipY - 1; y >= 0 && y > shipY - 100; y--) {
                try {
                    var blockType = currentWorld.getBlockType(x, y, z);
                    if (blockType != null) {
                        String blockId = blockType.getId();
                        if (blockId != null && !blockId.isEmpty() && !blockId.equalsIgnoreCase("air") && !blockId.equalsIgnoreCase("empty")) {
                            double height = shipY - y - 1;
                            minHeight = Math.min(minHeight, height);
                            break;
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
        }

        return minHeight;
    }

    /**
     * Stop descent loop
     */
    public static void stopDescent() {
        descentRunning = false;
        if (descentThread != null) {
            descentThread.interrupt();
            descentThread = null;
        }
    }

    /**
     * Check if flight loop is running
     */
    public static boolean isFlightLoopRunning() {
        return flightLoopRunning;
    }

    /**
     * Check if descent is running
     */
    public static boolean isDescentRunning() {
        return descentRunning;
    }

    // Counter for debug logging (only log every N ticks)
    private static int moveDebugCounter = 0;

    /**
     * Move ship by setting velocity on all its BlockEntities
     * Uses setVelocity to control speed directly (no accumulation)
     *
     * Vertical: gradual 0 -> 0.5 max, stops after 40 blocks
     * Horizontal: gradual increase up to 1.0f
     */
    public static void moveShip(Store<EntityStore> entityStore, Ship ship) {
        if (ship == null || ship.getBlockEntities().isEmpty()) {
            return;
        }

        // If stopped and not flying, nothing to do
        if (ship.isStopped()) {
            System.out.println("[Ship] moveShip: ship.isStopped()=true, skipping");
            return;
        }

        // Update both vertical and horizontal velocities (gradual increase)
        ship.updateForces();

        // Debug log every 20 ticks (1 second)
        moveDebugCounter++;
        boolean shouldLog = (moveDebugCounter % 20 == 0);

        Vector3d dir = ship.getFlightDirection();

        // Horizontal velocity - gradual increase to max 1.0f
        double hVel = ship.getCurrentHorizontalForce();
        double vx = dir.x * hVel;
        double vz = dir.z * hVel;

        // Vertical velocity - gradual 0 -> 0.5, stops after max altitude
        double vy = 0.0;

        // Use tracked altitude
        double currentY = ship.getCurrentAltitude();
        double altitudeGain = currentY - ship.getStartingY();

        // Gradual vertical velocity - slows down as approaching max altitude
        double baseVy = ship.getCurrentVerticalForce();

        if (altitudeGain < Ship.ALTITUDE_SLOWDOWN_START) {
            // Below slowdown zone - full vertical speed
            vy = baseVy;
        } else if (altitudeGain < Ship.MAX_ALTITUDE_GAIN) {
            // In slowdown zone - gradually reduce vertical speed
            double t = (altitudeGain - Ship.ALTITUDE_SLOWDOWN_START) / (Ship.MAX_ALTITUDE_GAIN - Ship.ALTITUDE_SLOWDOWN_START);
            // t goes from 0 to 1, so vy goes from baseVy to nearly 0
            vy = baseVy * (1.0 - t) * (1.0 - t); // Quadratic slowdown for smoother transition
            // Minimum tiny velocity to avoid abrupt stop
            vy = Math.max(vy, 0.001);
        } else {
            // At max altitude - maintain very tiny upward to keep player stable
            vy = 0.001;
        }

        // Sync tracked altitude with actual position periodically (every 20 ticks)
        // This prevents altitude drift from accumulating
        if (moveDebugCounter % 20 == 0) {
            double actualY = getActualShipY(ship);
            ship.setCurrentAltitude(actualY);
        } else {
            // Between syncs, adjust based on velocity
            ship.adjustAltitude(vy);
        }

        // Create velocity vector
        Vector3d velocity = new Vector3d(vx, vy, vz);

        if (shouldLog) {
            System.out.println("[Ship] moveShip: velocity=" + velocity +
                             ", hForce=" + ship.getCurrentHorizontalForce() +
                             ", vForce=" + ship.getCurrentVerticalForce() +
                             ", isFlying=" + ship.isFlying());
        }

        // Set velocity on each BlockEntity (no accumulation)
        int applied = 0;
        int nullPhysics = 0;
        for (BlockEntity blockEntity : ship.getBlockEntities()) {
            try {
                if (blockEntity != null) {
                    var physics = blockEntity.getSimplePhysicsProvider();
                    if (physics != null) {
                        physics.setVelocity(velocity);
                        applied++;
                    } else {
                        nullPhysics++;
                    }
                }
            } catch (Exception e) {
                System.err.println("[Ship] setVelocity error: " + e.getMessage());
            }
        }

        if (shouldLog) {
            System.out.println("[Ship] moveShip: applied velocity to " + applied + " blocks, nullPhysics=" + nullPhysics);
        }

        // Spawn smoke particles while flying
        if (ship.isFlying()) {
            spawnSmokeParticles(ship);
        }
    }

    public static void clearShip() {
        stopFlightLoop();
        stopDescent();
        stopRotation();
        currentShip = null;
        currentEntityStore = null;
        currentWorld = null;
    }

    /**
     * Start gradual rotation towards target direction
     */
    public static void startGradualRotation(Vector3d targetDirection) {
        Ship ship = currentShip;
        if (ship == null) return;

        // Normalize target direction
        double length = Math.sqrt(targetDirection.x * targetDirection.x + targetDirection.z * targetDirection.z);
        if (length > 0) {
            targetRotationDirection = new Vector3d(targetDirection.x / length, 0, targetDirection.z / length);
        } else {
            return;
        }

        if (rotationRunning) {
            // Already rotating, just update target
            System.out.println("[Ship] Updated rotation target");
            return;
        }

        rotationRunning = true;

        final double ROTATION_SPEED = 0.15; // How fast to rotate (0-1, where 1 = instant)

        new Thread(() -> {
            System.out.println("[Ship] Gradual rotation started");
            System.out.println("[Ship] Current direction: " + ship.getFlightDirection());
            System.out.println("[Ship] Target direction: " + targetRotationDirection);

            while (rotationRunning) {
                try {
                    Ship currentShipRef = currentShip;
                    if (currentShipRef == null || targetRotationDirection == null) break;

                    Vector3d currentDir = currentShipRef.getFlightDirection();

                    // Calculate angle difference
                    double currentAngle = Math.atan2(currentDir.x, currentDir.z);
                    double targetAngle = Math.atan2(targetRotationDirection.x, targetRotationDirection.z);

                    // Find shortest rotation direction
                    double angleDiff = targetAngle - currentAngle;

                    // Normalize to -PI to PI
                    while (angleDiff > Math.PI) angleDiff -= 2 * Math.PI;
                    while (angleDiff < -Math.PI) angleDiff += 2 * Math.PI;

                    // Check if close enough
                    if (Math.abs(angleDiff) < 0.01) {
                        currentShipRef.setFlightDirection(targetRotationDirection);
                        System.out.println("[Ship] Rotation complete");
                        rotationRunning = false;
                        break;
                    }

                    // Interpolate towards target
                    double newAngle = currentAngle + angleDiff * ROTATION_SPEED;
                    Vector3d newDir = new Vector3d(Math.sin(newAngle), 0, Math.cos(newAngle));
                    currentShipRef.setFlightDirection(newDir);

                    System.out.println("[Ship] Rotating... angle diff: " + String.format("%.2f", Math.toDegrees(angleDiff)) + "°");

                    Thread.sleep(50);
                } catch (Exception e) {
                    System.err.println("[Ship] Rotation error: " + e.getMessage());
                }
            }
            System.out.println("[Ship] Rotation ended");
        }).start();
    }

    /**
     * Stop rotation
     */
    public static void stopRotation() {
        rotationRunning = false;
        targetRotationDirection = null;
    }

    /**
     * Spawn smoke particles behind the ship (engine exhaust effect)
     */
    private static void spawnSmokeParticles(Ship ship) {
        if (ship == null || currentEntityStore == null) return;

        smokeTickCounter++;
        if (smokeTickCounter < SMOKE_INTERVAL_TICKS) return;
        smokeTickCounter = 0;

        // Spawn smoke from rear of ship
        Vector3d flightDir = ship.getFlightDirection();
        Vector3i minCorner = ship.getMinCorner();
        Vector3i maxCorner = ship.getMaxCorner();

        double centerX = (minCorner.x + maxCorner.x) / 2.0 + 0.5;
        double centerZ = (minCorner.z + maxCorner.z) / 2.0 + 0.5;
        double shipY = ship.getCurrentAltitude();

        double shipLength = Math.max(maxCorner.x - minCorner.x, maxCorner.z - minCorner.z) / 2.0 + 1;
        double rearX = centerX - flightDir.x * shipLength;
        double rearZ = centerZ - flightDir.z * shipLength;

        int particleCount = 2 + random.nextInt(2);
        for (int i = 0; i < particleCount; i++) {
            double offsetX = (random.nextDouble() - 0.5) * 2.0;
            double offsetY = random.nextDouble() * 0.5;
            double offsetZ = (random.nextDouble() - 0.5) * 2.0;

            Vector3d particlePos = new Vector3d(
                rearX + offsetX,
                shipY + offsetY,
                rearZ + offsetZ
            );

            try {
                ParticleUtil.spawnParticleEffect(SMOKE_PARTICLE_ID, particlePos, currentEntityStore);
            } catch (Exception e) {
                // Ignore particle spawn errors
            }
        }
    }
}
