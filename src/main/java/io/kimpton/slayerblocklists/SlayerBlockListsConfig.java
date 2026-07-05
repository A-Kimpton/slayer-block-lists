package io.kimpton.slayerblocklists;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(SlayerBlockListsConfig.GROUP)
public interface SlayerBlockListsConfig extends Config
{
	String GROUP = "slayerblocklists";

	@ConfigItem(
		keyName = "showCurrentTask",
		name = "Current task",
		description = "<p width='250'>Show your current slayer task at the top of the panel.</p>",
		position = 0
	)
	default boolean showCurrentTask()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showLockedSlots",
		name = "Locked slots",
		description = "<p width='250'>Show locked block slots with what unlocks them (quest points per slot; Elite Lumbridge & Draynor diary for the seventh).</p>",
		position = 1
	)
	default boolean showLockedSlots()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showEmptySlots",
		name = "Empty slots",
		description = "<p width='250'>Show unlocked block slots that have nothing blocked yet.</p>",
		position = 2
	)
	default boolean showEmptySlots()
	{
		return true;
	}
}
