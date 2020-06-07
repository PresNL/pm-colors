package com.pmcolors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ObjectArrays;
import com.google.inject.Provides;
import javax.inject.Inject;
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
import net.runelite.client.util.Text;
import org.apache.commons.lang3.ArrayUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.runelite.api.ScriptID.OPEN_PRIVATE_MESSAGE_INTERFACE;

@Slf4j
@PluginDescriptor(
	name = "PM Colors",
	description = "Allows you to highlight certain users private messages, split private chat only",
	tags = {"private", "message", "chat", "highlight"}
)
public class PMColorsPlugin extends Plugin
{
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

	private static final ImmutableList<String> AFTER_OPTIONS = ImmutableList.of("Message", "Lookup");

	private static final String HIGHLIGHT = "Highlight";
	private static final String REMOVE_HIGHLIGHT = "Remove highlight";

	private String selectedPlayer = null;

	private List<String> highlightedPlayerList = new CopyOnWriteArrayList<>();
	@Override
	protected void startUp() throws Exception
	{
		executor.execute(this::reset);
		selectedPlayer = null;
	}

	@Override
	protected void shutDown() throws Exception
	{
		highlightedPlayerList = null;
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
		if (event.getGroup().equals("pmcolors"))
		{
			executor.execute(this::reset);
		}
	}

	private void reset()
	{
		highlightedPlayerList = Text.fromCSV(config.getHighlightPlayers());
		HighlightMessages();
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
			HighlightMessages();
		}
	}

	private void HighlightMessages()
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
						if (highlightedPlayerList.contains(name))
						{
							Color color = config.highlightColor();
							sender.setTextColor(color.getRGB());
							msg.setTextColor(color.getRGB());
						}
						else
						{
							sender.setTextColor(65535);
							msg.setTextColor(65535);
						}
						continue;
					}

					if (senderNameMatcher.find())
					{
						String name = senderNameMatcher.group(3);

						if (highlightedPlayerList.contains(name))
						{
							Color color = config.highlightColor();
							sender.setTextColor(color.getRGB());
							msg.setTextColor(color.getRGB());
						}
						else
						{
							sender.setTextColor(65535);
							msg.setTextColor(65535);
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
			if (!highlightedPlayerList.contains(selectedPlayer))
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
			final List<String> highlightedPlayerSet = new ArrayList<>(highlightedPlayerList);

			highlightedPlayerSet.add(selectedPlayer);

			config.setHighlightedPlayers(Text.toCSV(highlightedPlayerSet));
		}
		else if (event.getMenuOption().equals(REMOVE_HIGHLIGHT))
		{
			final List<String> highlightedPlayerSet = new ArrayList<>(highlightedPlayerList);

			highlightedPlayerSet.removeIf(selectedPlayer::equalsIgnoreCase);

			config.setHighlightedPlayers(Text.toCSV(highlightedPlayerSet));
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
}
