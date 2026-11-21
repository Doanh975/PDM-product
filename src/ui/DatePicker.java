package ui;

import db.BusinessException;
import db.DBConnection;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

/** A date field (yyyy-MM-dd, can be typed) with a calendar button that opens a month view. */
public class DatePicker extends JPanel {

    private final JTextField field = new JTextField(9);
    private final JButton button = new JButton(new CalendarIcon());
    private YearMonth shown = YearMonth.now();

    public DatePicker() { this(null); }

    public DatePicker(LocalDate initial) {
        super(new BorderLayout(4, 0));
        setOpaque(false);
        Theme.placeholder(field, "yyyy-mm-dd");
        button.setToolTipText("Pick a date");
        button.setFocusable(false);
        button.setMargin(new Insets(2, 6, 2, 6));
        add(field, BorderLayout.CENTER);
        add(button, BorderLayout.EAST);
        setDate(initial);
        button.addActionListener(e -> openPopup());
    }

    public void setDate(LocalDate d) { field.setText(d == null ? "" : d.toString()); }

    /** The entered date; throws a friendly message when it is empty or not a valid date. */
    public LocalDate getDate(String label) throws BusinessException {
        String v = field.getText().trim();
        if (v.isEmpty()) throw new BusinessException(label + " is required.");
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw new BusinessException(label + " is not a valid date. Use the calendar button or type yyyy-mm-dd.");
        }
    }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        field.setEnabled(enabled);
        button.setEnabled(enabled);
    }

    private void openPopup() {
        LocalDate current;
        try { current = LocalDate.parse(field.getText().trim()); } catch (Exception e) { current = LocalDate.now(); }
        shown = YearMonth.from(current);
        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.setBackground(Color.WHITE);
        popup.add(content);
        LocalDate selected = current;
        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            content.removeAll();
            JPanel head = new JPanel(new BorderLayout());
            head.setOpaque(false);
            JButton prev = new JButton("<");
            JButton next = new JButton(">");
            prev.putClientProperty("JButton.buttonType", "borderless");
            next.putClientProperty("JButton.buttonType", "borderless");
            JLabel title = new JLabel(shown.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + shown.getYear(),
                    SwingConstants.CENTER);
            title.setFont(title.getFont().deriveFont(Font.BOLD));
            head.add(prev, BorderLayout.WEST);
            head.add(title, BorderLayout.CENTER);
            head.add(next, BorderLayout.EAST);
            prev.addActionListener(e -> { shown = shown.minusMonths(1); render[0].run(); });
            next.addActionListener(e -> { shown = shown.plusMonths(1); render[0].run(); });

            JPanel grid = new JPanel(new GridLayout(0, 7, 2, 2));
            grid.setOpaque(false);
            for (DayOfWeek dow : new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY}) {
                JLabel l = new JLabel(dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).substring(0, 2), SwingConstants.CENTER);
                l.setForeground(Theme.MUTED);
                grid.add(l);
            }
            int offset = shown.atDay(1).getDayOfWeek().getValue() - 1;
            for (int i = 0; i < offset; i++) grid.add(new JLabel());
            LocalDate today = DBConnection.referenceDate();
            for (int day = 1; day <= shown.lengthOfMonth(); day++) {
                LocalDate d = shown.atDay(day);
                JButton b = new JButton(String.valueOf(day));
                b.setMargin(new Insets(2, 2, 2, 2));
                b.setPreferredSize(new Dimension(36, 30));
                b.setFocusable(false);
                if (d.equals(selected)) {
                    b.putClientProperty("FlatLaf.style", "background: #2563EB; foreground: #FFFFFF; borderWidth: 0");
                } else {
                    b.putClientProperty("JButton.buttonType", "borderless");
                    if (d.equals(today)) b.setForeground(Theme.ACCENT);
                    if (d.getDayOfWeek().getValue() >= 6) b.setForeground(Theme.MUTED);
                }
                b.addActionListener(e -> {
                    field.setText(d.toString());
                    popup.setVisible(false);
                });
                grid.add(b);
            }
            content.add(head, BorderLayout.NORTH);
            content.add(grid, BorderLayout.CENTER);
            content.revalidate();
            content.repaint();
            popup.pack();
        };
        render[0].run();
        popup.show(this, 0, getHeight() + 2);
    }

    /** Calendar icon drawn in code. */
    private static final class CalendarIcon implements Icon {
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(c.isEnabled() ? new Color(0x4B5563) : Theme.BORDER);
            g2.drawRoundRect(x + 1, y + 3, 13, 11, 3, 3);
            g2.fillRect(x + 1, y + 3, 14, 3);
            g2.drawLine(x + 4, y + 1, x + 4, y + 4);
            g2.drawLine(x + 11, y + 1, x + 11, y + 4);
            for (int r = 0; r < 2; r++) for (int col = 0; col < 3; col++) g2.fillRect(x + 4 + col * 3, y + 8 + r * 3, 2, 2);
            g2.dispose();
        }

        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }
    }
}
