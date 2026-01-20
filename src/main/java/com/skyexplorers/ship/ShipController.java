package com.skyexplorers.ship;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controls ship using an item held by the player.
 *
 * How it works:
 * - Hold the control item (compass/helm) and the ship rotates to face your direction
 * - Right-click (Use/Secondary) to toggle flight on/off
 *
 * Based on hytale-friends tracker system by kidandcat
 */
public class ShipController {

    // The item that controls the ship - must be a tool/weapon with interactions defined
    // The custom item that controls the ship (has ShipControl interaction)
    public static final String CONTROL_ITEM = "Ship_Controller";

    // Cooldown between activations (milliseconds)
    private static final long COOLDOWN_MS = 500;

    // How often to update ship direction when holding item (milliseconds)
    private static final long DIRECTION_UPDATE_INTERVAL = 100;

    // Track last activation time per player (for cooldown)
    private final Map<Ref<EntityStore>, Long> lastActivation = new ConcurrentHashMap<>();

    // Track players currently holding the control item
    private final Map<Ref<EntityStore>, PlayerRef> activeControllers = new ConcurrentHashMap<>();

    // Direction update thread
    private Thread directionUpdateThread;
    private volatile boolean running = false;

    public ShipController() {
        // Constructor
    }

    /**
     * Start the direction update loop.
     * This continuously updates ship direction based on active controller's facing.
     */
    public void start() {
        if (running) return;
        running = true;

        directionUpdateThread = new Thread(() -> {
            System.out.println("[ShipController] Direction update loop started");
            while (running) {
                try {
                    updateShipDirectionFromControllers();
                    Thread.sleep(DIRECTION_UPDATE_INTERVAL);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    System.err.println("[ShipController] Error in direction update: " + e.getMessage());
                }
            }
            System.out.println("[ShipController] Direction update loop stopped");
        });
        directionUpdateThread.setDaemon(true);
        directionUpdateThread.start();
    }

    /**
     * Stop the direction update loop.
     */
    public void stop() {
        running = false;
        if (directionUpdateThread != null) {
            directionUpdateThread.interrupt();
        }
        activeControllers.clear();
    }

