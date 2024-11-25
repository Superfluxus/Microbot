package net.runelite.client.plugins.microbot.example;

import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.Plugin;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.storm.plugins.PlayerMonitor.PlayerMonitorConfig;
import net.runelite.client.plugins.microbot.storm.plugins.PlayerMonitor.PlayerMonitorPlugin;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.*;

import java.util.Random;

import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;

import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

enum ExampleState {
    COLLECT_SUPPLIES,
    GO_TO_FIGHT,
    BLOOD_SPLATS,
    JAGUARS,
    CLAIM_CHEST,
    IDLE
}

public class ExampleScript extends Script {

    @Inject
    private Client client;
    // Constructor to initialize plugin

    public ExampleScript(ExamplePlugin plugin, ExampleConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    private volatile boolean running = true;

    public boolean isRunning() {
        return running;
    }

    public void shutdown() {
        running = false;
    }

    @Inject
    private ExampleConfig config;
    @Inject
    private ExamplePlugin plugin;

    public void setConfigAndPlugin(ExampleConfig config, ExamplePlugin plugin) {
        this.config = config;
        this.plugin = plugin;
    }


    @Inject

    //private ExampleState currentState = ExampleState.COLLECT_SUPPLIES;

    private ExampleState currentState = ExampleState.COLLECT_SUPPLIES;
    // IDs and constants
    private static final int SUPPLY_CRATE_ID = 51371;
    private static final int TREE_ID = 51358;
    private static final int GRUBBY_SAPLING_ID = 51365;// Tree ID
    private static final int GRUB_ID = 29078;
    private static final int PESTLE_AND_MORTAR_ID = 233;
    private static final int PASTE_ID = 29079;
    private static final int VIAL_ID = 227;
    private static final int DESIRED_VIAL_COUNT = 5;
    private static final int ROCK_ID = 51359; // Rock ID
    private static final String ACTION = "Trap"; // The action to perform
    private static final int STOVE_ID = 51362;
    private static final int RAW_LIZARD_ID = 29076; // The ID of the Supply Crate
    private static final int COOKED_LIZARD_ID = 29077;
    private static final int ONEDOSE_ID = 29083;
    private static final int TWODOSE_ID = 29082;
    private static final int THREEDOSE_ID = 29081;
    private static final int FOURDOSE_ID = 29080;
    private static final int GRUBPASTE_ID = 29079;
    private static final int BUTTERFLY_NET_ID = 10010;
    private static final int EN_ROUTE_STOVE = 51362;
    private boolean claimChest = false;

    private int sleepMin = 600;  // Default 500ms
    private int sleepMax = 2100; // Default 2100ms
    private int sleepTarget = 1200; // Midpoint as default

    private static final WorldPoint[] ROCK_LOCATIONS = {
            new WorldPoint(1388, 9709, 0),
            new WorldPoint(1390, 9707, 0),
            new WorldPoint(1392, 9707, 0),
            new WorldPoint(1394, 9705, 0)
    };
    @Override
    public boolean run() {
        Microbot.enableAutoRunOn = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    return;
                }
                if (!this.isRunning()) {
                    return;
                }

                // Handle the current state
                switch (currentState) {
                    case COLLECT_SUPPLIES:
                        preparePhase();
                        break;

                    case GO_TO_FIGHT:
                        goToFight();
                        break;

                    case BLOOD_SPLATS:
                        bloodsplats();
                        break;

                    case JAGUARS:
                        ensureSafeSpot();
                        break;

                    case CLAIM_CHEST:
                        claimChestReward();
                        break;

                    case IDLE:
                        ensureSafeSpot();
                        break;

                    default:
                        Microbot.log("Unknown state: " + currentState);
                        break;
                }

            } catch (Exception ex) {
                Microbot.log("Error in state " + currentState + ": " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }
    public void transitionToState(ExampleState newState) {
        if (currentState != newState) {
            Microbot.log("Transitioning from " + currentState + " to " + newState);
            currentState = newState;
        } else {
            Microbot.log("Already in state: " + currentState);
        }
    }

    public void MoveToSafeSpot(WorldPoint safeSpotLocation) {
        Microbot.log("Moving to new safe spot at: " + safeSpotLocation);
        if (Rs2Player.getWorldLocation().distanceTo(safeSpotLocation) > 1) {
            Rs2Walker.walkFastCanvas(safeSpotLocation);
            sleep(calculateSleepDuration());
            sleepUntilTrue(() -> (!Rs2Player.isMoving() &&
                    Rs2Player.getWorldLocation().distanceTo(safeSpotLocation) <= 1), 300, 2500);
            Microbot.log("Reached the safe spot.");
        } else {
            Microbot.log("Already at the safe spot.");
        }
        Rs2Npc.interact(13011, "Attack");
    }


    private NPC findNpcById(int npcId) {
        return Microbot.getClient().getNpcs().stream()
                .filter(npc -> npc.getId() == npcId)
                .findFirst()
                .orElse(null);
    }

    private boolean isCurrentLocationDangerous() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        return plugin.getDangerousTiles().contains(playerLocation);
    }

