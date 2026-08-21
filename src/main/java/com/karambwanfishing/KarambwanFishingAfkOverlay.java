package com.karambwanfishing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Area;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

public class KarambwanFishingAfkOverlay extends Overlay {

    private static final double SECONDS_PER_GAME_TICK = 0.6;

    private final Client client;
    private final KarambwanFishingPlugin plugin;
    private final KarambwanFishingConfig config;

    @Inject
    KarambwanFishingAfkOverlay(Client client, KarambwanFishingPlugin plugin, KarambwanFishingConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPriority(OverlayPriority.HIGHEST);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!plugin.shouldRenderOverlay())
        {
            return null;
        }

        Dimension canvasSize = graphics.getClipBounds().getSize();

        Color overlayColor = getOverlayColor();

        if (overlayColor.getAlpha() > 0)
        {
            drawDimOverlay(graphics, canvasSize, overlayColor);
        }

        String mainText = getMainText();
        String tierText = getSlotsTex();

        Font originalFont = graphics.getFont();

        graphics.setFont(new Font("SansSerif", Font.BOLD, 34));
        drawCenteredText(graphics, mainText, canvasSize.width, canvasSize.height / 2 - 6);

        graphics.setFont(new Font("SansSerif", Font.BOLD, 22));
        drawCenteredText(graphics, tierText, canvasSize.width, canvasSize.height / 2 + 34);

        graphics.setFont(originalFont);
        return null;
    }

    private Color getOverlayColor()
    {
        Color baseColor = config.overlayColor();
        int alpha = Math.max(0, Math.min(255, config.overlayOpacity()));

        return new Color(
                baseColor.getRed(),
                baseColor.getGreen(),
                baseColor.getBlue(),
                alpha
        );
    }

    private void drawDimOverlay(Graphics2D graphics, Dimension canvasSize, Color overlayColor)
    {
        Area overlayArea = new Area(new Rectangle(0, 0, canvasSize.width, canvasSize.height));

        if (config.showChatbox())
        {
            subtractWidgetBounds(overlayArea, InterfaceID.Chatbox.UNIVERSE, 0);
            subtractWidgetBounds(overlayArea, InterfaceID.Chatbox.CHATAREA, 0);
            subtractWidgetBounds(overlayArea, InterfaceID.ToplevelOsrsStretch.CHAT_CONTAINER, 0);
            subtractWidgetBounds(overlayArea, InterfaceID.ToplevelPreEoc.CHAT_CONTAINER, 0);
        }

        graphics.setColor(overlayColor);
        graphics.fill(overlayArea);
    }

    private void subtractWidgetBounds(Area overlayArea, int componentId, int padding)
    {
        Widget widget = client.getWidget(componentId);

        if (widget == null || widget.isHidden())
        {
            return;
        }

        Rectangle bounds = widget.getBounds();

        if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
        {
            return;
        }

        Rectangle paddedBounds = new Rectangle(
                bounds.x - padding,
                bounds.y - padding,
                bounds.width + padding * 2,
                bounds.height + padding * 2
        );

        overlayArea.subtract(new Area(paddedBounds));
    }

    private String getMainText()
    {
        if (plugin.getEstimatedFishingTicks() >= 0)
        {
            return "Estimated time remaining: " + formatTicks(plugin.getEstimatedFishingTicks());
        }

        return "Fishing :)";
    }

    private String getSlotsTex()
    {
        if (plugin.getInventorySlotsLeft() > 0)
        {
            return "Inventory slots left: " + plugin.getInventorySlotsLeft();
        }

        return "";
    }

    private void drawCenteredText(Graphics2D graphics, String text, int canvasWidth, int y)
    {
        if (text == null || text.isEmpty())
        {
            return;
        }

        FontMetrics metrics = graphics.getFontMetrics();
        int x = (canvasWidth - metrics.stringWidth(text)) / 2;

        graphics.setColor(Color.BLACK);
        graphics.drawString(text, x + 2, y + 2);

        graphics.setColor(Color.WHITE);
        graphics.drawString(text, x, y);
    }

    private String formatTicks(int ticks)
    {
        int totalSeconds = (int) Math.round(ticks * SECONDS_PER_GAME_TICK);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}
