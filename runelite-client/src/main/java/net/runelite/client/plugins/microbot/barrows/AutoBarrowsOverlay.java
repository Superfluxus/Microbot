package net.runelite.client.plugins.microbot.barrows;

import net.runelite.api.Varbits;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.barrows.models.TheBarrowsBrothers;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.text.DecimalFormat;

public class AutoBarrowsOverlay extends OverlayPanel {

    private static final DecimalFormat REWARD_POTENTIAL_FORMATTER = new DecimalFormat("##0.00%");

    AutoBarrowsPlugin plugin;

    @Inject
    AutoBarrowsOverlay(AutoBarrowsPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {

        panelComponent.getChildren().clear();

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Auto Barrows V" + AutoBarrowsScript.version)
                .color(Color.blue)
                .build());

        // Add the recommended prayer information to the panel
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Is fighting brother:")
                .right(String.valueOf(plugin.isPlayerFightingBrother()))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("In Crypt: ")
                .right(String.valueOf(plugin.isInCrypt()))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Prayer: ")
                .build());

        final int rewardPotential = rewardPotential();
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Potential")
                .right(REWARD_POTENTIAL_FORMATTER.format(rewardPotential / 1012f))
                .rightColor(rewardPotential >= 756 && rewardPotential < 881 ? Color.GREEN : rewardPotential < 631 ? Color.WHITE : Color.YELLOW)
                .build());

        for (TheBarrowsBrothers brother : TheBarrowsBrothers.values()) {
            final boolean brotherSlain = Microbot.getVarbitValue(brother.getKilledVarbit()) > 0;
            String slain = brotherSlain ? "\u2713" : "\u2717";
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(brother.getName())
                    .right(slain)
                    .rightFont(FontManager.getDefaultFont())
                    .rightColor(brotherSlain ? Color.GREEN : Color.RED)
                    .build());
        }

        panelComponent.getChildren().add(LineComponent.builder()
                .left(plugin.barrowsScript.state.toString())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left(Microbot.status.toString())
                .build());

        return panelComponent.render(graphics);
    }

    private int rewardPotential() {
        int brothers = Microbot.getVarbitValue(Varbits.BARROWS_KILLED_AHRIM)
                + Microbot.getVarbitValue(Varbits.BARROWS_KILLED_DHAROK)
                + Microbot.getVarbitValue(Varbits.BARROWS_KILLED_GUTHAN)
                + Microbot.getVarbitValue(Varbits.BARROWS_KILLED_KARIL)
                + Microbot.getVarbitValue(Varbits.BARROWS_KILLED_TORAG)
                + Microbot.getVarbitValue(Varbits.BARROWS_KILLED_VERAC);
        return Microbot.getVarbitValue(Varbits.BARROWS_REWARD_POTENTIAL) + brothers * 2;
    }
}
