package io.kimpton.slayerblocklists;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SlayerBlockListsTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SlayerBlockListsPlugin.class);
		RuneLite.main(args);
	}
}
