package io.kimpton.slayerblocklists;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/** One collapsible master card: a clickable header (name, blocked/unlocked
 *  count, chevron) over a body of slot rows. The card is created once and
 *  updated in place, so its expand state and the panel's scroll survive the
 *  per-tick refreshes. The player's assigning master is accented and shown as
 *  "on task". */
class MasterSection extends JPanel
{
	private static final String WIKI_SEARCH = "https://oldschool.runescape.wiki/w/Special:Search?search=";
	private static final Color BLOCKED_DOT = new Color(220, 90, 90);

	private final String master;
	private final JPanel header = new JPanel(new BorderLayout());
	private final JLabel chevron = new JLabel("▸"); // right-pointing triangle
	private final JLabel nameLabel = new JLabel();
	private final JLabel countLabel = new JLabel();
	private final JPanel body = new JPanel();

	private boolean expanded;
	private boolean everPopulated;

	MasterSection(String master)
	{
		this.master = master;
		setLayout(new BorderLayout());
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0)); // gap between cards

		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		header.setCursor(new Cursor(Cursor.HAND_CURSOR));

		chevron.setFont(FontManager.getRunescapeSmallFont());
		chevron.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		chevron.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));

		nameLabel.setFont(FontManager.getRunescapeBoldFont());
		nameLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JPanel left = new JPanel(new BorderLayout());
		left.setOpaque(false);
		left.add(chevron, BorderLayout.WEST);
		left.add(nameLabel, BorderLayout.CENTER);

		countLabel.setFont(FontManager.getRunescapeSmallFont());
		countLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		countLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		header.add(left, BorderLayout.CENTER);
		header.add(countLabel, BorderLayout.EAST);

		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		body.setBorder(BorderFactory.createEmptyBorder(4, 10, 6, 8));
		body.setVisible(false);

		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				setExpanded(!expanded);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				header.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});

		add(header, BorderLayout.NORTH);
		add(body, BorderLayout.CENTER);
	}

	/** Repopulates the card from the model. Called on the EDT for each master. */
	void update(SlayerData.Model m, List<SlayerData.Model.Slot> slots)
	{
		boolean active = master.equals(m.activeMaster);

		int blocked = 0;
		int unlocked = 0;
		for (SlayerData.Model.Slot s : slots)
		{
			if (s.blocked)
			{
				blocked++;
			}
			if (isUnlocked(s, m))
			{
				unlocked++;
			}
		}

		nameLabel.setText(active ? master + "  • on task" : master);
		nameLabel.setForeground(active ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
		// Full = all available slots used (green), else muted.
		countLabel.setText(blocked + "/" + unlocked);
		countLabel.setForeground(unlocked > 0 && blocked >= unlocked
			? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.MEDIUM_GRAY_COLOR);

		body.removeAll();
		for (SlayerData.Model.Slot slot : slots)
		{
			addSlotRow(m, slot);
		}

		// Auto-expand the active master the first time we have data; afterwards
		// respect whatever the user toggled.
		if (!everPopulated)
		{
			everPopulated = true;
			setExpanded(active);
		}

		body.revalidate();
		body.repaint();
	}

	private boolean isUnlocked(SlayerData.Model.Slot s, SlayerData.Model m)
	{
		if (s.blocked)
		{
			return true;
		}
		if (s.slot == 0)
		{
			return m.diaryDone;
		}
		return m.questPoints >= s.slot * 50;
	}

	private void addSlotRow(SlayerData.Model m, SlayerData.Model.Slot slot)
	{
		if (slot.blocked)
		{
			String name = slot.name != null ? slot.name : "Task #" + slot.taskId;
			body.add(creatureRow(name));
			return;
		}
		if (slot.slot == 0)
		{
			if (!m.diaryDone)
			{
				if (m.showLockedSlots)
				{
					body.add(note("🔒 Diary slot - Elite Lumbridge & Draynor"));
				}
			}
			else if (m.showEmptySlots)
			{
				body.add(note("Diary slot - empty"));
			}
			return;
		}
		int need = slot.slot * 50;
		if (m.questPoints < need)
		{
			if (m.showLockedSlots)
			{
				body.add(note("🔒 Slot " + slot.slot + " - " + need + " QP"));
			}
		}
		else if (m.showEmptySlots)
		{
			body.add(note("Slot " + slot.slot + " - empty"));
		}
	}

	/** A blocked creature: a colored bullet + a wiki-linked button. */
	private JPanel creatureRow(String name)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel dot = new JLabel("•");
		dot.setForeground(BLOCKED_DOT);
		dot.setFont(FontManager.getRunescapeFont());
		dot.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
		dot.setVerticalAlignment(SwingConstants.TOP);

		JButton link = new JButton("<html>" + name + "</html>");
		link.setFont(FontManager.getRunescapeFont());
		link.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		link.setToolTipText("Open " + name + " on the OSRS Wiki");
		link.setCursor(new Cursor(Cursor.HAND_CURSOR));
		link.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
		link.setContentAreaFilled(false);
		link.setFocusPainted(false);
		link.setHorizontalAlignment(SwingConstants.LEFT);
		link.addActionListener(e -> LinkBrowser.browse(wikiSearchUrl(name)));

		row.add(dot, BorderLayout.WEST);
		row.add(link, BorderLayout.CENTER);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private JLabel note(String text)
	{
		JLabel l = new JLabel("<html>" + text + "</html>");
		l.setFont(FontManager.getRunescapeFont());
		l.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		l.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
		return l;
	}

	private void setExpanded(boolean expand)
	{
		expanded = expand;
		chevron.setText(expand ? "▾" : "▸"); // down / right triangle
		body.setVisible(expand);
		revalidate();
		repaint();
	}

	private static String wikiSearchUrl(String name)
	{
		try
		{
			return WIKI_SEARCH + URLEncoder.encode(name, "UTF-8");
		}
		catch (UnsupportedEncodingException e)
		{
			return WIKI_SEARCH + name.replace(' ', '+');
		}
	}
}