    /**
     * Called when a player interacts with anything.
     * We check if they're holding the control item and handle accordingly.
     */
    public void onPlayerInteract(PlayerInteractEvent event) {
        try {
            // Get the item in hand directly from event
            ItemStack itemInHand = event.getItemInHand();
            if (itemInHand == null) {
                // No item in hand - try to remove from active controllers
                Ref<EntityStore> playerRef = event.getPlayerRef();
                if (playerRef != null) {
                    removeController(playerRef);
                }
                return;
            }

            // Check if holding the control item
            String itemId = itemInHand.getItemId();
            if (itemId == null || !itemId.contains(CONTROL_ITEM)) {
                // Not holding control item - remove from active controllers
                Ref<EntityStore> playerRef = event.getPlayerRef();
                if (playerRef != null) {
                    removeController(playerRef);
                }
                return;
            }

            // Player is holding the control item!
            System.out.println("[ShipController] Player holding control item: " + itemId);

            // Get player entity and ref
            Player playerEntity = event.getPlayer();
            Ref<EntityStore> playerRef = event.getPlayerRef();

            if (playerEntity == null || playerRef == null) {
                System.out.println("[ShipController] Could not get player from event");
                return;
            }

            // We need a PlayerRef to send messages - try to find it
            // For now, just track the player ref for direction updates

            // Add to active controllers (for continuous direction tracking)
            // We'll need to get the PlayerRef later for messages
            addController(playerRef, playerEntity);

            // Check action type for toggle behavior
            InteractionType actionType = event.getActionType();
            System.out.println("[ShipController] Action type: " + actionType);

            // Right-click (Use/Secondary) toggles flight AND updates direction
            if (actionType == InteractionType.Use || actionType == InteractionType.Secondary) {
                // First update ship direction to face where player is looking
                updateShipDirection(playerRef);
                // Then toggle flight
                handleToggleFlight(playerEntity, playerRef);
            }

        } catch (Exception e) {
            System.err.println("[ShipController] Error handling interaction: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Add a player as an active controller.
     */
    private void addController(Ref<EntityStore> playerRef, Player playerEntity) {
        if (!activeControllers.containsKey(playerRef)) {
            // Store the player reference - we'll use it for direction tracking
            // Note: We need PlayerRef for messages, but we can use Player for transform
            activeControllers.put(playerRef, null); // Placeholder
            System.out.println("[ShipController] Player now controlling ship direction");
        }
    }

    /**
     * Remove a player from active controllers.
     */
    private void removeController(Ref<EntityStore> playerRef) {
        if (activeControllers.remove(playerRef) != null) {
            System.out.println("[ShipController] Player stopped controlling ship");
        }
    }

    /**
     * Toggle flight on/off when player activates control item.
     */
    private void handleToggleFlight(Player playerEntity, Ref<EntityStore> playerRef) {
        // Check cooldown
        long now = System.currentTimeMillis();
        Long lastTime = lastActivation.get(playerRef);

        if (lastTime != null && (now - lastTime) < COOLDOWN_MS) {
            return; // Still on cooldown
        }
        lastActivation.put(playerRef, now);

        // Get current ship
        Ship ship = ShipManager.getCurrentShip();
        if (ship == null) {
            System.out.println("[ShipController] No ship exists! Use /se create first");
            return;
        }

        // Toggle flight
        if (ship.isFlying() || ShipManager.isFlightLoopRunning()) {
            // Currently flying - stop
            ShipManager.startDescentLoop();
            System.out.println("[ShipController] ENGINE STOPPED - Ship descending...");
        } else {
            // Not flying - start
            ShipManager.startFlightLoop();
            System.out.println("[ShipController] ENGINE STARTED - Ship is now flying!");
        }
    }

    /**
     * Update ship direction based on active controllers.
     * Called periodically from the update thread.
     */
    private void updateShipDirectionFromControllers() {
        Ship ship = ShipManager.getCurrentShip();
        if (ship == null || activeControllers.isEmpty()) {
            return;
        }

        // We need to track players with their transforms
        // Since we only have Ref<EntityStore>, we need another approach
        // For now, this will be a placeholder - the actual direction update
        // happens when the player interacts (they must right-click to steer)

        // TODO: Implement continuous direction tracking
        // This would require storing the Player entity or finding another way
        // to get the player's transform from just the Ref<EntityStore>
    }

    /**
     * Update ship direction to face where player is looking.
     * Called when player uses the control item.
     *
     * Uses the EntityStore from ShipManager to access player's transform.
     *
     * @param playerRef Reference to the player entity
     */
    public void updateShipDirection(Ref<EntityStore> playerRef) {
        Ship ship = ShipManager.getCurrentShip();
        if (ship == null || playerRef == null) {
            return;
        }

        // Get the entity store from ShipManager
        var entityStore = ShipManager.getEntityStore();
        if (entityStore == null) {
            System.out.println("[ShipController] No entity store available - create a ship first");
            return;
        }

        try {
            // Get TransformComponent from the player entity
            TransformComponent transform = entityStore.getComponent(playerRef, TransformComponent.getComponentType());
            if (transform == null) {
                System.out.println("[ShipController] Cannot get player transform component");
                return;
            }

            Vector3f rotation = transform.getRotation();
            double yaw = Math.toRadians(rotation.y);

            // Convert yaw to direction vector (horizontal only)
            Vector3d playerDirection = new Vector3d(-Math.sin(yaw), 0, Math.cos(yaw));

            // Get current ship direction
            Vector3d currentDir = ship.getFlightDirection();

            // Only update if direction has changed significantly (avoid jitter)
            double dot = currentDir.x * playerDirection.x + currentDir.z * playerDirection.z;
            if (dot < 0.99) { // About 8 degrees difference
                // Start gradual rotation towards player's facing direction
                ShipManager.startGradualRotation(playerDirection);
                System.out.println("[ShipController] Rotating ship to face player direction");
            }
        } catch (Exception e) {
            System.err.println("[ShipController] Error updating direction: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if a player is currently controlling the ship.
     */
    public boolean isControlling(Ref<EntityStore> playerRef) {
        return activeControllers.containsKey(playerRef);
    }

    /**
     * Get count of active controllers.
     */
    public int getActiveControllerCount() {
        return activeControllers.size();
    }
}
