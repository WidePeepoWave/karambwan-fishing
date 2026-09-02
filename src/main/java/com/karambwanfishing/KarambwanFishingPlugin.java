package com.karambwanfishing;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import java.awt.event.MouseEvent;


@PluginDescriptor(
	name = "Karambwan AFK Helper"
)
public class KarambwanFishingPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private KarambwanFishingConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private KarambwanFishingAfkOverlay overlay;

	@Inject
	private ClientThread clientThread;

	@Inject
	private KarambwanBarrel barrel;

	private static final int CLIENT_TICKS_PER_GAME_TICK = 30;
	private static final int KARAM_FISHING_SPOT_NPC_ID = 4712;

	private NPC karamNpc;

	private boolean currentlyFishingKarams = false;
	private boolean supressedByNonFishingClick = false;
	private int estimatedFishingTicks = -1;
	private int inventorySlotsLeft = -1;

	private final MouseListener mouseListener = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent mouseEvent)
		{
			if (mouseEvent.getButton() != MouseEvent.BUTTON1)
			{
				return mouseEvent;
			}

			handleLeftClick();

			return mouseEvent;
		}
	};

	@Override
	protected void startUp() {
		overlayManager.add(overlay);
		mouseManager.registerMouseListener(mouseListener);

		supressedByNonFishingClick = false;
		resetFishing();

		// seed the barrel in case the plugin was enabled while already logged in
		barrel.reset();
		clientThread.invokeLater(() ->
		{
			ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
			if (inventory != null)
			{
				barrel.onInventoryChanged(inventory);
			}
		});
	}

	@Override
	protected void shutDown() {
		mouseManager.unregisterMouseListener(mouseListener);
		overlayManager.remove(overlay);

		supressedByNonFishingClick = false;
		resetFishing();
		barrel.reset();
	}

	@Provides
	KarambwanFishingConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(KarambwanFishingConfig.class);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		if (inventory != null) {
			inventorySlotsLeft = 28 - inventory.count();
			barrel.onInventoryChanged(inventory);
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		barrel.onChatMessage(event);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		switch (event.getMenuAction())
		{
			case CC_OP:
			case CC_OP_LOW_PRIORITY:
				Widget widget = event.getWidget();
				if (widget != null)
				{
					barrel.onMenuOptionClicked(widget.getItemId(), event.getMenuOption());
				}
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != InterfaceID.OBJECTBOX || !barrel.wasCheckClicked())
		{
			return;
		}

		// the text is not populated yet when the interface loads
		clientThread.invokeLater(() ->
		{
			Widget widget = client.getWidget(InterfaceID.Objectbox.TEXT);
			if (widget != null)
			{
				barrel.applyCheckText(widget.getText());
			}
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case HOPPING:
			case LOGGING_IN:
			case LOGIN_SCREEN:
				resetFishing();
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();

		if (npc.getId() != KARAM_FISHING_SPOT_NPC_ID)
		{
			return;
		}

		karamNpc = npc;
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (event.getNpc().equals(karamNpc))
		{
			karamNpc = null;
			estimatedFishingTicks = -1;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		barrel.onGameTick();

		Player player = client.getLocalPlayer();

		if (player == null)
		{
			currentlyFishingKarams = false;
			supressedByNonFishingClick = false;
			return;
		}

		if (karamNpc == null)
		{
			scanForKaramFishingNpc();
		}

		if (karamNpc == null)
		{
			currentlyFishingKarams = false;
			supressedByNonFishingClick = false;
			return;
		}

		if (inventorySlotsLeft == 0) {
			currentlyFishingKarams = false;
			supressedByNonFishingClick = false;
			return;
		}

		boolean fishingNow = isFishing(player);

		if (!fishingNow)
		{
			supressedByNonFishingClick = false;
		}

		currentlyFishingKarams = fishingNow;
	}

	/*private void processMenuEntries() {
		Menu root = client.getMenu();
		MenuEntry[] entries = root.getMenuEntries();
		int teleportIndex = -1;
		int topIndex = entries.length - 1;

		for (int i = 0; i < topIndex; i++) {
			String target = Text.removeTags(entries[i].getTarget());
			if (!target.toLowerCase().contains("fairy ring"))
			{
				continue;
			}

			if (Text.removeTags(entries[i].getOption()).equals("Zanaris"))
			{
				teleportIndex = i;
				break;
			}

			if (Text.removeTags(entries[i].getOption()).contains("DKP"))
			{
				teleportIndex = i;
				break;
			}
		}

		if (teleportIndex == -1) {
			return;
		}

		MenuEntry entry1 = entries[teleportIndex];
		MenuEntry entry2 = entries[topIndex];

		entries[teleportIndex] = entry2;
		entries[topIndex] = entry1;

		client.setMenuEntries(entries);
	}

	private void updateEstimatedFishingTicks() {
		int fishingLevel = client.getBoostedSkillLevel(Skill.FISHING);
		if (fishingLevel < 65 || fishingLevel > 99) {
			fishingLevel = 65;
		}
		int chance = (int) Math.floor(
				100.0 * (99 - fishingLevel) / 98.0
						+ 250.0 * (fishingLevel - 1) / 98.0
						+ 0.5
		) + 1;

		double catchChance = chance / 256.0;

		estimatedFishingTicks = (int) (inventorySlotsLeft * (1.0 / catchChance) * 4.0);
	}*/

	private boolean isFishing(Player player) {
		Actor actor = client.getLocalPlayer().getInteracting();
		if (actor instanceof NPC) {
			return ((NPC) actor).getId() == KARAM_FISHING_SPOT_NPC_ID;
		}
		return false;
	}

	private void scanForKaramFishingNpc()
	{
		for (NPC npc : client.getNpcs())
		{
			if (npc.getId() == KARAM_FISHING_SPOT_NPC_ID)
			{
				karamNpc = npc;
				return;
			}
		}
	}

	boolean shouldRenderOverlay()
	{
		if (!config.timerEnabled())
		{
			return false;
		}

		if (supressedByNonFishingClick)
		{
			return false;
		}

		if (inventorySlotsLeft == 0)
		{
			return false;
		}

		if (!currentlyFishingKarams)
		{
			return false;
		}

		return !isOverlaySuppressedByMouse();
	}

	int getInventorySlotsLeft() {
		return inventorySlotsLeft;
	}

	KarambwanBarrel getBarrel() {
		return barrel;
	}

	int getEstimatedFishingTicks() {
		return estimatedFishingTicks;
	}

	private void handleLeftClick()
	{
		if (!config.hideOnNonFishingClick())
		{
			return;
		}

		if (!config.timerEnabled())
		{
			return;
		}

		MenuEntry[] entries = client.getMenuEntries();

		if (menuContainsFishing(entries))
		{
			supressedByNonFishingClick = false;
			return;
		}

		if (currentlyFishingKarams)
		{
			supressedByNonFishingClick = true;
		}
	}

	private boolean menuContainsFishing(MenuEntry[] entries)
	{
		if (entries == null)
		{
			return false;
		}

		for (MenuEntry entry : entries)
		{
			if (isFishingMenuEntry(entry))
			{
				return true;
			}
		}

		return false;
	}

	private boolean isFishingMenuEntry(MenuEntry entry)
	{
		if (entry == null)
		{
			return false;
		}

		return targetLooksLikeFishingSpot(entry.getTarget());
	}

	private boolean targetLooksLikeFishingSpot(String target)
	{
		if (target == null)
		{
			return false;
		}

		return target.toLowerCase().contains("fishing spot");
	}

	private boolean isOverlaySuppressedByMouse()
	{
		if (config.disableOnHover() && isMouseOverCanvas())
		{
			return true;
		}

		if (config.hideOnMouseMovement())
		{
			int requiredClientIdleTicks = config.mouseIdleGameTicks() * CLIENT_TICKS_PER_GAME_TICK;
			return client.getMouseIdleTicks() < requiredClientIdleTicks;
		}

		return false;
	}

	private boolean isMouseOverCanvas()
	{
		Point mousePosition = client.getMouseCanvasPosition();

		if (mousePosition == null)
		{
			return false;
		}

		int x = mousePosition.getX();
		int y = mousePosition.getY();

		return x >= 0
				&& y >= 0
				&& x < client.getCanvasWidth()
				&& y < client.getCanvasHeight();
	}

	private void resetFishing()
	{
		currentlyFishingKarams = false;
		supressedByNonFishingClick = false;
		estimatedFishingTicks = -1;
		karamNpc = null;
	}

}
