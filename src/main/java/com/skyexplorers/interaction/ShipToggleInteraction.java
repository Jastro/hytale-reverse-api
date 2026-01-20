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
import com.skyexplorers.ship.Ship;
import com.skyexplorers.ship.ShipManager;

/**
 * Toggle ship flight on/off (Primary click - left mouse button)
 */
public class ShipToggleInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<ShipToggleInteraction> CODEC = BuilderCodec.builder(
        ShipToggleInteraction.class,
        ShipToggleInteraction::new,
        SimpleInstantInteraction.CODEC
    ).build();

    public ShipToggleInteraction() {
        super();
    }

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldownHandler) {
        System.out.println("[ShipToggle] Toggle interaction triggered!");

        Ref<EntityStore> entityRef = context.getEntity();
        ComponentAccessor<EntityStore> commandBuffer = context.getCommandBuffer();

        if (entityRef == null || commandBuffer == null) {
            System.out.println("[ShipToggle] Entity ref or command buffer is null");
            return;
        }

        Entity entity = EntityUtils.getEntity(entityRef, commandBuffer);
        if (!(entity instanceof Player)) {
            System.out.println("[ShipToggle] Entity is not a player");
            return;
        }

        Ship ship = ShipManager.getCurrentShip();
        if (ship == null) {
            System.out.println("[ShipToggle] No ship exists! Use /se create first");
            return;
        }

        // Toggle flight
        boolean isActive = ship.isFlying() || ShipManager.isFlightLoopRunning();
        boolean isDescending = ShipManager.isDescentRunning();

        System.out.println("[ShipToggle] State check - isFlying: " + ship.isFlying() +
                          ", flightLoop: " + ShipManager.isFlightLoopRunning() +
                          ", descending: " + isDescending);

        if (isActive) {
            // Currently flying - stop and descend
            ShipManager.startDescentLoop();
            System.out.println("[ShipToggle] ENGINE STOPPED - Ship descending...");
        } else {
            // Not flying - start flight (also stops any ongoing descent)
            ShipManager.stopDescent();  // Make sure descent is stopped
            ShipManager.startFlightLoop();
            System.out.println("[ShipToggle] ENGINE STARTED - Ship is now flying!");
        }
    }
}
