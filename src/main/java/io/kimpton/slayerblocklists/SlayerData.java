package io.kimpton.slayerblocklists;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/** Reads the slayer block lists, current task and gating state from the client
 *  (client thread only) into an immutable model the panel renders on the EDT.
 *  Creature names resolve via the SlayerTask DB table with a bundled
 *  game-cache mapping as fallback. */
@Slf4j
@Singleton
class SlayerData
{
	/** One master's block varbits: six purchasable slots then the diary slot. */
	private static final int[][] MASTER_VARBITS = {
		{VarbitID.SLAYER_BLOCKED_TURAEL_1, VarbitID.SLAYER_BLOCKED_TURAEL_2, VarbitID.SLAYER_BLOCKED_TURAEL_3,
			VarbitID.SLAYER_BLOCKED_TURAEL_4, VarbitID.SLAYER_BLOCKED_TURAEL_5, VarbitID.SLAYER_BLOCKED_TURAEL_6,
			VarbitID.SLAYER_BLOCKED_TURAEL_DIARY},
		{VarbitID.SLAYER_BLOCKED_MAZCHNA_1, VarbitID.SLAYER_BLOCKED_MAZCHNA_2, VarbitID.SLAYER_BLOCKED_MAZCHNA_3,
			VarbitID.SLAYER_BLOCKED_MAZCHNA_4, VarbitID.SLAYER_BLOCKED_MAZCHNA_5, VarbitID.SLAYER_BLOCKED_MAZCHNA_6,
			VarbitID.SLAYER_BLOCKED_MAZCHNA_DIARY},
		{VarbitID.SLAYER_BLOCKED_VANNAKA_1, VarbitID.SLAYER_BLOCKED_VANNAKA_2, VarbitID.SLAYER_BLOCKED_VANNAKA_3,
			VarbitID.SLAYER_BLOCKED_VANNAKA_4, VarbitID.SLAYER_BLOCKED_VANNAKA_5, VarbitID.SLAYER_BLOCKED_VANNAKA_6,
			VarbitID.SLAYER_BLOCKED_VANNAKA_DIARY},
		{VarbitID.SLAYER_BLOCKED_CHAELDAR_1, VarbitID.SLAYER_BLOCKED_CHAELDAR_2, VarbitID.SLAYER_BLOCKED_CHAELDAR_3,
			VarbitID.SLAYER_BLOCKED_CHAELDAR_4, VarbitID.SLAYER_BLOCKED_CHAELDAR_5, VarbitID.SLAYER_BLOCKED_CHAELDAR_6,
			VarbitID.SLAYER_BLOCKED_CHAELDAR_DIARY},
		{VarbitID.SLAYER_BLOCKED_KONAR_1, VarbitID.SLAYER_BLOCKED_KONAR_2, VarbitID.SLAYER_BLOCKED_KONAR_3,
			VarbitID.SLAYER_BLOCKED_KONAR_4, VarbitID.SLAYER_BLOCKED_KONAR_5, VarbitID.SLAYER_BLOCKED_KONAR_6,
			VarbitID.SLAYER_BLOCKED_KONAR_DIARY},
		{VarbitID.SLAYER_BLOCKED_NIEVE_1, VarbitID.SLAYER_BLOCKED_NIEVE_2, VarbitID.SLAYER_BLOCKED_NIEVE_3,
			VarbitID.SLAYER_BLOCKED_NIEVE_4, VarbitID.SLAYER_BLOCKED_NIEVE_5, VarbitID.SLAYER_BLOCKED_NIEVE_6,
			VarbitID.SLAYER_BLOCKED_NIEVE_DIARY},
		{VarbitID.SLAYER_BLOCKED_DURADEL_1, VarbitID.SLAYER_BLOCKED_DURADEL_2, VarbitID.SLAYER_BLOCKED_DURADEL_3,
			VarbitID.SLAYER_BLOCKED_DURADEL_4, VarbitID.SLAYER_BLOCKED_DURADEL_5, VarbitID.SLAYER_BLOCKED_DURADEL_6,
			VarbitID.SLAYER_BLOCKED_DURADEL_DIARY},
		{VarbitID.SLAYER_BLOCKED_KRYSTILIA_1, VarbitID.SLAYER_BLOCKED_KRYSTILIA_2, VarbitID.SLAYER_BLOCKED_KRYSTILIA_3,
			VarbitID.SLAYER_BLOCKED_KRYSTILIA_4, VarbitID.SLAYER_BLOCKED_KRYSTILIA_5, VarbitID.SLAYER_BLOCKED_KRYSTILIA_6,
			VarbitID.SLAYER_BLOCKED_KRYSTILIA_DIARY},
	};

	static final String[] MASTER_NAMES = {
		"Turael", "Mazchna", "Vannaka", "Chaeldar", "Konar", "Nieve", "Duradel", "Krystilia",
	};

	static final int BLOCK_VARBITS_TOTAL = 8 * 7;

	// SLAYER_MASTER varbit value -> assigning master. 0 = no master assigned.
	// Ordered by when each master was added to the game, NOT by combat level: the
	// four originals then Duradel(5), then Nieve(6)/Krystilia(7)/Konar(8).
	private static final String[] ASSIGNING_MASTER = {
		null, "Turael", "Mazchna", "Vannaka", "Chaeldar", "Duradel", "Nieve", "Krystilia", "Konar quo Maten",
	};

