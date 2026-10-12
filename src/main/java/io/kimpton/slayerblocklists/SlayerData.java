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
	/** Quest points needed per purchasable slot, for every master except Mortimer. */
	static final int QP_PER_SLOT = 50;

	/** Slayer points Mortimer charges for each of his two block slots. */
	static final int MORTIMER_SLOT_POINTS = 120;

	// SLAYER_MASTER values that keep their own streak counter rather than moving
	// SLAYER_TASKS_COMPLETED. Named after RuneLite's own Slayer plugin constants.
	private static final int KRYSTILIA_MASTER_ID = 7;
	private static final int MORTIMER_MASTER_ID = 10;

	/** A master's block list: its slot varbits, whether it has the achievement
	 *  diary bonus slot, and how its purchasable slots are paid for. */
	static final class MasterDef
	{
		final String key;
		final int[] slotVarbits;
		final int diaryVarbit; // -1 when the master has no diary slot
		final int slotPointCost; // 0 = gated on quest points instead

		private MasterDef(String key, int[] slotVarbits, int diaryVarbit, int slotPointCost)
		{
			this.key = key;
			this.slotVarbits = slotVarbits;
			this.diaryVarbit = diaryVarbit;
			this.slotPointCost = slotPointCost;
		}

		/** One of the eight classic masters: six slots bought with quest points
		 *  plus the Elite Lumbridge & Draynor diary slot. */
		private static MasterDef classic(String key, int s1, int s2, int s3, int s4, int s5, int s6, int diary)
		{
			return new MasterDef(key, new int[]{s1, s2, s3, s4, s5, s6}, diary, 0);
		}

		int slotCount()
		{
			return slotVarbits.length + (diaryVarbit > 0 ? 1 : 0);
		}

		boolean hasDiarySlot()
		{
			return diaryVarbit > 0;
		}
	}

	static final MasterDef[] MASTERS = {
		MasterDef.classic("Turael",
			VarbitID.SLAYER_BLOCKED_TURAEL_1, VarbitID.SLAYER_BLOCKED_TURAEL_2, VarbitID.SLAYER_BLOCKED_TURAEL_3,
			VarbitID.SLAYER_BLOCKED_TURAEL_4, VarbitID.SLAYER_BLOCKED_TURAEL_5, VarbitID.SLAYER_BLOCKED_TURAEL_6,
			VarbitID.SLAYER_BLOCKED_TURAEL_DIARY),
		MasterDef.classic("Mazchna",
			VarbitID.SLAYER_BLOCKED_MAZCHNA_1, VarbitID.SLAYER_BLOCKED_MAZCHNA_2, VarbitID.SLAYER_BLOCKED_MAZCHNA_3,
			VarbitID.SLAYER_BLOCKED_MAZCHNA_4, VarbitID.SLAYER_BLOCKED_MAZCHNA_5, VarbitID.SLAYER_BLOCKED_MAZCHNA_6,
			VarbitID.SLAYER_BLOCKED_MAZCHNA_DIARY),
		MasterDef.classic("Vannaka",
			VarbitID.SLAYER_BLOCKED_VANNAKA_1, VarbitID.SLAYER_BLOCKED_VANNAKA_2, VarbitID.SLAYER_BLOCKED_VANNAKA_3,
			VarbitID.SLAYER_BLOCKED_VANNAKA_4, VarbitID.SLAYER_BLOCKED_VANNAKA_5, VarbitID.SLAYER_BLOCKED_VANNAKA_6,
			VarbitID.SLAYER_BLOCKED_VANNAKA_DIARY),
		MasterDef.classic("Chaeldar",
			VarbitID.SLAYER_BLOCKED_CHAELDAR_1, VarbitID.SLAYER_BLOCKED_CHAELDAR_2, VarbitID.SLAYER_BLOCKED_CHAELDAR_3,
			VarbitID.SLAYER_BLOCKED_CHAELDAR_4, VarbitID.SLAYER_BLOCKED_CHAELDAR_5, VarbitID.SLAYER_BLOCKED_CHAELDAR_6,
			VarbitID.SLAYER_BLOCKED_CHAELDAR_DIARY),
		MasterDef.classic("Konar",
			VarbitID.SLAYER_BLOCKED_KONAR_1, VarbitID.SLAYER_BLOCKED_KONAR_2, VarbitID.SLAYER_BLOCKED_KONAR_3,
			VarbitID.SLAYER_BLOCKED_KONAR_4, VarbitID.SLAYER_BLOCKED_KONAR_5, VarbitID.SLAYER_BLOCKED_KONAR_6,
			VarbitID.SLAYER_BLOCKED_KONAR_DIARY),
		MasterDef.classic("Nieve",
			VarbitID.SLAYER_BLOCKED_NIEVE_1, VarbitID.SLAYER_BLOCKED_NIEVE_2, VarbitID.SLAYER_BLOCKED_NIEVE_3,
			VarbitID.SLAYER_BLOCKED_NIEVE_4, VarbitID.SLAYER_BLOCKED_NIEVE_5, VarbitID.SLAYER_BLOCKED_NIEVE_6,
			VarbitID.SLAYER_BLOCKED_NIEVE_DIARY),
		MasterDef.classic("Duradel",
			VarbitID.SLAYER_BLOCKED_DURADEL_1, VarbitID.SLAYER_BLOCKED_DURADEL_2, VarbitID.SLAYER_BLOCKED_DURADEL_3,
			VarbitID.SLAYER_BLOCKED_DURADEL_4, VarbitID.SLAYER_BLOCKED_DURADEL_5, VarbitID.SLAYER_BLOCKED_DURADEL_6,
			VarbitID.SLAYER_BLOCKED_DURADEL_DIARY),
		MasterDef.classic("Krystilia",
			VarbitID.SLAYER_BLOCKED_KRYSTILIA_1, VarbitID.SLAYER_BLOCKED_KRYSTILIA_2, VarbitID.SLAYER_BLOCKED_KRYSTILIA_3,
			VarbitID.SLAYER_BLOCKED_KRYSTILIA_4, VarbitID.SLAYER_BLOCKED_KRYSTILIA_5, VarbitID.SLAYER_BLOCKED_KRYSTILIA_6,
			VarbitID.SLAYER_BLOCKED_KRYSTILIA_DIARY),
		// Mortimer breaks the shape: two slots bought with slayer points, no diary slot.
		new MasterDef("Mortimer",
			new int[]{VarbitID.SLAYER_BLOCKED_MORTIMER_1, VarbitID.SLAYER_BLOCKED_MORTIMER_2},
			-1, MORTIMER_SLOT_POINTS),
	};

	static final int BLOCK_VARBITS_TOTAL = countBlockVarbits();

	private static int countBlockVarbits()
	{
		int n = 0;
		for (MasterDef m : MASTERS)
		{
			n += m.slotCount();
		}
		return n;
	}

	// SLAYER_MASTER varbit value -> assigning master. 0 = no master assigned.
	// Ordered by when each master was added to the game, NOT by combat level: the
	// four originals then Duradel(5), then Nieve(6)/Krystilia(7)/Konar(8), then
	// Spria(9) — who shares Turael's block list — and Mortimer(10). RuneLite's own
	// Slayer plugin pins the two ends of that run (KRYSTILIA_SLAYER_MASTER = 7,
	// MORTIMER_SLAYER_MASTER = 10), which leaves 9 for Spria, the only master added
	// between Konar and Mortimer.
	private static final String[] ASSIGNING_MASTER = {
		null, "Turael", "Mazchna", "Vannaka", "Chaeldar", "Duradel", "Nieve", "Krystilia", "Konar quo Maten",
		"Spria", "Mortimer",
	};

	// SLAYER_MASTER varbit value -> block-list display key (matches MASTERS).
	// Spria has no block list of her own; she shares Turael's.
	private static final String[] MASTER_KEY_BY_ID = {
		null, "Turael", "Mazchna", "Vannaka", "Chaeldar", "Duradel", "Nieve", "Krystilia", "Konar",
		"Turael", "Mortimer",
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
		// master display name -> its slots, in slot order, diary slot (slot=0) last
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
			int slot; // 1..n, 0 = diary slot
			boolean blocked;
			int taskId;
			String name; // null when unresolvable
			int pointCost; // slayer points this slot costs, 0 = gated on quest points
		}
	}

	/** Fills the block varbit values into fp starting at 0; returns the next index. */
	static int fillBlockVarbits(Client client, int[] fp)
	{
		int i = 0;
		for (MasterDef master : MASTERS)
		{
			for (int vb : master.slotVarbits)
			{
				fp[i++] = client.getVarbitValue(vb);
			}
			if (master.hasDiarySlot())
			{
				fp[i++] = client.getVarbitValue(master.diaryVarbit);
			}
		}
		return i;
	}

	Model collect(SlayerBlockListsConfig config)
	{
		Model m = new Model();
		int masterId = client.getVarbitValue(VarbitID.SLAYER_MASTER);
		m.points = client.getVarbitValue(VarbitID.SLAYER_POINTS);
		m.streak = streakFor(masterId);
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
			t.master = masterId > 0 && masterId < ASSIGNING_MASTER.length ? ASSIGNING_MASTER[masterId] : null;
			m.activeMaster = masterId > 0 && masterId < MASTER_KEY_BY_ID.length ? MASTER_KEY_BY_ID[masterId] : null;
			t.area = areaName(client.getVarpValue(VarPlayerID.SLAYER_AREA));
			m.task = t;
			if (t.name == null)
			{
				m.namesPending = true;
			}
		}

		for (MasterDef def : MASTERS)
		{
			List<Model.Slot> slots = new ArrayList<>(def.slotCount());
			for (int si = 0; si < def.slotVarbits.length; si++)
			{
				slots.add(readSlot(m, si + 1, def.slotVarbits[si], def.slotPointCost));
			}
			if (def.hasDiarySlot())
			{
				slots.add(readSlot(m, 0, def.diaryVarbit, 0));
			}
			m.masters.put(def.key, slots);
		}
		return m;
	}

	/** The task streak for the master currently assigning, which is not always
	 *  SLAYER_TASKS_COMPLETED: Krystilia and Mortimer each count separately, and
	 *  their tasks leave the shared counter untouched. Mirrors RuneLite's Slayer
	 *  plugin, so the chip agrees with the in-game task infobox. */
	private int streakFor(int masterId)
	{
		switch (masterId)
		{
			case KRYSTILIA_MASTER_ID:
				return client.getVarbitValue(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED);
			case MORTIMER_MASTER_ID:
				return client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED);
			default:
				return client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED);
		}
	}

	private Model.Slot readSlot(Model m, int slotNumber, int varbit, int pointCost)
	{
		Model.Slot slot = new Model.Slot();
		slot.slot = slotNumber;
		slot.pointCost = pointCost;
		slot.taskId = client.getVarbitValue(varbit);
		slot.blocked = slot.taskId > 0;
		if (slot.blocked)
		{
			slot.name = taskName(slot.taskId);
			if (slot.name == null)
			{
				m.namesPending = true;
			}
		}
		return slot;
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
