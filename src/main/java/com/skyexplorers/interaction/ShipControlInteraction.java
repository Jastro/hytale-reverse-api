package com.skyexplorers.interaction;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.skyexplorers.Plugin;
import com.skyexplorers.ship.Ship;
import com.skyexplorers.ship.ShipManager;

/**
 * Custom interaction that triggers ship control functionality.
 * When the player uses an item with this interaction, it toggles flight
 * and updates ship direction.
 */
public class ShipControlInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<ShipControlInteraction> CODEC = BuilderCodec.builder(
        ShipControlInteraction.class,
        ShipControlInteraction::new,
        SimpleInstantInteraction.CODEC
    ).build();

    public ShipControlInteraction() {
        super();
    }

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldownHandler) {
        System.out.println("[ShipControl] Interaction triggered! Type: " + type);

        // Get the entity from the context
        Ref<EntityStore> entityRef = context.getEntity();
        ComponentAccessor<EntityStore> commandBuffer = context.getCommandBuffer();

        if (entityRef == null || commandBuffer == null) {
            System.out.println("[ShipControl] Entity ref or command buffer is null");
            return;
        }

        Entity entity = EntityUtils.getEntity(entityRef, commandBuffer);
        if (!(entity instanceof Player)) {
            System.out.println("[ShipControl] Entity is not a player: " + entity);
            return;
        }

        Player player = (Player) entity;
        System.out.println("[ShipControl] Player found: " + player);

        // Get current ship
        Ship ship = ShipManager.getCurrentShip();
        if (ship == null) {
            System.out.println("[ShipControl] No ship exists! Use /se create first");
            return;
        }

        // Update ship direction to face where player is looking
        Plugin plugin = Plugin.getInstance();
        if (plugin != null && plugin.getShipController() != null) {
            plugin.getShipController().updateShipDirection(entityRef);
        }

        // Toggle flight
        if (ship.isFlying() || ShipManager.isFlightLoopRunning()) {
            // Currently flying - stop
            ShipManager.startDescentLoop();
            System.out.println("[ShipControl] ENGINE STOPPED - Ship descending...");
        } else {
            // Not flying - start
            ShipManager.startFlightLoop();
            System.out.println("[ShipControl] ENGINE STARTED - Ship is now flying!");
        }
    }
}
