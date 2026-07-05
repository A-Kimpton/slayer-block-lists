package io.kimpton.slayerblocklists;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
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
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

/** The Slayer Block Lists side panel. A prominent current-task card and
 *  points/streak chips sit above a stack of collapsible per-master cards; the
 *  master who gave your current task is accented and auto-expanded. Persistent
 *  components are updated in place so expand state and scroll position survive
 *  each tick's refresh. Text wraps; the panel only ever scrolls vertically. */
class SlayerBlockListsPanel extends PluginPanel
{
	private static final String COFFEE_URL = "https://buymeacoffee.com/kimpton";
	private static final String WIKI_SEARCH = "https://oldschool.runescape.wiki/w/Special:Search?search=";

	private final JScrollPane scroll;

	// Waiting-for-login placeholder, swapped out once real data arrives.
	private final JLabel placeholder = new JLabel("Log in to load your slayer block lists.");
	private final JPanel data = column(); // everything below the header

	// Current-task card.
	private final JPanel taskCard = new JPanel(new BorderLayout());
	private final JButton taskLine = linkButton("");
	private final JLabel taskSub = subtle("");
	private final JLabel taskArea = subtle("");

	// Stat chips.
	private final JLabel pointsValue = statValue();
	private final JLabel streakValue = statValue();

	// One card per master, created once and updated in place.
	private final Map<String, MasterSection> sections = new LinkedHashMap<>();

	SlayerBlockListsPanel()
	{
		super(false);
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel content = column();
		content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		content.add(buildHeader());
		content.add(Box.createVerticalStrut(10));

		placeholder.setFont(FontManager.getRunescapeFont());
		placeholder.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		placeholder.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(placeholder);

		buildTaskCard();
		data.add(taskCard);
		data.add(Box.createVerticalStrut(8));
		data.add(buildStatChips());
		data.add(Box.createVerticalStrut(10));
		data.add(sectionTitle("Block lists"));
		for (String master : SlayerData.MASTER_NAMES)
		{
			MasterSection s = new MasterSection(master);
			sections.put(master, s);
			data.add(s);
		}
		data.setVisible(false);
		content.add(data);

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

	private JPanel buildHeader()
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel title = new JLabel("Slayer Block Lists");
		title.setFont(FontManager.getRunescapeBoldFont());
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
		return header;
	}

	private void buildTaskCard()
	{
		taskCard.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		taskCard.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
		taskCard.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel col = column();
		JLabel caption = new JLabel("CURRENT TASK");
		caption.setFont(FontManager.getRunescapeSmallFont());
		caption.setForeground(ColorScheme.BRAND_ORANGE);
		caption.setAlignmentX(Component.LEFT_ALIGNMENT);

		taskLine.setFont(FontManager.getRunescapeBoldFont());
		taskLine.setForeground(ColorScheme.TEXT_COLOR);
		taskLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

		col.add(caption);
		col.add(Box.createVerticalStrut(2));
		col.add(taskLine);
		col.add(taskSub);
		col.add(taskArea);
		taskCard.add(col, BorderLayout.CENTER);
	}

	private JPanel buildStatChips()
	{
		JPanel row = new JPanel(new GridLayout(1, 2, 6, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
		row.add(chip(pointsValue, "points"));
		row.add(chip(streakValue, "streak"));
		return row;
	}

	private JPanel chip(JLabel value, String caption)
	{
		JPanel chip = new JPanel(new BorderLayout());
		chip.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		chip.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		JLabel cap = new JLabel(caption, SwingConstants.CENTER);
		cap.setFont(FontManager.getRunescapeSmallFont());
		cap.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		chip.add(value, BorderLayout.CENTER);
		chip.add(cap, BorderLayout.SOUTH);
		return chip;
	}

	/** Called on the EDT whenever the underlying vars changed. Updates in place,
	 *  preserving scroll position and each card's expand state. */
	void update(SlayerData.Model m)
	{
		final int pos = scroll.getVerticalScrollBar().getValue();

		placeholder.setVisible(false);
		data.setVisible(true);

		taskCard.setVisible(m.showCurrentTask);
		if (m.showCurrentTask)
		{
			if (m.task != null)
			{
				String name = m.task.name != null ? m.task.name : "Task";
				taskLine.setText("<html>" + m.task.remaining + "x " + name + "</html>");
				taskLine.setToolTipText("Open " + name + " on the OSRS Wiki");
				String url = wikiSearchUrl(name);
				for (java.awt.event.ActionListener al : taskLine.getActionListeners())
				{
					taskLine.removeActionListener(al);
				}
				taskLine.addActionListener(e -> LinkBrowser.browse(url));
				taskLine.setEnabled(true);

				taskSub.setText(m.task.master != null ? "Assigned by " + m.task.master : "");
				taskSub.setVisible(m.task.master != null);
				taskArea.setText(m.task.area != null ? "<html>Must kill in: " + m.task.area + "</html>" : "");
				taskArea.setVisible(m.task.area != null);
			}
			else
			{
				taskLine.setText("No active task");
				taskLine.setToolTipText(null);
				taskLine.setEnabled(false);
				for (java.awt.event.ActionListener al : taskLine.getActionListeners())
				{
					taskLine.removeActionListener(al);
				}
				taskSub.setVisible(false);
				taskArea.setVisible(false);
			}
		}

		pointsValue.setText(String.format("%,d", m.points));
		streakValue.setText(String.format("%,d", m.streak));

		for (Map.Entry<String, List<SlayerData.Model.Slot>> e : m.masters.entrySet())
		{
			MasterSection s = sections.get(e.getKey());
			if (s != null)
			{
				s.update(m, e.getValue());
			}
		}

		revalidate();
		repaint();
		SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(pos));
	}

	// ---- small builders ----

	private static JButton linkButton(String text)
	{
		JButton b = new JButton(text);
		b.setFont(FontManager.getRunescapeFont());
		b.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		b.setCursor(new Cursor(Cursor.HAND_CURSOR));
		b.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
		b.setContentAreaFilled(false);
		b.setFocusPainted(false);
		b.setHorizontalAlignment(SwingConstants.LEFT);
		b.setAlignmentX(Component.LEFT_ALIGNMENT);
		return b;
	}

	private static JLabel subtle(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private static JLabel statValue()
	{
		JLabel l = new JLabel("-", SwingConstants.CENTER);
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setForeground(ColorScheme.BRAND_ORANGE);
		return l;
	}

	private static JLabel sectionTitle(String text)
	{
		JLabel l = new JLabel(text.toUpperCase());
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		l.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
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
