package io.kimpton.slayerblocklists;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Bundled slayer task id → name mapping (dumped from the game cache's
 *  slayer_task DB table). Fallback for when the live DB lookup yields nothing
 *  (the cache tables load lazily). */
final class SlayerTaskNames
{
	private static final Properties NAMES = load();

	private SlayerTaskNames()
	{
	}

	static String name(int taskId)
	{
		return taskId > 0 ? NAMES.getProperty(String.valueOf(taskId)) : null;
	}

	private static Properties load()
	{
		Properties p = new Properties();
		try (InputStream in = SlayerTaskNames.class.getResourceAsStream(
			"/io/kimpton/slayerblocklists/slayer-task-names.properties"))
		{
			if (in != null)
			{
				p.load(in);
			}
		}
		catch (IOException e)
		{
			// Names then resolve to null; consumers fall back to task ids.
		}
		return p;
	}
}
