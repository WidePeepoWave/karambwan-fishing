package com.karambwanfishing;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.ItemID;

/**
 * Tracks how full the player's fish barrel is.
 * <p>
 * The game does not expose the contents of the barrel, so the count is inferred:
 * catching a karambwan while carrying an open barrel and seeing nothing appear in the
 * inventory means the fish went into the barrel. A few chat messages ("the barrel is
 * full", "you empty all of your containers") and the 'Check' option give an exact count.
 * When the inference breaks down the count is flagged as unknown rather than guessed at.
 */
class KarambwanBarrel
{
	static final int CAPACITY = 28;

	private static final Set<Integer> BARREL_IDS = Set.of(
		ItemID.FISH_BARREL_CLOSED,
		ItemID.FISH_BARREL_OPEN,
		ItemID.FISH_SACK_BARREL_CLOSED,
		ItemID.FISH_SACK_BARREL_OPEN
	);

	// only an open barrel collects fish as they are caught
	private static final Set<Integer> OPEN_BARREL_IDS = Set.of(
		ItemID.FISH_BARREL_OPEN,
		ItemID.FISH_SACK_BARREL_OPEN
	);

	// how many game ticks a click on the barrel stays 'recent' for
	private static final int ACTION_TICK_MARGIN = 3;

	private static final Pattern CATCH_MESSAGE = Pattern.compile("^You catch a(?: raw)? [Kk]arambwan[.!]?$");
	private static final String RADA_CATCH_MESSAGE = "Rada's blessing enabled you to catch an extra fish.";
	private static final String FLAKES_CATCH_MESSAGE = "The spirit flakes enabled you to catch an extra fish.";

	private static final String BARREL_FULL_MESSAGE = "The barrel is full. It may be emptied at a bank.";
	private static final String BANK_EMPTY_MESSAGE = "You empty all of your containers into the bank.";
	private static final String CONTAINERS_EMPTY_MESSAGE = "Your containers are already empty.";
	private static final String BANK_FULL_MESSAGE = "Your bank could not hold your fish.";

	// text shown by the 'Check' option, e.g. "The barrel contains: 12 x Raw karambwan"
	private static final String CHECK_EMPTY_TEXT = "The barrel is empty.";
	private static final String CHECK_ENTRY_REGEX = "([0-9]+) x [a-zA-Z ]+,? ?";
	private static final Pattern CHECK_ENTRY_PATTERN = Pattern.compile(CHECK_ENTRY_REGEX);
	private static final Pattern CHECK_TEXT_PATTERN = Pattern.compile("^The barrel contains: (" + CHECK_ENTRY_REGEX + ")+$");

	private enum Action
	{
		FILL,
		EMPTY,
		CHECK
	}

	private final Client client;

	private boolean present;
	private boolean open;
	private int holding;
	private boolean unknown = true;

	// raw karambwan held in the inventory as of the last inventory update
	private int inventoryKarambwan;
	// counters for the current game tick
	private int catchMessages;
	private int karambwanGained;
	// game tick each barrel menu option was last clicked on
	private final Map<Action, Integer> actionTicks = new EnumMap<>(Action.class);

	@Inject
	KarambwanBarrel(Client client)
	{
		this.client = client;
	}

	boolean isPresent()
	{
		return present;
	}

	boolean isUnknown()
	{
		return unknown;
	}

	int getSlotsLeft()
	{
		return Math.max(0, CAPACITY - holding);
	}

	void reset()
	{
		present = false;
		open = false;
		holding = 0;
		unknown = true;
		inventoryKarambwan = 0;
		catchMessages = 0;
		karambwanGained = 0;
		actionTicks.clear();
	}

