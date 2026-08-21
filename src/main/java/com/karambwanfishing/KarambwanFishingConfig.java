package com.karambwanfishing;

import net.runelite.client.config.*;

import java.awt.*;

@ConfigGroup("karambwanfishing")
public interface KarambwanFishingConfig extends Config
{
	@ConfigSection(
			name = "AFK Timer",
			description = "Timer and overlay for AFK fishing",
			position = 1
	)
	String timerSection = "timerSection";

	@ConfigItem(
			position = 0,
			keyName = "timerEnabled",
			name = "Enable timer",
			description = "Enable afk fishing timer",
			section = "timerSection"
	)
	default boolean timerEnabled()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
			position = 2,
			keyName = "overlayColor",
			name = "Overlay color",
			description = "Color of the screen overlay",
			section = "timerSection"
	)
	default Color overlayColor()
	{
		return new Color(0, 0, 0, 210);
	}

	@Range(
			min = 0,
			max = 255
	)
	@ConfigItem(
			position = 3,
			keyName = "overlayOpacity",
			name = "Overlay opacity",
			description = "How strong the overlay appears",
			section = "timerSection"
	)
	default int overlayOpacity()
	{
		return 210;
	}

	@ConfigItem(
			position = 4,
			keyName = "showChatbox",
			name = "Show chatbox",
			description = "Leave the chatbox visible through the dim overlay",
			section = "timerSection"
	)
	default boolean showChatbox()
	{
		return false;
	}

	@ConfigItem(
			position = 6,
			keyName = "disableOnHover",
			name = "Disable on hover",
			description = "Hide the overlay while your mouse is over the game canvas",
			section = "timerSection"
	)
	default boolean disableOnHover()
	{
		return false;
	}

	@ConfigItem(
			position = 7,
			keyName = "hideOnMouseMovement",
			name = "Hide on mouse movement",
			description = "Hide the overlay after moving your mouse",
			section = "timerSection"
	)
	default boolean hideOnMouseMovement()
	{
		return false;
	}

	@ConfigItem(
			position = 9,
			keyName = "hideOnNonFishingClick",
			name = "Hide on non-fishing click",
			description = "Hide the overlay when left-clicking anything other than the fishing spot. Click the fishing spot again to show it",
			section = "timerSection"
	)
	default boolean hideOnNonFishingClick()
	{
		return false;
	}

	@Range(
			min = 1,
			max = 100
	)
	@ConfigItem(
			position = 8,
			keyName = "mouseIdleGameTicks",
			name = "Reappear after idle (ticks)",
			description = "How many game ticks your mouse must be idle before the overlay appears again. 1 game tick = 0.6 seconds",
			section = "timerSection"
	)
	default int mouseIdleGameTicks()
	{
		return 5;
	}
}