	// SLAYER_MASTER varbit value -> block-list display key (matches MASTER_NAMES).
	private static final String[] MASTER_KEY_BY_ID = {
		null, "Turael", "Mazchna", "Vannaka", "Chaeldar", "Duradel", "Nieve", "Krystilia", "Konar",
	};

	@Inject
	private Client client;

	// DB-table lookups are stable per session; failed (null) lookups are NOT
	// cached so they retry once the cache tables are available.
	private final Map<Integer, String> taskNameCache = new HashMap<>();
	private final Map<Integer, String> areaNameCache = new HashMap<>();

	/** Immutable render model. */
	static class Model
	{
		int points;
		int streak;
		int questPoints;
		boolean diaryDone;
		boolean showCurrentTask;
		boolean showLockedSlots;
		boolean showEmptySlots;
		Task task; // null = no active task
		String activeMaster; // display key of the master who gave the current task (null = none)
		// master display name -> 7 slots (slot 1..6 then the diary slot, slot=0)
		final Map<String, List<Slot>> masters = new LinkedHashMap<>();
		boolean namesPending;

		static class Task
		{
			String name;
			int remaining;
			String master;
			String area;
		}

		static class Slot
		{
			int slot; // 1..6, 0 = diary slot
			boolean blocked;
			int taskId;
			String name; // null when unresolvable
		}
	}

	/** Fills the block varbit values into fp starting at 0; returns the next index. */
	static int fillBlockVarbits(Client client, int[] fp)
	{
		int i = 0;
		for (int[] master : MASTER_VARBITS)
		{
			for (int vb : master)
			{
				fp[i++] = client.getVarbitValue(vb);
			}
		}
		return i;
	}

	Model collect(SlayerBlockListsConfig config)
	{
		Model m = new Model();
		m.points = client.getVarbitValue(VarbitID.SLAYER_POINTS);
		m.streak = client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED);
		m.questPoints = client.getVarpValue(VarPlayerID.QP);
		m.diaryDone = client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) > 0;
		m.showCurrentTask = config.showCurrentTask();
		m.showLockedSlots = config.showLockedSlots();
		m.showEmptySlots = config.showEmptySlots();

		int taskId = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
		int remaining = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		if (taskId > 0 && remaining > 0)
		{
			Model.Task t = new Model.Task();
			t.name = taskName(taskId);
			t.remaining = remaining;
			int masterId = client.getVarbitValue(VarbitID.SLAYER_MASTER);
			t.master = masterId > 0 && masterId < ASSIGNING_MASTER.length ? ASSIGNING_MASTER[masterId] : null;
			m.activeMaster = masterId > 0 && masterId < MASTER_KEY_BY_ID.length ? MASTER_KEY_BY_ID[masterId] : null;
			t.area = areaName(client.getVarpValue(VarPlayerID.SLAYER_AREA));
			m.task = t;
			if (t.name == null)
			{
				m.namesPending = true;
			}
		}

		for (int mi = 0; mi < MASTER_VARBITS.length; mi++)
		{
			List<Model.Slot> slots = new ArrayList<>(7);
			for (int si = 0; si < MASTER_VARBITS[mi].length; si++)
			{
				Model.Slot slot = new Model.Slot();
				slot.slot = si < 6 ? si + 1 : 0;
				slot.taskId = client.getVarbitValue(MASTER_VARBITS[mi][si]);
				slot.blocked = slot.taskId > 0;
				if (slot.blocked)
				{
					slot.name = taskName(slot.taskId);
					if (slot.name == null)
					{
						m.namesPending = true;
					}
				}
				slots.add(slot);
			}
			m.masters.put(MASTER_NAMES[mi], slots);
		}
		return m;
	}

	/** Resolves a slayer_task DB id to its creature name: session cache, then the
	 *  live DB table, then the bundled game-cache mapping. Null if unknown. */
	private String taskName(int taskId)
	{
		if (taskId <= 0)
		{
			return null;
		}
		String name = taskNameCache.get(taskId);
		if (name != null)
		{
			return name;
		}
		name = lookupTaskName(taskId);
		if (name == null)
		{
			name = SlayerTaskNames.name(taskId);
		}
		if (name != null)
		{
			taskNameCache.put(taskId, name);
		}
		return name;
	}

	private String lookupTaskName(int taskId)
	{
		try
		{
			List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, taskId);
			if (rows == null || rows.isEmpty())
			{
				return null;
			}
			Object[] nameField = client.getDBTableField(rows.get(0), DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0);
			if (nameField == null || nameField.length == 0 || nameField[0] == null)
			{
				return null;
			}
			return String.valueOf(nameField[0]);
		}
		catch (Exception e)
		{
			log.debug("Could not resolve slayer task name for id {}", taskId, e);
			return null;
		}
	}

	/** Konar's location-locked task area (the name the slayer helper shows). */
	private String areaName(int areaId)
	{
		if (areaId <= 0)
		{
			return null;
		}
		String name = areaNameCache.get(areaId);
		if (name != null)
		{
			return name;
		}
		try
		{
			List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, 0, areaId);
			if (rows == null || rows.isEmpty())
			{
				return null;
			}
			Object[] nameField = client.getDBTableField(rows.get(0), DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, 0);
			if (nameField == null || nameField.length == 0 || nameField[0] == null)
			{
				return null;
			}
			name = String.valueOf(nameField[0]);
			areaNameCache.put(areaId, name);
			return name;
		}
		catch (Exception e)
		{
			log.debug("Could not resolve slayer area name for id {}", areaId, e);
			return null;
		}
	}
}