	void onInventoryChanged(ItemContainer inventory)
	{
		present = false;
		open = false;

		int karambwan = 0;
		for (Item item : inventory.getItems())
		{
			int itemId = item.getId();
			if (OPEN_BARREL_IDS.contains(itemId))
			{
				present = true;
				open = true;
			}
			else if (BARREL_IDS.contains(itemId))
			{
				present = true;
			}
			else if (itemId == ItemID.TBWT_RAW_KARAMBWAN)
			{
				karambwan += item.getQuantity();
			}
		}

		int previous = inventoryKarambwan;
		inventoryKarambwan = karambwan;

		if (karambwan > previous)
		{
			karambwanGained += karambwan - previous;
		}
		else if (karambwan < previous && consumeRecentAction(Action.FILL))
		{
			if (karambwan > 0)
			{
				// karambwan left over after a 'Fill' means the barrel could not take them all
				setHolding(CAPACITY);
			}
			else
			{
				holding = Math.min(CAPACITY, holding + (previous - karambwan));
			}
		}
	}

	void onChatMessage(ChatMessage event)
	{
		if (!present)
		{
			return;
		}

		if (event.getType() == ChatMessageType.GAMEMESSAGE)
		{
			switch (event.getMessage())
			{
				case BARREL_FULL_MESSAGE:
					setHolding(CAPACITY);
					break;
				case BANK_EMPTY_MESSAGE:
				case CONTAINERS_EMPTY_MESSAGE:
					setHolding(0);
					break;
				case BANK_FULL_MESSAGE:
					// not everything made it into the bank, so the count is no longer trustworthy
					unknown = true;
					break;
			}
		}
		else if (event.getType() == ChatMessageType.SPAM && open)
		{
			String message = event.getMessage();
			if (CATCH_MESSAGE.matcher(message).matches()
				|| RADA_CATCH_MESSAGE.equals(message)
				|| FLAKES_CATCH_MESSAGE.equals(message))
			{
				catchMessages++;
			}
		}
	}

	void onGameTick()
	{
		if (catchMessages > 0)
		{
			if (karambwanGained == 0)
			{
				// nothing landed in the inventory, so every fish caught went into the barrel
				holding = Math.min(CAPACITY, holding + catchMessages);
			}
			else
			{
				// fish spilled over into the inventory, so the barrel must be full
				setHolding(CAPACITY);
			}
		}
		else if (consumeRecentAction(Action.EMPTY))
		{
			setHolding(0);
		}

		catchMessages = 0;
		karambwanGained = 0;
	}

	/**
	 * Records a click on a barrel menu option, so that the resulting inventory change or
	 * interface can be attributed to it. Clicks on anything else are ignored.
	 */
	void onMenuOptionClicked(int itemId, String menuOption)
	{
		if (!BARREL_IDS.contains(itemId))
		{
			return;
		}

		Action action = forMenuOption(menuOption);
		if (action != null)
		{
			actionTicks.put(action, client.getTickCount());
		}
	}

	boolean wasCheckClicked()
	{
		return consumeRecentAction(Action.CHECK);
	}

	/**
	 * Applies the exact count reported by the 'Check' option. Text that cannot be parsed
	 * leaves the current count alone.
	 */
	void applyCheckText(String text)
	{
		if (text == null)
		{
			return;
		}
		text = text.replace("<br>", " ").trim();

		if (CHECK_EMPTY_TEXT.equals(text))
		{
			setHolding(0);
			return;
		}

		if (!CHECK_TEXT_PATTERN.matcher(text).matches())
		{
			return;
		}

		int count = 0;
		Matcher matcher = CHECK_ENTRY_PATTERN.matcher(text);
		while (matcher.find())
		{
			count += Integer.parseInt(matcher.group(1));
		}

		setHolding(Math.min(CAPACITY, count));
	}

	private void setHolding(int value)
	{
		holding = value;
		unknown = false;
	}

	private boolean consumeRecentAction(Action action)
	{
		Integer tick = actionTicks.get(action);
		if (tick != null && tick > client.getTickCount() - ACTION_TICK_MARGIN)
		{
			actionTicks.remove(action);
			return true;
		}
		return false;
	}

	private static Action forMenuOption(String menuOption)
	{
		switch (menuOption)
		{
			case "Fill":
				return Action.FILL;
			case "Empty":
			case "Empty to bank":
				return Action.EMPTY;
			case "Check":
				return Action.CHECK;
			default:
				return null;
		}
	}
}
