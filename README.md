# Employee Attendance & Shift Scheduling

Desktop application for **IT079IU – Principles of Database Management**, Group 9, Topic 15.
Java Swing front end, JDBC access to a MySQL database (`employee_attendance_db`).

## Requirements

| | Version |
|---|---|
| Java (JDK to build, JRE/JDK to run) | 17 or newer |
| MySQL | 8.0 or newer |

All libraries are in `lib/`:
- `mysql-connector-j-8.2.0.jar` (JDBC driver)
- `flatlaf-3.5.4.jar` (look and feel)
- `jbcrypt-0.4.jar` (password hashing)

## Run the app

**macOS / Linux**
```bash
./run.sh
```
**Windows**
```
run.bat
```
The scripts compile the sources the first time. Run them with `--rebuild` after changing code.

### First start on a new computer
1. On the sign-in screen click **Database settings**.
2. Enter your MySQL user and password, then click **Test connection**. Use `root` or another admin account.
3. If the database does not exist yet, click **Install database...**.
   It runs `sql/01_schema_and_data.sql` and `sql/01b_triggers.sql`.

The settings are saved in `config.properties`. You can also load the database yourself:
```bash
mysql -u root -p < sql/01_schema_and_data.sql
mysql -u root -p < sql/01b_triggers.sql
```
Do not run `sql/02_queries_and_updates.sql` before a demo: its transaction example approves leave request #4.

## Sign in

The sample data has no real passwords, so any non-empty password works while
`auth.allowPlaceholderHash=true` is set in `config.properties`.
Use **File > Change password** to store a real (bcrypt) password.

| Account | Role | What it can do |
|---|---|---|
| `admin.tri` | Admin | Everything: all employees, employee CRUD, data browser, other users' passwords |
| `mgr.ngan`, `mgr.thinh` | Manager | Their team only (everyone below them in `ManagerID`); approve or reject the team's leave; reports |
| `binh.dinh` (and other `USER` accounts) | Employee | Own shifts, attendance and leave requests |

Managers cannot review their own requests. Permissions are checked in the UI and again in the data-access layer.

## Pages

| Page | Contents |
|---|---|
| Overview | Key numbers and open items (managers: their team) |
| Employees / My Team | Search by name, email or SSN; filter by department and status |
| Schedules & Attendance | Shifts, attendance, clock events and leave balance of an employee |
| Manage Employees | Add, edit and delete employees (admin) |
| Leave Requests | Submit, edit and delete pending requests; approve or reject |
| Reports | Attendance by department (view), understaffed shifts, pay overview |
| Insights (Q1–Q5) | The five Topic 15 queries with parameters |
| Data Browser | Read-only view of every table (admin) |

Every page has a **Show SQL** button that shows the statements it ran.

Approving a leave request is one transaction. It updates `LeaveRequest` and `LeaveBalance.UsedDays`, and rolls back if
the employee has no balance row for that leave type or not enough days left.

## Configuration (`config.properties`)

| Key | Meaning |
|---|---|
| `db.url`, `db.user`, `db.password` | MySQL connection |
| `auth.allowPlaceholderHash` | Accept any password for accounts without a real hash (demo data) |
| `app.referenceDate` | "Today" used for default dates. Empty means the real date; `2026-11-19` matches the sample data |
| `leave.dayCountMode` | `CALENDAR` counts every day of a leave; `WORKING` skips weekends and paid holidays |

## Build a portable version

```bash
./build.sh        # or build.bat on Windows
```
This creates `dist/` with a single `app.jar` (libraries and SQL scripts included), `config.properties`,
start files for macOS and Windows, and `HOW-TO-RUN.txt`. Copy the folder to any computer with Java 17+ and MySQL.

## Project structure

```
src/app      Main (entry point)
src/auth     sign-in, roles and permissions, team scope
src/db       connection, query helpers, error messages, schema metadata, SQL script runner
src/dao      SQL for employees, leave requests, queries and reports
src/ui       windows, pages and shared UI components
sql/         database scripts from the team
test/        SmokeTest (database checks), DemoShots (GUI walkthrough with screenshots)
screenshots/ screenshots of every page
```

## Tests

Run these on a freshly loaded database (01 and 01b only). The smoke test changes data, so reload the scripts afterwards.
```bash
javac -d test-bin -cp "bin:lib/*" test/*.java
java -cp "bin:lib/*:test-bin" SmokeTest
```
On Windows, use `;` instead of `:` in the class path.

## Troubleshooting

| Message | Fix |
|---|---|
| Cannot reach MySQL | Start MySQL; check host and port in Database settings |
| MySQL rejected the user/password | Correct them in Database settings |
| The database is not installed yet | Database settings > Install database |
| This MySQL user may not create triggers | Install again as `root`, or run `sql/01b_triggers.sql` as an admin |
