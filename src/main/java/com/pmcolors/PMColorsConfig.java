package com.pmcolors;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

import java.awt.*;

@ConfigGroup("pmcolors")
public interface PMColorsConfig extends Config
{
	@ConfigItem(
			keyName = "highlightedPlayers",
			name = "Highlighted Players",
			description = "Configures which players to highlight when they send you a pm. Format: (player), (player)",
			position = 0
	)
	default String getHighlightPlayers()
	{
		return "";
	}

	@ConfigItem(
			keyName = "highlightedPlayers",
			name = "",
			description = ""
	)
	void setHighlightedPlayers(String key);

	@ConfigItem(
			keyName = "highlightColor",
			name = "Highlight color",
			description = "Configures which color to highlight the players with",
			position = 14
	)
	default Color highlightColor() { return Color.ORANGE; }
}
