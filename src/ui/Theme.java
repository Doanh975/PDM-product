package ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.table.DefaultTableCellRenderer;

/** Look and feel, colors and shared UI building blocks. */
public final class Theme {

    public static final Color ACCENT = new Color(0x2563EB);
    public static final Color SUCCESS = new Color(0x16A34A);
    public static final Color DANGER = new Color(0xDC2626);
    public static final Color TEXT = new Color(0x111827);
    public static final Color MUTED = new Color(0x6B7280);
    public static final Color BORDER = new Color(0xE5E7EB);
    public static final Color CARD = Color.WHITE;
    public static final Color PAGE = new Color(0xF3F4F6);

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##");

    private Theme() {}

    /** Installs FlatLaf (macOS variant on a Mac). Falls back to the system look if the jar is missing. */
    public static void install() {
        boolean mac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        try {
            if (mac) com.formdev.flatlaf.themes.FlatMacLightLaf.setup();
            else com.formdev.flatlaf.FlatLightLaf.setup();
            UIManager.put("Component.arc", 10);
            UIManager.put("Button.arc", 10);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("Component.focusWidth", 1);
            UIManager.put("Component.accentColor", ACCENT);
            UIManager.put("Panel.background", PAGE);
            UIManager.put("Table.rowHeight", 32);
            UIManager.put("Table.showHorizontalLines", true);
            UIManager.put("Table.showVerticalLines", false);
            UIManager.put("Table.gridColor", new Color(0xF0F1F3));
            UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
            UIManager.put("Table.selectionBackground", new Color(0xDBEAFE));
            UIManager.put("Table.selectionForeground", TEXT);
            UIManager.put("Table.selectionInactiveBackground", new Color(0xE8F0FE));
            UIManager.put("Table.selectionInactiveForeground", TEXT);
            UIManager.put("TableHeader.height", 36);
            UIManager.put("TableHeader.background", new Color(0xF9FAFB));
            UIManager.put("TableHeader.bottomSeparatorColor", BORDER);
            UIManager.put("ScrollBar.showButtons", false);
            UIManager.put("ScrollBar.width", 10);
            UIManager.put("ScrollPane.borderColor", BORDER);
            UIManager.put("TabbedPane.tabHeight", 42);
            UIManager.put("TabbedPane.selectedBackground", new Color(0xE8EEFC));
            UIManager.put("TabbedPane.hoverColor", new Color(0xEEF1F6));
            UIManager.put("TabbedPane.underlineColor", ACCENT);
            UIManager.put("TabbedPane.tabSeparatorsFullHeight", false);
            UIManager.put("TabbedPane.contentAreaColor", PAGE);
            UIManager.put("SplitPane.dividerSize", 8);
            UIManager.put("SplitPaneDivider.gripDotCount", 0);
        } catch (Throwable t) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // default look and feel
            }
        }
    }

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("Ssn", "SSN"),
            Map.entry("FullName", "Name"),
            Map.entry("EmploymentType", "Contract"),
            Map.entry("DepartmentName", "Department"),
            Map.entry("PositionName", "Position"),
            Map.entry("ShiftName", "Shift"),
            Map.entry("RequestStatus", "Status"),
            Map.entry("AttendanceStatus", "Attendance"),
            Map.entry("AssignmentStatus", "Assignment"),
            Map.entry("EmploymentStatus", "Status"),
            Map.entry("ActualCheckIn", "Check-in"),
            Map.entry("ActualCheckOut", "Check-out"),
            Map.entry("SubmittedAt", "Submitted"),
            Map.entry("ReviewedAt", "Reviewed"),
            Map.entry("LeaveRequestID", "Request #"),
            Map.entry("ShiftAssignmentID", "Assignment #"),
            Map.entry("AttendanceID", "Attendance #"),
            Map.entry("EmployeeID", "ID"),
            Map.entry("WorkingDaysWaiting", "Days waiting"),
            Map.entry("RemainingDays", "Left"),
            Map.entry("AllocatedDays", "Allowed"),
            Map.entry("UsedDays", "Used"),
            Map.entry("LeaveYear", "Year"),
            Map.entry("StartDate", "From"),
            Map.entry("EndDate", "To"),
            Map.entry("IsOvernight", "Overnight"),
            Map.entry("IsPaidHoliday", "Paid holiday")
    );

    /** "WorkingDaysWaiting" -> "Working days waiting", with a few friendlier overrides. */
    public static String humanize(String column) {
        String fixed = LABELS.get(column);
        if (fixed != null) return fixed;
        String s = column.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
        if (s.isEmpty()) return s;
        String[] words = s.split(" ");
        StringBuilder sb = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) {
            String w = words[i];
            sb.append(' ').append(w.length() > 1 && w.equals(w.toUpperCase()) ? w : w.toLowerCase());
        }
        return sb.toString();
    }

    public static String text(Object value) {
        if (value == null) return "";
        if (value instanceof LocalDateTime ldt) return ldt.format(DT);
        if (value instanceof BigDecimal || value instanceof Double) return MONEY.format(value);
        if (value instanceof Boolean b) return b ? "Yes" : "No";
        return value.toString();
    }

    private static final Color[] GREEN = {new Color(0x166534), new Color(0xDCFCE7)};
    private static final Color[] AMBER = {new Color(0x92400E), new Color(0xFEF3C7)};
    private static final Color[] RED = {new Color(0x991B1B), new Color(0xFEE2E2)};
    private static final Color[] BLUE = {new Color(0x1E40AF), new Color(0xDBEAFE)};
    private static final Color[] GREY = {new Color(0x374151), new Color(0xE5E7EB)};

    private static final Map<String, Color[]> STATUS = Map.ofEntries(
            Map.entry("Approved", GREEN), Map.entry("Present", GREEN), Map.entry("Active", GREEN), Map.entry("Confirmed", GREEN),
            Map.entry("Pending", AMBER), Map.entry("Late", AMBER), Map.entry("OnLeave", AMBER), Map.entry("Scheduled", BLUE),
            Map.entry("Excused", BLUE),
            Map.entry("Rejected", RED), Map.entry("Absent", RED), Map.entry("Cancelled", GREY), Map.entry("Terminated", RED),
            Map.entry("Locked", RED), Map.entry("Disabled", GREY), Map.entry("Inactive", GREY),
            Map.entry("ADMIN", BLUE), Map.entry("MANAGER", BLUE), Map.entry("USER", GREY)
    );

    /** Table renderer: padded cells, friendly values, colored "pill" for status columns. */
    public static final class CellRenderer extends DefaultTableCellRenderer {
        private Color pill;

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(t, value, sel, false, row, col);
            setText(text(value));
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            setHorizontalAlignment(value instanceof Number ? SwingConstants.RIGHT : SwingConstants.LEFT);
            pill = null;
            String header = t.getColumnName(col).toLowerCase();
            if (value instanceof String s && (header.contains("status") || header.contains("attendance")
                    || header.contains("assignment") || header.equals("role"))) {
                Color[] c = STATUS.get(s);
                if (c != null) {
                    pill = c[1];
                    setForeground(c[0]);
                    setFont(getFont().deriveFont(Font.BOLD, getFont().getSize2D() - 1f));
                    setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 12));
                    if (s.equals("OnLeave")) setText("On leave");
                }
            }
            if (!sel && pill == null) setForeground(TEXT);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (pill != null) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                FontMetrics fm = g2.getFontMetrics(getFont());
                int w = fm.stringWidth(getText()) + 18;
                int h = fm.getHeight() + 4;
                g2.setColor(pill);
                g2.fillRoundRect(11, (getHeight() - h) / 2, w, h, h, h);
                g2.dispose();
                setOpaque(false);
                super.paintComponent(g);
                setOpaque(true);
            } else {
                super.paintComponent(g);
            }
        }
    }

    public static JButton primary(String text) {
        JButton b = new JButton(text);
        b.putClientProperty("FlatLaf.style", "background: #2563EB; foreground: #FFFFFF; borderWidth: 0; focusWidth: 0;"
                + " hoverBackground: #1D4ED8; pressedBackground: #1E40AF; font: bold");
        return b;
    }

    public static JButton success(String text) {
        JButton b = new JButton(text);
        b.putClientProperty("FlatLaf.style", "background: #16A34A; foreground: #FFFFFF; borderWidth: 0; focusWidth: 0;"
                + " hoverBackground: #15803D; pressedBackground: #166534; font: bold");
        return b;
    }

    public static JButton danger(String text) {
        JButton b = new JButton(text);
        b.putClientProperty("FlatLaf.style", "foreground: #DC2626; borderColor: #FCA5A5; hoverBorderColor: #DC2626");
        return b;
    }

    public static JButton subtle(String text) {
        JButton b = new JButton(text);
        b.putClientProperty("JButton.buttonType", "borderless");
        b.setForeground(ACCENT);
        return b;
    }

    /** White rounded card with padding. */
    public static JPanel card(JComponent content) {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createCompoundBorder(new RoundBorder(BORDER, 12), BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        p.add(content, BorderLayout.CENTER);
        return p;
    }

    /** Card with a bold title line. */
    public static JPanel card(String title, JComponent content) {
        JPanel inner = new JPanel(new BorderLayout(0, 8));
        inner.setOpaque(false);
        JLabel l = new JLabel(title);
        l.setFont(l.getFont().deriveFont(Font.BOLD, l.getFont().getSize2D() + 1f));
        l.setForeground(TEXT);
        inner.add(l, BorderLayout.NORTH);
        inner.add(content, BorderLayout.CENTER);
        return card(inner);
    }

    /** Scroll pane for a table placed inside a card. */
    public static JScrollPane tableScroll(JTable t) {
        JScrollPane sp = new JScrollPane(t);
        sp.setBorder(BorderFactory.createLineBorder(BORDER));
        sp.getViewport().setBackground(CARD);
        return sp;
    }

    public static JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(l.getFont().deriveFont(Font.BOLD, l.getFont().getSize2D() - 1.5f));
        l.setForeground(MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(10, 0, 2, 0));
        return l;
    }

    public static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(MUTED);
        return l;
    }

    public static JPanel row(Component... items) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setOpaque(false);
        for (Component c : items) p.add(c);
        return p;
    }

    public static void placeholder(JComponent field, String text) {
        field.putClientProperty("JTextField.placeholderText", text);
    }

    private static boolean sqlVisible = false;
    private static final List<JComponent> SQL_PANES = new ArrayList<>();
    private static final List<JToggleButton> SQL_TOGGLES = new ArrayList<>();

    public static boolean isSqlVisible() { return sqlVisible; }

    /** Show/hide the "Underlying SQL Statement" box on every page at once. */
    public static void setSqlVisible(boolean v) {
        sqlVisible = v;
        for (JComponent c : SQL_PANES) {
            c.setVisible(v);
            Container parent = c.getParent();
            if (parent != null) {
                parent.invalidate();
                parent.validate();
                parent.repaint();
            }
        }
        for (JToggleButton b : SQL_TOGGLES) b.setSelected(v);
    }

    /**
     * Standard page: header (title, subtitle, optional actions, "Show SQL" toggle),
     * body, and the collapsible "Underlying SQL Statement" box at the bottom.
     */
    public static JPanel page(String title, String subtitle, JComponent actions, JComponent body, JTextArea sql) {
        JPanel page = new JPanel(new BorderLayout(0, 12));
        page.setBackground(PAGE);
        page.setBorder(BorderFactory.createEmptyBorder(18, 20, 14, 20));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel t = new JLabel(title);
        t.setFont(t.getFont().deriveFont(Font.BOLD, t.getFont().getSize2D() + 8f));
        t.setForeground(TEXT);
        titles.add(t);
        if (subtitle != null) {
            titles.add(Box.createVerticalStrut(2));
            titles.add(muted(subtitle));
        }
        header.add(titles, BorderLayout.CENTER);

        JPanel right = row();
        if (actions != null) right.add(actions);
        if (sql != null) {
            JToggleButton toggle = new JToggleButton("Show SQL", sqlVisible);
            toggle.setToolTipText("Show the SQL statement this page just ran");
            toggle.addActionListener(e -> setSqlVisible(toggle.isSelected()));
            SQL_TOGGLES.add(toggle);
            right.add(toggle);
        }
        header.add(right, BorderLayout.EAST);
        page.add(header, BorderLayout.NORTH);
        page.add(body, BorderLayout.CENTER);

        if (sql != null) {
            JScrollPane sp = new JScrollPane(sql);
            sp.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BORDER), "Underlying SQL Statement"),
                    BorderFactory.createEmptyBorder(2, 2, 2, 2)));
            sp.setPreferredSize(new Dimension(200, 170));
            sp.setVisible(sqlVisible);
            SQL_PANES.add(sp);
            page.add(sp, BorderLayout.SOUTH);
        }
        return page;
    }

    /** Rounded 1px line border. */
    public static final class RoundBorder extends AbstractBorder {
        private final Color color;
        private final int arc;

        public RoundBorder(Color color, int arc) { this.color = color; this.arc = arc; }

        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(x, y, w - 1, h - 1, arc, arc);
            g2.dispose();
        }

        @Override public Insets getBorderInsets(Component c) { return new Insets(2, 2, 2, 2); }
    }
}
