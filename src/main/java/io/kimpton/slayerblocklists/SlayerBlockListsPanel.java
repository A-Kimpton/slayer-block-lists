package io.kimpton.slayerblocklists;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

/** The Slayer Block Lists side panel: slayer points/streak, the current task,
 *  and every master's block list: blocked creatures as wiki-linked buttons,
 *  with empty and locked slots (and what unlocks them). Text wraps; the panel
 *  only ever scrolls vertically. */
class SlayerBlockListsPanel extends PluginPanel
{
	private static final String COFFEE_URL = "https://buymeacoffee.com/kimpton";
	private static final String WIKI_SEARCH = "https://oldschool.runescape.wiki/w/Special:Search?search=";

	private final JLabel pointsLabel = label("-", ColorScheme.LIGHT_GRAY_COLOR);
	private final JPanel taskSection = column();
	private final JPanel blocksSection = column();
	private final JScrollPane scroll;

	SlayerBlockListsPanel()
	{
		super(false);
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel content = column();
		content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Header: title left, coffee-mug support button pinned top right.
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel title = new JLabel("Slayer Block Lists");
		title.setForeground(ColorScheme.BRAND_ORANGE);
		header.add(title, BorderLayout.WEST);
		JButton coffee = new JButton(new ImageIcon(ImageUtil.loadImageResource(SlayerBlockListsPanel.class, "coffee.png")));
		coffee.setToolTipText("Enjoying the plugin? Buy me a coffee");
		coffee.setCursor(new Cursor(Cursor.HAND_CURSOR));
		coffee.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		coffee.setContentAreaFilled(false);
		coffee.setFocusPainted(false);
		coffee.addActionListener(e -> LinkBrowser.browse(COFFEE_URL));
		header.add(coffee, BorderLayout.EAST);
		content.add(header);
		content.add(Box.createVerticalStrut(8));

		content.add(pointsLabel);
		content.add(Box.createVerticalStrut(8));

		taskSection.add(label("<html>Log in to load your block lists.</html>", ColorScheme.MEDIUM_GRAY_COLOR));
		content.add(taskSection);
		content.add(blocksSection);

		// Vertical-only scrolling: the wrapper tracks the viewport width so long
		// text wraps instead of pushing content (and the coffee button) off-screen.
		ScrollableColumn north = new ScrollableColumn();
		north.add(content, BorderLayout.NORTH);
		scroll = new JScrollPane(north,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);
	}

	/** Called on the EDT whenever the underlying vars changed (the plugin
	 *  fingerprints them, so routine ticks never rebuild or blink). */
	void update(SlayerData.Model m)
	{
		pointsLabel.setText(String.format("Points: %,d   |   Streak: %,d", m.points, m.streak));

		taskSection.removeAll();
		if (m.showCurrentTask)
		{
			taskSection.add(sectionTitle("Current task"));
			if (m.task != null)
			{
				String name = m.task.name != null ? m.task.name : "Task";
				String line = m.task.remaining + "x " + name
					+ (m.task.master != null ? "  (" + m.task.master + ")" : "");
				// html body + uncapped height so a long task line wraps instead
				// of clipping (plain buttons render one line only).
				JButton t = linkButton("<html>" + line + "</html>", wikiSearchUrl(name));
				t.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 2));
				t.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
				taskSection.add(t);
				if (m.task.area != null)
				{
					taskSection.add(slotNote("must be killed in: " + m.task.area));
				}
			}
			else
			{
				taskSection.add(label("<html>No task assigned.</html>", ColorScheme.MEDIUM_GRAY_COLOR));
			}
			taskSection.add(Box.createVerticalStrut(8));
		}

		blocksSection.removeAll();
		blocksSection.add(sectionTitle("Block lists"));
		for (Map.Entry<String, List<SlayerData.Model.Slot>> master : m.masters.entrySet())
		{
			JButton mb = linkButton(master.getKey(), wikiSearchUrl(master.getKey()));
			mb.setForeground(ColorScheme.BRAND_ORANGE);
			mb.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 2));
			blocksSection.add(mb);
			for (SlayerData.Model.Slot slot : master.getValue())
			{
				addSlotRow(m, slot);
			}
			blocksSection.add(Box.createVerticalStrut(6));
		}

		revalidate();
		repaint();
	}

	private void addSlotRow(SlayerData.Model m, SlayerData.Model.Slot slot)
	{
		if (slot.blocked)
		{
			String name = slot.name != null ? slot.name : "Task #" + slot.taskId;
			blocksSection.add(creatureButton(name));
			return;
		}
		if (slot.slot == 0) // diary slot
		{
			if (!m.diaryDone)
			{
				if (m.showLockedSlots)
				{
					blocksSection.add(slotNote("🔒 Diary slot - Elite Lumbridge & Draynor"));
				}
			}
			else if (m.showEmptySlots)
			{
				blocksSection.add(slotNote("Diary slot - empty"));
			}
			return;
		}
		int need = slot.slot * 50;
		if (m.questPoints < need)
		{
			if (m.showLockedSlots)
			{
				blocksSection.add(slotNote("🔒 Slot " + slot.slot + " - " + need + " quest points"));
			}
		}
		else if (m.showEmptySlots)
		{
			blocksSection.add(slotNote("Slot " + slot.slot + " - empty"));
		}
	}

	/** A blocked creature as its own row: a wiki-linked button. */
	private JButton creatureButton(String name)
	{
		return linkButton("• " + name, wikiSearchUrl(name));
	}

	private JButton linkButton(String text, String url)
	{
		JButton b = new JButton(text);
		b.setFont(FontManager.getRunescapeFont());
		b.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		b.setToolTipText("Open on the OSRS Wiki");
		b.setCursor(new Cursor(Cursor.HAND_CURSOR));
		b.setBorder(BorderFactory.createEmptyBorder(1, 8, 1, 2));
		b.setContentAreaFilled(false);
		b.setFocusPainted(false);
		b.setHorizontalAlignment(SwingConstants.LEFT);
		b.setAlignmentX(Component.LEFT_ALIGNMENT);
		b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height));
		b.addActionListener(e -> LinkBrowser.browse(url));
		return b;
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

	/** An indented note line. Rendered as html so long text (Konar's location,
	 *  the diary-slot requirement) wraps instead of clipping at the panel edge. */
	private JLabel slotNote(String text)
	{
		JLabel l = label("<html>" + text + "</html>", ColorScheme.MEDIUM_GRAY_COLOR);
		l.setBorder(BorderFactory.createEmptyBorder(1, 8, 1, 0));
		return l;
	}

	private static JPanel column()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setOpaque(false);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		return p;
	}

	private static JLabel label(String text, java.awt.Color color)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeFont());
		l.setForeground(color);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private static JLabel sectionTitle(String text)
	{
		JLabel l = new JLabel(text.toUpperCase());
		l.setFont(FontManager.getRunescapeFont());
		l.setForeground(ColorScheme.BRAND_ORANGE);
		l.setBorder(BorderFactory.createEmptyBorder(0, 0, 3, 0));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	/** BorderLayout panel that always matches the scroll viewport's width, so
	 *  content can only grow downward (vertical scrolling, wrapped text). */
	private static class ScrollableColumn extends JPanel implements Scrollable
	{
		ScrollableColumn()
		{
			super(new BorderLayout());
			setBackground(ColorScheme.DARK_GRAY_COLOR);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 64;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}
}
