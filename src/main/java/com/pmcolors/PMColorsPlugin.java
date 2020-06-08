/*
 * Copyright (c) 2020, PresNL
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.pmcolors;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ObjectArrays;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import javax.inject.Inject;
import javax.swing.*;

import com.pmcolors.ui.PMColorsPanel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import org.apache.commons.lang3.ArrayUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.runelite.api.ScriptID.OPEN_PRIVATE_MESSAGE_INTERFACE;

@Slf4j
@PluginDescriptor(
	name = "PM Colors",
	description = "Allows you to highlight certain users private messages in specified colors, split private chat only",
	tags = {"private", "message", "chat", "highlight"}
)
public class PMColorsPlugin extends Plugin
{
	private static final String PLUGIN_NAME = "PM Colors";

	private static final String CONFIG_GROUP = "pmcolors";
	private static final String CONFIG_KEY = "highlightedplayers";

	private static final Pattern SENDER_NAME_PATTERN = Pattern.compile(
		"(From|To) (<img=[0-9]>)?(.*?):");
	private static final Pattern LOGGED_PATTERN = Pattern.compile(
		"(.*?) has logged (out|in)");

	@Inject
	private Client client;

	@Inject
	private PMColorsConfig config;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Getter
	@Inject
	private ColorPickerManager colorPickerManager;

	private static final ImmutableList<String> AFTER_OPTIONS = ImmutableList.of("Message", "Lookup");

	private static final String HIGHLIGHT = "Highlight";
	private static final String REMOVE_HIGHLIGHT = "Remove highlight";

	private String selectedPlayer = null;

	private PMColorsPanel pluginPanel;

	private NavigationButton navigationButton;

	@Getter
	private final List<PlayerHighlight> highlightedPlayers = new ArrayList<>();

	@Override
	protected void startUp() throws Exception
	{
		selectedPlayer = null;

		loadConfig(configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY)).forEach(highlightedPlayers::add);

		pluginPanel = new PMColorsPanel(this);
		pluginPanel.rebuild();

		final BufferedImage icon = ImageUtil.getResourceStreamFromClass(getClass(), "/icon_marker.png");

		navigationButton = NavigationButton.builder()
				.tooltip(PLUGIN_NAME)
				.icon(icon)
				.priority(5)
				.panel(pluginPanel)
				.build();

		clientToolbar.addNavigation(navigationButton);

	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navigationButton);

		highlightedPlayers.clear();
		pluginPanel = null;

		navigationButton = null;

		selectedPlayer = null;
	}

	@Provides
	PMColorsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PMColorsConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (highlightedPlayers.isEmpty() && event.getGroup().equals(CONFIG_GROUP) && event.getKey().equals(CONFIG_KEY))
		{
			loadConfig(event.getNewValue()).forEach(highlightedPlayers::add);
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired scriptPostFired)
	{
		/* 663 and 664 seems to be the scripts that update the private message window
		* sending a private message and opening the interface also seem to reset the colors
		* so that's why they are included
		* 924 is chatbox parent which seems to update whenever you switch to a different tab
		* which also breaks the highlighting so we update then too
		* 175 is when you switch chat tabs (public, private etc) which also breaks the highlighting
		* 80 updates the chatbox when you close an interface so you can guess what happens there
		*/
		if (scriptPostFired.getScriptId() == 663
				|| scriptPostFired.getScriptId() == 664
				|| scriptPostFired.getScriptId() == OPEN_PRIVATE_MESSAGE_INTERFACE
				|| scriptPostFired.getScriptId() == 112
				|| scriptPostFired.getScriptId() == 924
				|| scriptPostFired.getScriptId() == 175
				|| scriptPostFired.getScriptId() == 80)
		{
			highlightMessages();
		}
	}

	private void highlightMessages()
	{
		Widget pmWidget = client.getWidget(WidgetInfo.PRIVATE_CHAT_MESSAGE);
		if (pmWidget != null)
		{
			Widget[] children = pmWidget.getDynamicChildren();
			for (int i = 0; i < children.length; i+= 2)
			{
				Widget sender = children[i];
				Widget msg = children[i + 1];
				if (sender != null && msg != null)
				{
					String senderName = sender.getText();
					Matcher senderNameMatcher = SENDER_NAME_PATTERN.matcher(senderName);
					Matcher loggedMatcher = LOGGED_PATTERN.matcher(senderName);
					if (loggedMatcher.find())
					{
						String name = loggedMatcher.group(1);

						PlayerHighlight player = highlightedPlayers.stream()
								.filter(p -> name.equalsIgnoreCase(p.getName()))
								.findAny()
								.orElse(null);

						if (player != null)
						{
							Color color = player.getColor();
							sender.setTextColor(color.getRGB());
							msg.setTextColor(color.getRGB());
						}
						else
						{
							sender.setTextColor(JagexColors.CHAT_PRIVATE_MESSAGE_TEXT_TRANSPARENT_BACKGROUND.getRGB());
							msg.setTextColor(JagexColors.CHAT_PRIVATE_MESSAGE_TEXT_TRANSPARENT_BACKGROUND.getRGB());
						}
						continue;
					}

					if (senderNameMatcher.find())
					{
						String name = senderNameMatcher.group(3);
						PlayerHighlight player = highlightedPlayers.stream()
								.filter(p -> name.equalsIgnoreCase(p.getName()))
								.findAny()
								.orElse(null);
						if (player != null)
						{
							Color color = player.getColor();
							sender.setTextColor(color.getRGB());
							msg.setTextColor(color.getRGB());
						}
						else
						{
							sender.setTextColor(JagexColors.CHAT_PRIVATE_MESSAGE_TEXT_TRANSPARENT_BACKGROUND.getRGB());
							msg.setTextColor(JagexColors.CHAT_PRIVATE_MESSAGE_TEXT_TRANSPARENT_BACKGROUND.getRGB());
						}
					}
				}
			}
		}
	}

	// adapted from the hiscore plugin
	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		final int groupId = WidgetInfo.TO_GROUP(event.getActionParam1());

		// Look for "Message" on friends list
		if (groupId == WidgetInfo.PRIVATE_CHAT_MESSAGE.getGroupId())
		{
			//// Friends have color tagsString option = event.getOption();
			selectedPlayer = Text.toJagexName(Text.removeTags(event.getTarget()));
			if (!AFTER_OPTIONS.contains(event.getOption()) )
			{
				return;
			}

			// Build "Add Note" or "Edit Note" menu entry
			final MenuEntry highlight = new MenuEntry();
			PlayerHighlight player = highlightedPlayers.stream()
					.filter(p -> selectedPlayer.equalsIgnoreCase(p.getName()))
					.findAny()
					.orElse(null);

			if (player == null)
				highlight.setOption(HIGHLIGHT);
			else
				highlight.setOption(REMOVE_HIGHLIGHT);
			highlight.setType(MenuAction.RUNELITE.getId());
			highlight.setTarget(event.getTarget()); //Preserve color codes here
			highlight.setParam0(event.getActionParam0());
			highlight.setParam1(event.getActionParam1());
			highlight.setIdentifier(event.getIdentifier());
			// Add menu entry
			insertMenuEntry(highlight, client.getMenuEntries());
		}
		else
		{
			selectedPlayer = null;
		}
	}

	@Subscribe
	public void onPlayerMenuOptionClicked(PlayerMenuOptionClicked event)
	{
		if (event.getMenuOption().equals(HIGHLIGHT))
		{
			finishCreation(false, selectedPlayer, config.highlightColor());
		}
		else if (event.getMenuOption().equals(REMOVE_HIGHLIGHT))
		{
			PlayerHighlight player = highlightedPlayers.stream()
					.filter(p -> selectedPlayer.equalsIgnoreCase(p.getName()))
					.findAny()
					.orElse(null);
			if (player != null)
				deleteHighlight(player);
		}
		selectedPlayer = null;
	}

	// adapted from the hiscore plugin
	private void insertMenuEntry(MenuEntry newEntry, MenuEntry[] entries)
	{
		MenuEntry[] newMenu = ObjectArrays.concat(entries, newEntry);
		int menuEntryCount = newMenu.length;
		ArrayUtils.swap(newMenu, menuEntryCount - 1, menuEntryCount - 2);
		client.setMenuEntries(newMenu);
	}

	public Color getDefaultColor()
	{
		return config.highlightColor();
	}

	public void finishCreation(boolean aborted, String name, Color color)
	{
		if (!aborted && name != null && color != null)
		{
			PlayerHighlight highlight = new PlayerHighlight();
			highlight.setName(Text.toJagexName(name));
			highlight.setColor(color);

			highlightedPlayers.add(highlight);

			SwingUtilities.invokeLater(() -> pluginPanel.rebuild());
			updateConfig();
		}

		pluginPanel.setCreation(false);
	}

	public void deleteHighlight(final PlayerHighlight highlight)
	{
		highlightedPlayers.remove(highlight);
		updateConfig();
		SwingUtilities.invokeLater(() -> pluginPanel.rebuild());

	}

	public void updateConfig()
	{
		if (highlightedPlayers.isEmpty())
		{
			configManager.unsetConfiguration(CONFIG_GROUP, CONFIG_KEY);
			return;
		}

		final Gson gson = new Gson();
		final String json = gson
				.toJson(highlightedPlayers.stream().collect(Collectors.toList()));
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY, json);
		highlightMessages();
	}

	private Stream<PlayerHighlight> loadConfig(String json)
	{
		if (Strings.isNullOrEmpty(json))
		{
			return Stream.empty();
		}

		final Gson gson = new Gson();
		final List<PlayerHighlight> playerHiglightData = gson.fromJson(json, new TypeToken<ArrayList<PlayerHighlight>>()
		{
		}.getType());

		return playerHiglightData.stream().filter(Objects::nonNull);
	}
}