    private void moveToSafeTile() {
        WorldPoint safeTile = plugin.getSafeTiles().stream()
                .sorted(Comparator.comparingInt(tile -> tile.distanceTo(Rs2Player.getWorldLocation())))
                .findFirst()
                .orElse(null);

        if (safeTile != null) {
            Rs2Walker.walkFastCanvas(safeTile);
            sleep(calculateSleepDuration());
            sleepUntilTrue(() -> Rs2Player.getWorldLocation().equals(safeTile), 300, 5000);
            Microbot.log("Moved to safe tile: " + safeTile);
        } else {
            Microbot.log("No safe tiles available. Staying in place.");
        }
    }


    private int calculateSleepDuration() {


        // Create a Random object
        Random random = new Random();

        // Fallback default values if not provided
        if (sleepMin == 0) sleepMin = 500;  // Default 500ms
        if (sleepMax == 0) sleepMax = 1500; // Default 1500ms
        if (sleepTarget == 0) sleepTarget = (sleepMin + sleepMax) / 2; // Midpoint as default


        // Calculate the mean (average) of sleepMin and sleepMax, adjusted by sleepTarget
        double mean = (sleepMin + sleepMax + sleepTarget) / 3.0;

        // Calculate the standard deviation with added noise
        double noiseFactor = 0.2; // Adjust the noise factor as needed (0.0 to 1.0)
        double stdDeviation = Math.abs(sleepTarget - mean) / 3.0 * (1 + noiseFactor * (random.nextDouble() - 0.5) * 2);

        // Generate a random number following a normal distribution
        int sleepDuration;
        do {
            // Generate a random number using nextGaussian method, scaled by standard deviation
            sleepDuration = (int) Math.round(mean + random.nextGaussian() * stdDeviation);
        } while (this.isRunning() && sleepDuration < sleepMin || sleepDuration > sleepMax); // Ensure the duration is within the specified range

        return sleepDuration;
    }

    public class MissingResources {
        private int missingPotionDoses;
        private int missingCookedLizards;

        // Constructor
        public MissingResources(int missingPotionDoses, int missingCookedLizards) {
            this.missingPotionDoses = missingPotionDoses;
            this.missingCookedLizards = missingCookedLizards;
        }

        // Getters
        public int getMissingPotionDoses() {
            return missingPotionDoses;
        }

        public int getMissingCookedLizards() {
            return missingCookedLizards;
        }

        // Check if no resources are missing
        public boolean isComplete() {
            return missingPotionDoses <= 0 && missingCookedLizards <= 0;
        }

        @Override
        public String toString() {
            return "MissingResources{" +
                    "missingPotionDoses=" + missingPotionDoses +
                    ", missingCookedLizards=" + missingCookedLizards +
                    '}';
        }
    }

    private MissingResources validateInventoryDetailed() {
        int requiredPotionDoses = config.requiredPotionDoses();
        int requiredCookedLizards = config.requiredCookedLizards();

        int potionDoses = Rs2Inventory.count(FOURDOSE_ID) * 4 +
                Rs2Inventory.count(THREEDOSE_ID) * 3 +
                Rs2Inventory.count(TWODOSE_ID) * 2 +
                Rs2Inventory.count(ONEDOSE_ID);
        int cookedLizards = Rs2Inventory.count(COOKED_LIZARD_ID);

        int missingPotionDoses = Math.max(0, requiredPotionDoses - potionDoses);
        int missingCookedLizards = Math.max(0, requiredCookedLizards - cookedLizards);

        return new MissingResources(missingPotionDoses, missingCookedLizards);
    }

    public void preparePhase() {
        Microbot.log("State: Preparation Phase");
        moveToStove(); // Ensure we are in the correct area

        // Validate inventory and start preparation tasks
        validateAndStartPreparation();
    }

