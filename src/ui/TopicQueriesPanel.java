package ui;

import dao.Item;
import dao.LookupDao;
import dao.QueryDao;
import db.BusinessException;
import db.DBConnection;
import db.SqlErrorHandler;
import db.SqlTrace;
import db.TableData;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Font;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;

/** Insights: the five Topic 15 queries (Q1-Q5), phrased as questions, with easy parameter inputs. */
public class TopicQueriesPanel extends JPanel implements Refreshable {

    private static final String[][] QUESTIONS = {
            {"Q1 · Who is working next week?", "Shift roster of one department for a date range (cancelled shifts are hidden)."},
            {"Q2 · Who is often late?", "Employees who clocked in late more than N times in a month."},
            {"Q3 · How much overtime per department?", "Total overtime hours per department in a quarter."},
            {"Q4 · Which leave requests are waiting too long?", "Pending requests older than N working days (weekends and paid holidays are not counted)."},
            {"Q5 · Who had perfect attendance?", "Active employees who were present on every working day of the period."}
    };

    private final JComboBox<String> queryBox = new JComboBox<>();
    private final JLabel description = Theme.muted(" ");
    private final CardLayout cards = new CardLayout();
    private final JPanel params = new JPanel(cards);
    private final JLabel countLabel = new JLabel();
    private final JTable table = UiUtil.newTable();
    private final JTextArea sql = UiUtil.sqlArea();

    private final JComboBox<Item> q1Dept = new JComboBox<>();
    private final DatePicker q1From = new DatePicker();
    private final DatePicker q1To = new DatePicker();
    private final JComboBox<String> q2Month = new JComboBox<>();
    private final JSpinner q2Year = UiUtil.intSpinner(2026, 2000, 2100);
    private final JSpinner q2Times = UiUtil.intSpinner(3, 0, 100);
    private final JSpinner q3Year = UiUtil.intSpinner(2026, 2000, 2100);
    private final JComboBox<String> q3Quarter = new JComboBox<>(new String[]{"Q1 (Jan-Mar)", "Q2 (Apr-Jun)", "Q3 (Jul-Sep)", "Q4 (Oct-Dec)"});
    private final DatePicker q4AsOf = new DatePicker();
    private final JSpinner q4Days = UiUtil.intSpinner(5, 0, 365);
    private final DatePicker q5From = new DatePicker();
    private final DatePicker q5To = new DatePicker();

    private boolean defaultsSet;

