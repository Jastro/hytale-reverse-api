package com.skyexplorers.ship;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a flying ship made of BlockEntities
 */
public class Ship {

    // Ship bounds (corners)
    private Vector3i minCorner;
    private Vector3i maxCorner;

    // All BlockEntity references that make up the ship
    private List<Ref<EntityStore>> blockRefs = new ArrayList<>();

    // Direct BlockEntity references for addForce
    private List<BlockEntity> blockEntities = new ArrayList<>();

    // Original block types for each position (to recreate if needed)
    private List<String> blockTypes = new ArrayList<>();
    private List<Vector3d> blockPositions = new ArrayList<>();

    // Flight state
    private boolean isFlying = false;
    private double currentVerticalForce = 0.0;
    private double currentHorizontalForce = 0.0;

    // Flight direction (normalized)
    private Vector3d flightDirection = new Vector3d(0, 0, 1);

    // Starting Y position to track altitude
    private double startingY = 0.0;

    // Current altitude (tracked during flight/descent)
    private double currentAltitude = 0.0;

    // Owner of this ship (player who created it)
    private Ref<EntityStore> owner = null;

    // Pilot - player currently flying the ship (will be kept on the ship)
    private PlayerRef pilot = null;

    // Pilot's offset from ship center (where they stand on the ship)
    private Vector3d pilotOffset = new Vector3d(0, 1, 0);

    // Flight limits
    public static final float MAX_VERTICAL_FORCE = 0.5f;      // Max vertical force
    public static final float MAX_HORIZONTAL_FORCE = 2.5f;    // Max horizontal force (increased for faster forward speed)
    public static final double VERTICAL_ACCELERATION = 0.01;  // Gradual vertical increase (0 -> 0.1 -> 0.2 -> 0.5)
    public static final double HORIZONTAL_ACCELERATION = 0.05; // How fast horizontal builds up (faster acceleration)
    public static final double MAX_ALTITUDE_GAIN = 40.0;      // Max altitude gain
    public static final double ALTITUDE_SLOWDOWN_START = 30.0; // Start slowing down vertical at this altitude

    public Ship(Vector3i minCorner, Vector3i maxCorner) {
        this.minCorner = minCorner;
        this.maxCorner = maxCorner;
    }

    public void addBlock(Ref<EntityStore> ref, BlockEntity blockEntity, String blockType, Vector3d position) {
        blockRefs.add(ref);
        blockEntities.add(blockEntity);
        blockTypes.add(blockType);
        blockPositions.add(position);
    }

    public List<BlockEntity> getBlockEntities() {
        return blockEntities;
    }

    public List<Ref<EntityStore>> getBlockRefs() {
        return blockRefs;
    }

    public List<Vector3d> getBlockPositions() {
        return blockPositions;
    }

    public List<String> getBlockTypes() {
        return blockTypes;
    }

    public boolean isFlying() {
        return isFlying;
    }

    public void setFlying(boolean flying) {
        this.isFlying = flying;
    }

    public double getStartingY() {
        return startingY;
    }

    public void setStartingY(double y) {
        this.startingY = y;
        this.currentAltitude = y;
    }

    public double getCurrentAltitude() {
        return currentAltitude;
    }

    public void setCurrentAltitude(double altitude) {
        this.currentAltitude = altitude;
    }

    public void adjustAltitude(double delta) {
        this.currentAltitude += delta;
    }

    public double getCurrentVerticalForce() {
        return currentVerticalForce;
    }

    public double getCurrentHorizontalForce() {
        return currentHorizontalForce;
    }

    public Vector3d getFlightDirection() {
        return flightDirection;
    }

    public void setFlightDirection(Vector3d direction) {
        // Normalize the direction
        double length = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (length > 0) {
            this.flightDirection = new Vector3d(direction.x / length, 0, direction.z / length);
        }
    }

    public Ref<EntityStore> getOwner() {
        return owner;
    }

    public void setOwner(Ref<EntityStore> ownerRef) {
        this.owner = ownerRef;
    }

    public boolean isOwner(Ref<EntityStore> playerRef) {
        return owner != null && owner.equals(playerRef);
    }

    public Vector3i getMinCorner() {
        return minCorner;
    }

    public Vector3i getMaxCorner() {
        return maxCorner;
    }

    public int getBlockCount() {
        return blockRefs.size();
    }

    /**
     * Update forces (gradual increase for both vertical and horizontal)
     * Call this every tick
     */
    public void updateForces() {
        if (isFlying) {
            // Gradually increase vertical force: 0 -> 0.1 -> 0.2 -> ... -> 0.5
            currentVerticalForce = Math.min(currentVerticalForce + VERTICAL_ACCELERATION, MAX_VERTICAL_FORCE);
            // Gradually increase horizontal force up to max
            currentHorizontalForce = Math.min(currentHorizontalForce + HORIZONTAL_ACCELERATION, MAX_HORIZONTAL_FORCE);
        }
    }

    /**
     * Check if vertical force should be applied based on altitude
     */
    public boolean shouldApplyVerticalForce(double currentY) {
        return (currentY - startingY) < MAX_ALTITUDE_GAIN;
    }

    /**
     * Check if ship has completely stopped
     */
    public boolean isStopped() {
        return !isFlying && currentHorizontalForce <= 0 && currentVerticalForce <= 0;
    }

    /**
     * Reset forces for a new flight
     */
    public void resetForces() {
        currentVerticalForce = 0;
        currentHorizontalForce = 0;
    }

    public void clear() {
        blockRefs.clear();
        blockEntities.clear();
        blockTypes.clear();
        blockPositions.clear();
        isFlying = false;
        currentVerticalForce = 0;
        currentHorizontalForce = 0;
        startingY = 0;
    }
}
