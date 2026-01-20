package com.skyexplorers;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.skyexplorers.commands.ShipCommand;
import com.skyexplorers.ship.ShipController;
import com.skyexplorers.interaction.ShipToggleInteraction;
import com.skyexplorers.interaction.ShipRotateInteraction;

/**
 * Sky Explorers Plugin - Flying ships for Hytale
 * Build and pilot flying ships between floating islands.
 *
 * @author Jacobo Caraballo
 * @version 0.3.0
 */
public class Plugin extends JavaPlugin {

    private static Plugin instance;
    private ShipController shipController;

    public Plugin(JavaPluginInit init) {
        super(init);
        instance = this;
        System.out.println("[Sky Explorers] Plugin loaded!");
    }

    @Override
    protected void setup() {
        System.out.println("[Sky Explorers] Registering commands...");

        // Register ship interactions
        try {
            // Primary (left click) - Toggle engine on/off
            getCodecRegistry(Interaction.CODEC).register(
                "ShipToggle",
                ShipToggleInteraction.class,
                ShipToggleInteraction.CODEC
            );
            System.out.println("[Sky Explorers] Registered ShipToggle interaction (left click)");

            // Secondary (right click) - Rotate to player direction
            getCodecRegistry(Interaction.CODEC).register(
                "ShipRotate",
                ShipRotateInteraction.class,
                ShipRotateInteraction.CODEC
            );
            System.out.println("[Sky Explorers] Registered ShipRotate interaction (right click)");
        } catch (Exception e) {
            System.err.println("[Sky Explorers] Failed to register interactions: " + e.getMessage());
            e.printStackTrace();
        }

        try {
            getCommandRegistry().registerCommand(new ShipCommand());
            System.out.println("[Sky Explorers] Command registered: /se");
            System.out.println("[Sky Explorers]   /se create - Create ship from gold corners");
            System.out.println("[Sky Explorers]   /se fly    - Start flying");
            System.out.println("[Sky Explorers]   /se stop   - Stop and land");
            System.out.println("[Sky Explorers]   /se rotate - Rotate to facing direction");

        } catch (Exception e) {
            System.err.println("[Sky Explorers] Failed to register commands: " + e.getMessage());
            e.printStackTrace();
        }

        // Initialize ship controller (item-based control)
        shipController = new ShipController();

        // Register global interaction event to detect item usage
        getEventRegistry().registerGlobal(PlayerInteractEvent.class, event -> {
            System.out.println("[Sky Explorers] PlayerInteractEvent fired! Action: " + event.getActionType());
            shipController.onPlayerInteract(event);
        });

        System.out.println("[Sky Explorers] Ship controller initialized");
        System.out.println("[Sky Explorers] Use " + ShipController.CONTROL_ITEM + " to control ship:");
        System.out.println("[Sky Explorers]   Left-click  = Toggle engine on/off");
        System.out.println("[Sky Explorers]   Right-click = Rotate to your direction");
    }

    @Override
    protected void start() {
        System.out.println("[Sky Explorers] Starting ship controller...");
        if (shipController != null) {
            shipController.start();
        }
    }

    @Override
    protected void shutdown() {
        System.out.println("[Sky Explorers] Shutting down...");
        if (shipController != null) {
            shipController.stop();
        }
    }

    public ShipController getShipController() {
        return shipController;
    }

    public static Plugin getInstance() {
        return instance;
    }
}
