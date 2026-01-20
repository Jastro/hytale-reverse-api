package com.skyexplorers.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.skyexplorers.ship.Ship;
import com.skyexplorers.ship.ShipManager;
import com.skyexplorers.ship.ShipController;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.entity.entities.Player;
import java.awt.Color;
import java.util.List;

/**
 * /se - Main ship command with subcommands:
 *   /se create - Create ship from gold corners
 *   /se fly    - Start flying
 *   /se stop   - Stop and descend
 *   /se rotate - Rotate towards player facing direction (gradual)
 */
public class ShipCommand extends AbstractPlayerCommand {

    private static final int SCAN_RADIUS = 20;

    public ShipCommand() {
        super("se", "Ship control command");
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(
            CommandContext context,
            Store<EntityStore> entityStore,
            Ref<EntityStore> entityRef,
            PlayerRef player,
            World world
    ) {
        String input = context.getInputString();

        // Debug logging
        System.out.println("[Sky Explorers] Raw input: '" + input + "'");

        // Try multiple parsing approaches
        String subcommand = "";

        // Approach 1: Remove command prefix (with or without slash)
        String args = input.toLowerCase();
        if (args.startsWith("/se ")) {
            args = args.substring(4).trim();
        } else if (args.startsWith("se ")) {
            args = args.substring(3).trim();
        } else if (args.startsWith("/se")) {
            args = args.substring(3).trim();
        } else if (args.startsWith("se")) {
            args = args.substring(2).trim();
        }

        System.out.println("[Sky Explorers] Parsed args: '" + args + "'");

        if (args.isEmpty()) {
            showHelp(player);
            return;
        }

        String[] parts = args.split("\\s+");
        subcommand = parts[0];

        switch (subcommand) {
            case "create":
                handleCreate(player, world, entityStore, entityRef);
                break;
            case "fly":
                handleFly(player);
                break;
            case "stop":
                handleStop(player);
                break;
            case "rotate":
                String degreesArg = parts.length > 1 ? parts[1] : "90";
                handleRotate(player, degreesArg);
                break;
            default:
                player.sendMessage(Message.raw("Unknown subcommand: " + subcommand).color(Color.RED));
                showHelp(player);
        }
    }

    private void showHelp(PlayerRef player) {
        player.sendMessage(Message.raw("=== Sky Explorers Commands ===").color(Color.CYAN).bold(true));
        player.sendMessage(Message.raw("/se create - Create ship from gold corners").color(Color.WHITE));
        player.sendMessage(Message.raw("/se fly - Start flying").color(Color.WHITE));
        player.sendMessage(Message.raw("/se stop - Stop and land").color(Color.WHITE));
        player.sendMessage(Message.raw("/se rotate [degrees] - Rotate ship (default 90)").color(Color.WHITE));
    }

    private void handleCreate(PlayerRef player, World world, Store<EntityStore> entityStore, Ref<EntityStore> entityRef) {
        player.sendMessage(Message.raw("Scanning for gold block corners...").color(Color.YELLOW));

        Vector3d playerPos = player.getTransform().getPosition();

        List<Vector3i> corners = ShipManager.findCorners(world, playerPos, SCAN_RADIUS);

        if (corners.size() < 4) {
            player.sendMessage(Message.raw("Need 4 gold blocks for corners! Found: " + corners.size()).color(Color.RED));
            player.sendMessage(Message.raw("Place " + ShipManager.CORNER_BLOCK_ID + " at 4 corners").color(Color.GRAY));
            return;
        }

        if (corners.size() > 4) {
            player.sendMessage(Message.raw("Found " + corners.size() + " gold blocks, using first 4").color(Color.YELLOW));
            corners = corners.subList(0, 4);
        }

        // Get player's facing direction
        Vector3f rotation = player.getTransform().getRotation();
        double yaw = Math.toRadians(rotation.y);
        Vector3d flightDirection = new Vector3d(-Math.sin(yaw), 0, Math.cos(yaw));

        player.sendMessage(Message.raw("Creating ship...").color(Color.YELLOW));

        Ship ship = ShipManager.createShip(world, entityStore, corners, flightDirection);

        if (ship == null) {
            player.sendMessage(Message.raw("Failed to create ship!").color(Color.RED));
            return;
        }

        ship.setOwner(entityRef);

        player.sendMessage(Message.raw("Ship created! " + ship.getBlockCount() + " blocks").color(Color.GREEN).bold(true));

        // Give the player the control item
        try {
            // Get the Player entity from the EntityStore using the entity reference
            Player playerEntity = entityStore.getComponent(entityRef, Player.getComponentType());
            if (playerEntity != null) {
                Inventory inventory = playerEntity.getInventory();
                if (inventory != null) {
                    ItemStack controlItem = new ItemStack(ShipController.CONTROL_ITEM, 1);
                    // Add to hotbar first, if full it goes to storage
                    inventory.getHotbar().addItemStack(controlItem);
                    player.sendMessage(Message.raw("You received a Ship Controller!").color(Color.CYAN));
                    player.sendMessage(Message.raw("Right-click to fly/stop and steer!").color(Color.GRAY));
                } else {
                    player.sendMessage(Message.raw("Use /se fly to take off").color(Color.CYAN));
                }
            } else {
                player.sendMessage(Message.raw("Use /se fly to take off").color(Color.CYAN));
            }
        } catch (Exception e) {
            System.err.println("[Sky Explorers] Failed to give control item: " + e.getMessage());
            player.sendMessage(Message.raw("Use /se fly to take off").color(Color.CYAN));
        }
    }

    private void handleFly(PlayerRef player) {
        Ship ship = ShipManager.getCurrentShip();
        if (ship == null) {
            player.sendMessage(Message.raw("No ship exists! Use /se create first").color(Color.RED));
            return;
        }

        if (ship.isFlying()) {
            player.sendMessage(Message.raw("Ship is already flying!").color(Color.YELLOW));
            return;
        }

        ShipManager.startFlightLoop();
        player.sendMessage(Message.raw("ENGINE STARTED").color(Color.GREEN).bold(true));
        player.sendMessage(Message.raw("Ship is now flying").color(Color.CYAN));
    }

    private void handleStop(PlayerRef player) {
        Ship ship = ShipManager.getCurrentShip();
        if (ship == null) {
            player.sendMessage(Message.raw("No ship exists!").color(Color.RED));
            return;
        }

        if (!ship.isFlying() && !ShipManager.isFlightLoopRunning()) {
            player.sendMessage(Message.raw("Ship is not flying!").color(Color.YELLOW));
            return;
        }

        ShipManager.startDescentLoop();
        player.sendMessage(Message.raw("ENGINE STOPPED").color(Color.RED).bold(true));
        player.sendMessage(Message.raw("Ship descending...").color(Color.YELLOW));
    }

    private void handleRotate(PlayerRef player, String degreesStr) {
        Ship ship = ShipManager.getCurrentShip();
        if (ship == null) {
            player.sendMessage(Message.raw("No ship exists!").color(Color.RED));
            return;
        }

        if (!ship.isFlying()) {
            player.sendMessage(Message.raw("Ship must be flying to rotate! Use /se fly first").color(Color.YELLOW));
            return;
        }

        // Parse degrees
        double degrees;
        try {
            degrees = Double.parseDouble(degreesStr);
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("Invalid degrees: " + degreesStr).color(Color.RED));
            return;
        }

        // Get current direction and rotate by degrees
        Vector3d currentDir = ship.getFlightDirection();
        double currentAngle = Math.atan2(currentDir.x, currentDir.z);
        double newAngle = currentAngle + Math.toRadians(degrees);

        Vector3d targetDirection = new Vector3d(Math.sin(newAngle), 0, Math.cos(newAngle));

        player.sendMessage(Message.raw("Rotating " + (int)degrees + " degrees").color(Color.GRAY));
        ShipManager.startGradualRotation(targetDirection);
        player.sendMessage(Message.raw("ROTATING...").color(Color.MAGENTA).bold(true));
    }
}
