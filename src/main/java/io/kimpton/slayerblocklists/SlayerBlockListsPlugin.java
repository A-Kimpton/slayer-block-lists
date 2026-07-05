package io.kimpton.slayerblocklists;

import com.google.inject.Provides;
import java.util.Arrays;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Slayer Block Lists",
	description = "Shows your slayer block list for every master: blocked creatures as wiki links, plus empty and locked slots with their unlock requirements.",
	tags = {"slayer", "block", "blocklist", "task", "master", "konar", "duradel", "nieve", "krystilia"}
)
public class SlayerBlockListsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private SlayerBlockListsConfig config;

	@Inject
	private SlayerData slayerData;

	private SlayerBlockListsPanel panel;
	private NavigationButton navButton;

	// Change detection: the raw var values behind everything the panel shows.
	// Rebuilding only when one changes keeps the per-tick cost to varbit reads.
	private int[] lastFingerprint;

	@Provides
	SlayerBlockListsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SlayerBlockListsConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = new SlayerBlockListsPanel();
		navButton = NavigationButton.builder()
			.tooltip("Slayer Block Lists")
			.icon(ImageUtil.loadImageResource(getClass(), "panel_icon.png"))
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		lastFingerprint = null;
	}

	@Override
	protected void shutDown()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
			panel = null;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (SlayerBlockListsConfig.GROUP.equals(event.getGroup()))
		{
			lastFingerprint = null; // re-render with the new settings next tick
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() != GameState.LOGGED_IN || panel == null)
		{
			return;
		}
		int[] fp = fingerprint();
		if (Arrays.equals(fp, lastFingerprint))
		{
			return;
		}
		// Collect on the client thread (varbits + cache DB lookups), render on the EDT.
		SlayerData.Model model = slayerData.collect(config);
		if (model.namesPending)
		{
			// A creature name didn't resolve yet (cache tables still loading) —
			// leave the fingerprint stale so next tick retries the lookup.
			lastFingerprint = null;
		}
		else
		{
			lastFingerprint = fp;
		}
		final SlayerBlockListsPanel p = panel;
		SwingUtilities.invokeLater(() -> p.update(model));
	}

	/** Every var the panel renders, in one comparable array. */
	private int[] fingerprint()
	{
		int[] fp = new int[SlayerData.BLOCK_VARBITS_TOTAL + 8];
		int i = SlayerData.fillBlockVarbits(client, fp);
		fp[i++] = client.getVarpValue(VarPlayerID.QP);
		fp[i++] = client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE);
		fp[i++] = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
		fp[i++] = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		fp[i++] = client.getVarpValue(VarPlayerID.SLAYER_AREA);
		fp[i++] = client.getVarbitValue(VarbitID.SLAYER_MASTER);
		fp[i++] = client.getVarbitValue(VarbitID.SLAYER_POINTS);
		fp[i] = client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED);
		return fp;
	}
}
