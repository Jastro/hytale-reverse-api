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
 * Rotate ship to face player's direction (Secondary click - right mouse button)
 */
public class ShipRotateInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<ShipRotateInteraction> CODEC = BuilderCodec.builder(
        ShipRotateInteraction.class,
        ShipRotateInteraction::new,
        SimpleInstantInteraction.CODEC
    ).build();

    public ShipRotateInteraction() {
        super();
    }

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldownHandler) {
        System.out.println("[ShipRotate] Rotate interaction triggered!");

        Ref<EntityStore> entityRef = context.getEntity();
        ComponentAccessor<EntityStore> commandBuffer = context.getCommandBuffer();

        if (entityRef == null || commandBuffer == null) {
            System.out.println("[ShipRotate] Entity ref or command buffer is null");
            return;
        }

        Entity entity = EntityUtils.getEntity(entityRef, commandBuffer);
        if (!(entity instanceof Player)) {
            System.out.println("[ShipRotate] Entity is not a player");
            return;
        }

        Ship ship = ShipManager.getCurrentShip();
        if (ship == null) {
            System.out.println("[ShipRotate] No ship exists! Use /se create first");
            return;
        }

        // Update ship direction to face where player is looking
        Plugin plugin = Plugin.getInstance();
        if (plugin != null && plugin.getShipController() != null) {
            plugin.getShipController().updateShipDirection(entityRef);
            System.out.println("[ShipRotate] Ship rotating to player direction");
        }
    }
}