    private void moveToStove() {
        WorldPoint stovePoint = new WorldPoint(1376, 9710, 0);

        if (!Rs2Player.getWorldLocation().equals(stovePoint)) {
            Microbot.log("Moving to stove...");
            Rs2Walker.walkTo(stovePoint);
            sleep(calculateSleepDuration());
            sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(stovePoint) <= 3);

        }
    }

    private void validateAndStartPreparation() {
        MissingResources missingResources = validateInventoryDetailed();

        if (missingResources.getMissingPotionDoses() > 0) {
            collectPotionIngredients();
        } else if (missingResources.getMissingCookedLizards() > 0) {
            collectAndCookLizards();
        } else {
            restoreRunEnergyIfNeeded();
            sleep(calculateSleepDuration());
            completePreparation();
        }
    }

    private void collectPotionIngredients() {
        Microbot.log("Collecting potion ingredients...");

        // Step 1: Take herblore supplies
        if (Rs2GameObject.interact(SUPPLY_CRATE_ID, "Take-from Herblore")) {
            Microbot.log("Taking herblore supplies...");
            sleepUntilTrue(() -> Rs2Inventory.count("Vial of water") > 2, 100, 5000);

        }

        // Step 2: Collect grubs from sapling
        if (Rs2GameObject.interact(GRUBBY_SAPLING_ID, "Collect-from")) {
            Microbot.log("Collecting grubs from sapling...");
            sleepUntilTrue(() -> Rs2Inventory.count("Moonlight grub") > 3, 100, 5000);

        }

        // Step 3: Process grubs into paste
        while (Rs2Inventory.count(GRUB_ID) > 0) {
            if (Rs2Inventory.combine(PESTLE_AND_MORTAR_ID, GRUB_ID)) {
                Microbot.log("Processing grubs into paste...");
                sleep(calculateSleepDuration());
                sleepUntilTrue(() -> Rs2Inventory.count("Moonlight grub paste") < 2, 100, 5000);
            }
        }

        // Step 4: Combine paste with vials
        if (Rs2Inventory.combine(PASTE_ID, VIAL_ID)) {
            Microbot.log("Creating potions...");
            sleep(calculateSleepDuration());
            sleepUntilTrue(() -> Rs2Inventory.count("Moonlight potion") > 0, 100, 5000);
        }


        // Drop excess resources
        int[] itemsToDrop = {ONEDOSE_ID, TWODOSE_ID, THREEDOSE_ID, VIAL_ID, GRUBPASTE_ID, PESTLE_AND_MORTAR_ID};
        for (int itemId : itemsToDrop) {
            while (Rs2Inventory.contains(itemId)) {
                Rs2Inventory.drop(itemId);
                sleep(250,400);
            }
        }

        // Validate again
        validateAndStartPreparation();
    }

    private void collectAndCookLizards() {
        Microbot.log("Collecting and cooking lizards...");

        if (!Rs2Inventory.contains("Rope")) {
            Rs2GameObject.interact(SUPPLY_CRATE_ID, "Take-from Hunting");
            sleep(calculateSleepDuration());
            sleepUntilTrue(() -> Rs2Inventory.count("Rope") > 0, 100, 5000);
        }

        interactWithRocksAndRustleTree();
        cookLizards();
        validateAndStartPreparation();
    }

    private void interactWithRocksAndRustleTree() {
        for (WorldPoint location : ROCK_LOCATIONS) {
            GameObject rock = Rs2GameObject.findObject(ROCK_ID, location);
            if (rock != null && Rs2GameObject.interact(rock, ACTION)) {
                Microbot.log("Interacting with rock at: " + location);
                sleep(calculateSleepDuration());
                sleepUntilTrue(() -> !Rs2Player.isMoving(), 300, 5000);
            }
        }

        if (Rs2GameObject.interact(TREE_ID, "Rustle")) {
            Microbot.log("Rustling tree to spawn lizards...");
            sleep(calculateSleepDuration());
            sleepUntilTrue(() -> !Rs2Player.isMoving(), 300, 5000);
        }

        while (Rs2GroundItem.exists("Raw moss lizard", 20)) {
            Rs2GroundItem.loot("Raw moss lizard", 20);
            sleep(calculateSleepDuration());
        }
    }

    private void cookLizards() {
        if (Rs2GameObject.interact(STOVE_ID, "Cook")) {
            Microbot.log("Cooking lizards...");
            sleep(calculateSleepDuration());
            sleepUntilTrue(() -> !Rs2Inventory.hasItem(RAW_LIZARD_ID), 300, 5000);
        }
    }

    private void completePreparation() {
        Microbot.log("Preparation complete. Moving to GO_TO_FIGHT state.");
        transitionToState(ExampleState.GO_TO_FIGHT);
    }
    private void claimChest() {
        Microbot.log("Opening chest...");
        Rs2GameObject.interact(51346, "Claim");

        sleepUntilTrue(() -> Rs2Widget.isWidgetVisible(56885268), 100, 5000); // Wait for chest reward widget to appear

        if (Rs2Widget.isWidgetVisible(56885268)) {
            Rs2Widget.clickWidget(56885268); // Confirm rewards
            Microbot.log("Rewards claimed.");
        } else {
            Microbot.log("Failed to claim rewards. Widget not visible.");
        }
    }

    private void refuelAtStove() {
        Microbot.log("Refueling at stove...");
        WorldPoint nearStove = new WorldPoint(1351, 9581, 0);
        Rs2Walker.walkTo(nearStove);
        sleep(calculateSleepDuration());
        sleepUntilTrue(() -> !Rs2Player.isAnimating(), 300, 5000);
        Rs2GameObject.interact(EN_ROUTE_STOVE, "Make-cuppa");
        sleep(calculateSleepDuration());
    }



    private void claimChestReward() {
        Microbot.log("State: Claiming Chest Reward");

        // Navigate to the chest room
        Rs2Walker.walkTo(new WorldPoint(1513, 9563, 0));
        sleep(calculateSleepDuration());
        sleepUntilTrue(() -> (!Rs2Player.isMoving() && !Rs2Player.isAnimating()), 300, 5000);

        // Interact with the chest
        claimChest();

        // Refuel en route
        refuelAtStove();

        // Transition to the next state
        transitionToState(ExampleState.GO_TO_FIGHT);
    }



    private boolean safeWalkTo(WorldPoint target) {
        if (target == null) {
            Microbot.log("Target WorldPoint is null. Cannot walk.");
            return false;
        }

        try {
            return Rs2Walker.walkFastCanvas(target);
        } catch (Exception ex) {
            Microbot.log("Error while walking to target: " + ex.getMessage());
            return false;
        }
    }

    private boolean safeInteractWithGameObject(int objectId, String action) {
        try {
            return Rs2GameObject.interact(objectId, action);
        } catch (Exception ex) {
            Microbot.log("Error while interacting with object ID " + objectId + ": " + ex.getMessage());
            return false;
        }
    }

    private boolean isChestWidgetVisible() {
        boolean visible = Rs2Widget.isWidgetVisible(56950788);
        if (visible) {
            Microbot.log("Chest widget is visible. Transitioning to CLAIM_CHEST.");
        }
        return visible;
    }

    private void updateFoodCount() {
        int cookedLizardsCount = Rs2Inventory.count(COOKED_LIZARD_ID);
        plugin.setFoodCount(cookedLizardsCount);
        Microbot.log("Updated food count: " + cookedLizardsCount);
    }

    private void restoreRunEnergyIfNeeded() {
        int runEnergy = Microbot.getClient().getEnergy();
        if (runEnergy < 6500) {
            Microbot.log("Low run energy detected. Restoring...");
            WorldPoint stovePoint = new WorldPoint(1376, 9710, 0);
            Rs2Walker.walkFastCanvas(stovePoint);
            sleep(calculateSleepDuration());
            sleepUntilTrue(() -> (!Rs2Player.isMoving() && !Rs2Player.isAnimating()), 300, 5000);
            Rs2GameObject.interact(STOVE_ID, "Make-cuppa");
            sleepUntilTrue(() -> (!Rs2Player.isMoving() && !Rs2Player.isAnimating()), 300, 5000);
        }
        Rs2Player.toggleRunEnergy(true);
        Microbot.log("Run energy restored and toggled on.");
    }

    private void ensureBoostedDefence() {
        int rawDefence = Rs2Player.getRealSkillLevel(Skill.DEFENCE);
        int boostedDefence = Rs2Player.getBoostedSkillLevel(Skill.DEFENCE);

        if (boostedDefence - rawDefence < 5) {
            Microbot.log("Boosting defence with Moonlight potion...");
            Rs2Inventory.interact("Moonlight potion", "Drink");
            sleepUntilTrue(() -> Rs2Player.getBoostedSkillLevel(Skill.DEFENCE) > rawDefence, 100, 5000);
        } else {
            Microbot.log("Defence boost not needed.");
        }
    }

    private void waitForJaguarToDespawn() {
        Microbot.log("Checking for Jaguar phase...");
        NPC jaguar = findNpcById(13021);

        if (jaguar == null) {
            Microbot.log("No Jaguar present. Proceeding to fight.");
            return;
        }

        Microbot.log("Jaguar detected. Waiting for despawn...");
        sleepUntilTrue(() -> findNpcById(13021) == null, 100, 10000);
        Microbot.log("Jaguar has despawned. Proceeding to fight.");
    }

    public void handleIdleState() {
        Microbot.log("State: IDLE");
    }


    private void goToFight() {
        Microbot.log("State: GoToFight");
        int runEnergy = Microbot.getClient().getEnergy();
        if (runEnergy < 6500) {
            WorldPoint stovePoint = new WorldPoint(1376, 9710, 0);
            Rs2Walker.walkFastCanvas(stovePoint);
            sleep(calculateSleepDuration());
            sleepUntilTrue(() -> (!Rs2Player.isMoving() && !Rs2Player.isAnimating()), 300, 25000);
            Rs2GameObject.interact(STOVE_ID, "Make-cuppa");
        }

        // Check if chest is visible
        if (isChestWidgetVisible()) {
            transitionToState(ExampleState.CLAIM_CHEST);
            return;
        }

        // Update food count
        updateFoodCount();

        // Walk to fight area and prepare
        Rs2Walker.walkTo(new WorldPoint(1418, 9632, 0));
        sleep(calculateSleepDuration());
        sleepUntilTrue(() -> (!Rs2Player.isMoving() && !Rs2Player.isAnimating()), 300, 25000);
        Rs2Walker.walkFastCanvas(new WorldPoint(1410, 9630, 0));
        sleep(calculateSleepDuration());
        sleepUntilTrue(() -> (!Rs2Player.isMoving() && !Rs2Player.isAnimating()), 300, 5000);

        // Drink potion if needed
        ensureBoostedDefence();

        // Handle Jaguar phase if active
        waitForJaguarToDespawn();

        // Interact with fight object
        if (safeInteractWithGameObject(51372, "Use")) {
            Microbot.log("Successfully started the fight.");
            plugin.setBossDead(false);
            transitionToState(ExampleState.IDLE);
        } else {
            Microbot.log("Failed to start the fight.");
        }
    }

    private void bloodsplats() {
        Microbot.log("Entering blood splats phase...");

        if (isCurrentLocationDangerous()) {
            Microbot.log("Player is on a dangerous tile. Moving to a safe location...");
            moveToSafeTile();
        }

        Microbot.log("Listening for dangerous tile events...");
        // No need for a while loop here; events will handle the transitions
    }

    private void waitForNextTick(int currentTick) {
        while (this.isRunning() && plugin.getElapsedTicks() == currentTick) {
            sleep(10, 20); // Brief sleep to prevent busy-waiting
        }
    }

    private void handleBossDeath(){
        Microbot.log("awooga");
    }

    public void moveBackwardsToBloodSpot() {
        WorldPoint bloodSpot = plugin.getClosestBloodSplat(Rs2Player.getWorldLocation());
        if (bloodSpot != null && Rs2Player.getWorldLocation().distanceTo(bloodSpot) > 1) {
            Microbot.log("Moving backwards to blood spot at: " + bloodSpot);
            Rs2Walker.walkFastCanvas(bloodSpot);
            sleepUntilTrue(() -> Rs2Player.getWorldLocation().equals(bloodSpot));
        } else {
            Microbot.log("No valid blood spot found. Staying in position.");
        }
    }

    public void attackJaguar() {
        NPC jaguar = findNpcById(13021);
        if (jaguar == null) {
            Microbot.log("No Jaguar found. Skipping attack.");
            return;
        }

        Rs2Npc.interact(jaguar, "Attack");
    }


    public void ensureSafeSpot() {
        WorldPoint safeSpotLocation = plugin.getCurrentSafeSpot();
        if (safeSpotLocation == null) {
            Microbot.log("No safe spot available. Staying in place.");
            return;
        }

        if (Rs2Player.getWorldLocation().distanceTo(safeSpotLocation) > 1) {
            Microbot.log("Moving to safe spot: " + safeSpotLocation);
            Rs2Walker.walkFastCanvas(safeSpotLocation);
            sleep(calculateSleepDuration());
            sleepUntilTrue(() -> (Rs2Player.getWorldLocation().distanceTo(safeSpotLocation) <= 1) && !Rs2Player.isMoving(), 300, 5000);
            Microbot.log("Reached safe spot.");
        }
    }

    public ExampleState getCurrentState() {
        return currentState;
    }
}