    public TopicQueriesPanel() {
        super(new BorderLayout());
        for (String[] q : QUESTIONS) queryBox.addItem(q[0]);
        queryBox.setFont(queryBox.getFont().deriveFont(Font.BOLD));
        for (Month m : Month.values()) q2Month.addItem(m.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        q2Year.setEditor(new JSpinner.NumberEditor(q2Year, "#"));
        q3Year.setEditor(new JSpinner.NumberEditor(q3Year, "#"));

        params.setOpaque(false);
        params.add(card("Department", q1Dept, "From", q1From, "To", q1To), "0");
        params.add(card("Month", q2Month, q2Year, "Late more than", q2Times, "times"), "1");
        params.add(card("Year", q3Year, "Quarter", q3Quarter), "2");
        params.add(card("As of", q4AsOf, "Waiting more than", q4Days, "working days"), "3");
        params.add(card("From", q5From, "To", q5To), "4");

        JButton run = Theme.primary("Show results");
        JButton reset = Theme.subtle("Reset");

        JPanel question = new JPanel(new BorderLayout(0, 4));
        question.setOpaque(false);
        question.add(Theme.row(queryBox), BorderLayout.NORTH);
        JPanel descRow = Theme.row(description);
        descRow.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
        question.add(descRow, BorderLayout.CENTER);
        JPanel paramRow = new JPanel(new BorderLayout());
        paramRow.setOpaque(false);
        paramRow.add(params, BorderLayout.WEST);
        paramRow.add(Theme.row(run, reset), BorderLayout.CENTER);
        question.add(paramRow, BorderLayout.SOUTH);

        JPanel resultHead = new JPanel(new BorderLayout());
        resultHead.setOpaque(false);
        countLabel.setFont(countLabel.getFont().deriveFont(Font.BOLD));
        resultHead.add(countLabel, BorderLayout.WEST);
        JPanel results = new JPanel(new BorderLayout(0, 8));
        results.setOpaque(false);
        results.add(resultHead, BorderLayout.NORTH);
        results.add(Theme.tableScroll(table), BorderLayout.CENTER);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(Theme.card(question), BorderLayout.NORTH);
        body.add(Theme.card(results), BorderLayout.CENTER);
        add(Theme.page("Insights", "Answers to the five key questions of Topic 15 (Q1-Q5).", null, body, sql));

        queryBox.addActionListener(e -> {
            cards.show(params, String.valueOf(queryBox.getSelectedIndex()));
            run();
        });
        run.addActionListener(e -> run());
        reset.addActionListener(e -> { setDefaults(); run(); });
        q1Dept.addActionListener(e -> { if (defaultsSet) run(); });
    }

    private static JPanel card(Object... labelsAndFields) {
        JPanel p = Theme.row();
        for (Object o : labelsAndFields) p.add(o instanceof String s ? new JLabel(s) : (Component) o);
        return p;
    }

    @Override public void refresh() {
        try {
            Item keep = (Item) q1Dept.getSelectedItem();
            boolean wasSet = defaultsSet;
            defaultsSet = false;
            q1Dept.removeAllItems();
            Item storeOps = null;
            for (Item i : LookupDao.departments()) {
                q1Dept.addItem(i);
                if ("Store Operations".equalsIgnoreCase(i.label)) storeOps = i;
            }
            if (keep != null) q1Dept.setSelectedItem(keep);
            else if (storeOps != null) q1Dept.setSelectedItem(storeOps);
            defaultsSet = wasSet;
        } catch (Exception ex) {
            SqlErrorHandler.show(this, "Load departments", ex);
        }
        if (!defaultsSet) setDefaults();
        run();
    }

    public void show(int index) { queryBox.setSelectedIndex(index); }

    /** Defaults derived from app.referenceDate ("today"). */
    private void setDefaults() {
        LocalDate today = DBConnection.referenceDate();
        LocalDate nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        q1From.setDate(nextMonday);
        q1To.setDate(nextMonday.plusDays(6));
        YearMonth last = YearMonth.from(today).minusMonths(1);
        q2Month.setSelectedIndex(last.getMonthValue() - 1);
        q2Year.setValue(last.getYear());
        q2Times.setValue(3);
        q3Year.setValue(today.getYear());
        q3Quarter.setSelectedIndex((today.getMonthValue() - 1) / 3);
        q4AsOf.setDate(today);
        q4Days.setValue(5);
        q5From.setDate(today.withDayOfMonth(1));
        q5To.setDate(today);
        defaultsSet = true;
    }

    private void run() {
        SqlTrace.clear();
        int q = queryBox.getSelectedIndex();
        description.setText(QUESTIONS[q][1]);
        try {
            TableData d;
            switch (q) {
                case 0 -> {
                    Item dept = (Item) q1Dept.getSelectedItem();
                    if (dept == null) throw new BusinessException("Please choose a department.");
                    LocalDate from = q1From.getDate("From"), to = q1To.getDate("To");
                    checkRange(from, to);
                    d = QueryDao.q1(dept.id, from, to);
                }
                case 1 -> {
                    YearMonth ym = YearMonth.of((Integer) q2Year.getValue(), q2Month.getSelectedIndex() + 1);
                    d = QueryDao.q2(ym.atDay(1), ym.atEndOfMonth(), (Integer) q2Times.getValue());
                }
                case 2 -> {
                    LocalDate from = LocalDate.of((Integer) q3Year.getValue(), q3Quarter.getSelectedIndex() * 3 + 1, 1);
                    d = QueryDao.q3(from, from.plusMonths(3).minusDays(1));
                }
                case 3 -> d = QueryDao.q4(q4AsOf.getDate("As of date"), (Integer) q4Days.getValue());
                default -> {
                    LocalDate from = q5From.getDate("From"), to = q5To.getDate("To");
                    checkRange(from, to);
                    d = QueryDao.q5(from, to);
                }
            }
            UiUtil.setData(table, d);
            countLabel.setText(d.rowCount() == 0 ? "No results for these settings" : UiUtil.plural(d.rowCount(), "result"));
        } catch (Exception ex) {
            SqlErrorHandler.show(this, "Run query", ex);
        } finally {
            sql.setText(SqlTrace.dump());
        }
    }

    private static void checkRange(LocalDate from, LocalDate to) throws BusinessException {
        if (to.isBefore(from)) throw new BusinessException("'To' must be on or after 'From'.");
    }
}
