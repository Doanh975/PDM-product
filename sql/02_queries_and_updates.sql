-- ============================================================================
-- IT079IU - PRINCIPLES OF DATABASE MANAGEMENT (Semester 1, 2026-2027)
-- GROUP 9 - TOPIC 15: EMPLOYEE ATTENDANCE AND SHIFT SCHEDULING
-- FILE 2: 12 SQL QUERIES, VIEW, APPLICATION DML & TRANSACTION (MySQL 8.0+)
-- ============================================================================

USE employee_attendance_db;

-- ----------------------------------------------------------------------------
-- Q1 [Topic 15 - Required Query 1 | Multi-table JOIN | Uses idx_shiftassignment_workdate_status]:
-- Question: What is the shift roster of one department ('Store Operations') for next week (2026-11-23 to 2026-11-29)?
-- ----------------------------------------------------------------------------
SELECT d.DepartmentName, sa.WorkDate, e.EmployeeID, e.FullName,
       s.ShiftName, s.StartTime, s.EndTime, sa.AssignmentStatus
FROM ShiftAssignment sa
JOIN Employee e   ON sa.EmployeeID = e.EmployeeID
JOIN Department d ON e.DepartmentID = d.DepartmentID
JOIN Shift s      ON sa.ShiftID = s.ShiftID
WHERE d.DepartmentName = 'Store Operations'
  AND sa.WorkDate BETWEEN '2026-11-23' AND '2026-11-29'
  AND sa.AssignmentStatus <> 'Cancelled'
ORDER BY sa.WorkDate, s.StartTime, e.EmployeeID;

-- ----------------------------------------------------------------------------
-- Q2 [Topic 15 - Required Query 2 | Aggregation with GROUP BY and HAVING | Uses idx_attendance_status]:
-- Question: Which employees clocked in late more than three times last month (October 2026)?
-- ----------------------------------------------------------------------------
SELECT e.EmployeeID, e.FullName, d.DepartmentName,
       COUNT(a.AttendanceID) AS LateCountLastMonth
FROM Attendance a
JOIN ShiftAssignment sa ON a.ShiftAssignmentID = sa.ShiftAssignmentID
JOIN Employee e         ON sa.EmployeeID = e.EmployeeID
JOIN Department d       ON e.DepartmentID = d.DepartmentID
WHERE a.AttendanceStatus = 'Late'
  AND sa.WorkDate BETWEEN '2026-10-01' AND '2026-10-31'
GROUP BY e.EmployeeID, e.FullName, d.DepartmentName
HAVING COUNT(a.AttendanceID) > 3
ORDER BY LateCountLastMonth DESC;

-- ----------------------------------------------------------------------------
-- Q3 [Topic 15 - Required Query 3 | Aggregation with GROUP BY and HAVING]:
-- Question: What are the total overtime hours per department this quarter (Q4 2026)?
-- ----------------------------------------------------------------------------
SELECT d.DepartmentID, d.DepartmentName,
       COUNT(DISTINCT e.EmployeeID) AS ActiveStaffCount,
       SUM(a.OvertimeHours) AS TotalOvertimeHours
FROM Department d
JOIN Employee e         ON d.DepartmentID = e.DepartmentID
JOIN ShiftAssignment sa ON e.EmployeeID = sa.EmployeeID
JOIN Attendance a       ON sa.ShiftAssignmentID = a.ShiftAssignmentID
WHERE sa.WorkDate BETWEEN '2026-10-01' AND '2026-12-31'
GROUP BY d.DepartmentID, d.DepartmentName
HAVING SUM(a.OvertimeHours) > 0
ORDER BY TotalOvertimeHours DESC;

-- ----------------------------------------------------------------------------
-- Q4 [Topic 15 - Required Query 4 | Working-Day Calculation excluding Weekends & PublicHoliday]:
-- Question: Which leave requests are still awaiting approval after five working days (as of 2026-11-19)?
-- (MySQL 8.0 specific feature: WITH RECURSIVE calendar CTE to count exact working days)
-- ----------------------------------------------------------------------------
WITH RECURSIVE CalendarDates AS (
    SELECT DATE('2026-11-01') AS CalDate
    UNION ALL
    SELECT DATE_ADD(CalDate, INTERVAL 1 DAY)
    FROM CalendarDates
    WHERE CalDate < DATE('2026-11-19')
),
WorkingDays AS (
    SELECT c.CalDate
    FROM CalendarDates c
    WHERE DAYOFWEEK(c.CalDate) NOT IN (1, 7)
      AND NOT EXISTS (
          SELECT 1 FROM PublicHoliday ph
          WHERE ph.HolidayDate = c.CalDate AND ph.IsPaidHoliday = TRUE
      )
)
SELECT lr.LeaveRequestID, e.FullName AS Applicant, d.DepartmentName,
       lr.LeaveType, lr.SubmittedAt, lr.StartDate, lr.EndDate,
       (SELECT COUNT(*)
        FROM WorkingDays wd
        WHERE wd.CalDate > DATE(lr.SubmittedAt)
          AND wd.CalDate <= DATE('2026-11-19')) AS WorkingDaysWaiting
FROM LeaveRequest lr
JOIN Employee e   ON lr.EmployeeID = e.EmployeeID
JOIN Department d ON e.DepartmentID = d.DepartmentID
WHERE lr.RequestStatus = 'Pending'
  AND (SELECT COUNT(*)
       FROM WorkingDays wd
       WHERE wd.CalDate > DATE(lr.SubmittedAt)
         AND wd.CalDate <= DATE('2026-11-19')) > 5
ORDER BY lr.SubmittedAt ASC;

-- ----------------------------------------------------------------------------
-- Q5 [Topic 15 - Required Query 5 | Relational Division using Double NOT EXISTS ("for all")]:
-- Question: Which active employees were present on every completed working day of the month (Nov 1-19, 2026)?
-- ----------------------------------------------------------------------------
SELECT e.EmployeeID, e.FullName, d.DepartmentName
FROM Employee e
JOIN Department d ON e.DepartmentID = d.DepartmentID
WHERE e.EmploymentStatus = 'Active'
  AND EXISTS (
      SELECT 1
      FROM ShiftAssignment sa_check
      WHERE sa_check.WorkDate BETWEEN '2026-11-01' AND '2026-11-19'
        AND DAYOFWEEK(sa_check.WorkDate) NOT IN (1, 7)
        AND NOT EXISTS (
            SELECT 1 FROM PublicHoliday ph2
            WHERE ph2.HolidayDate = sa_check.WorkDate AND ph2.IsPaidHoliday = TRUE
        )
  )
  AND NOT EXISTS (
      SELECT DISTINCT sa_days.WorkDate
      FROM ShiftAssignment sa_days
      WHERE sa_days.WorkDate BETWEEN '2026-11-01' AND '2026-11-19'
        AND DAYOFWEEK(sa_days.WorkDate) NOT IN (1, 7)
        AND NOT EXISTS (
            SELECT 1 FROM PublicHoliday ph
            WHERE ph.HolidayDate = sa_days.WorkDate AND ph.IsPaidHoliday = TRUE
        )
        AND NOT EXISTS (
            SELECT 1
            FROM ShiftAssignment sa
            JOIN Attendance a ON sa.ShiftAssignmentID = a.ShiftAssignmentID
            WHERE sa.EmployeeID = e.EmployeeID
              AND sa.WorkDate = sa_days.WorkDate
              AND a.AttendanceStatus = 'Present'
        )
  );

-- ----------------------------------------------------------------------------
-- Q6 [Outer Join (LEFT JOIN) & Self-Join on Supervises]:
-- Question: List all employees along with their department, job position, and direct supervisor name.
-- ----------------------------------------------------------------------------
SELECT e.EmployeeID, e.FullName, d.DepartmentName, p.PositionName,
       COALESCE(m.FullName, 'Top Management') AS SupervisorName
FROM Employee e
JOIN Department d    ON e.DepartmentID = d.DepartmentID
JOIN `Position` p    ON e.PositionID = p.PositionID
LEFT JOIN Employee m ON e.ManagerID = m.EmployeeID
ORDER BY e.EmployeeID;

-- ----------------------------------------------------------------------------
-- Q7 [Set Operation (UNION ALL) across ISA Hierarchy]:
-- Question: Compare the estimated monthly base pay and expected monthly hours of all Full-Time and Part-Time employees.
-- ----------------------------------------------------------------------------
SELECT e.EmployeeID, e.FullName, 'Full-Time' AS EmploymentType,
       ft.MonthlyBaseSalary AS EstimatedMonthlyPay,
       ft.StandardMonthlyHours AS ExpectedMonthlyHours
FROM Employee e
JOIN FullTime ft ON e.EmployeeID = ft.EmployeeID
UNION ALL
SELECT e.EmployeeID, e.FullName, 'Part-Time' AS EmploymentType,
       (pt.HourlyRate * pt.ExpectedWeeklyHours * 4) AS EstimatedMonthlyPay,
       (pt.ExpectedWeeklyHours * 4) AS ExpectedMonthlyHours
FROM Employee e
JOIN PartTime pt ON e.EmployeeID = pt.EmployeeID
ORDER BY EmployeeID;

-- ----------------------------------------------------------------------------
-- Q8 [Outer Join + Aggregation with GROUP BY and HAVING]:
-- Question: Which shift requirements are currently understaffed (fewer active assignments than required)?
-- ----------------------------------------------------------------------------
SELECT sr.WorkDate, s.ShiftName, sr.RequiredEmployees,
       COUNT(sa.ShiftAssignmentID) AS AssignedEmployees,
       (sr.RequiredEmployees - COUNT(sa.ShiftAssignmentID)) AS StaffingGap
FROM ShiftRequirement sr
JOIN Shift s ON sr.ShiftID = s.ShiftID
LEFT JOIN ShiftAssignment sa
       ON sr.ShiftID = sa.ShiftID
      AND sr.WorkDate = sa.WorkDate
      AND sa.AssignmentStatus <> 'Cancelled'
GROUP BY sr.WorkDate, s.ShiftName, sr.RequiredEmployees
HAVING COUNT(sa.ShiftAssignmentID) < sr.RequiredEmployees
ORDER BY sr.WorkDate, s.ShiftName;

-- ----------------------------------------------------------------------------
-- Q9 [Nested Query with IN and EXISTS]:
-- Question: Find all employees who have submitted at least one Annual leave request and have also worked overtime.
-- ----------------------------------------------------------------------------
SELECT e.EmployeeID, e.FullName, d.DepartmentName, e.Email
FROM Employee e
JOIN Department d ON e.DepartmentID = d.DepartmentID
WHERE e.EmployeeID IN (
    SELECT lr.EmployeeID
    FROM LeaveRequest lr
    WHERE lr.LeaveType = 'Annual'
)
AND EXISTS (
    SELECT 1
    FROM ShiftAssignment sa
    JOIN Attendance a ON sa.ShiftAssignmentID = a.ShiftAssignmentID
    WHERE sa.EmployeeID = e.EmployeeID
      AND a.OvertimeHours > 0
)
ORDER BY e.EmployeeID;

-- ----------------------------------------------------------------------------
-- Q10 [Weak Entity Query - AttendanceEvent with Device/Location]:
-- Question: Show the detailed chronological clock event log (including device/location) for each recorded shift.
-- ----------------------------------------------------------------------------
SELECT a.AttendanceID, e.FullName, sa.WorkDate,
       ae.EventNo, ae.EventType, ae.EventTimestamp, ae.DeviceLocation
FROM AttendanceEvent ae
JOIN Attendance a       ON ae.AttendanceID = a.AttendanceID
JOIN ShiftAssignment sa ON a.ShiftAssignmentID = sa.ShiftAssignmentID
JOIN Employee e         ON sa.EmployeeID = e.EmployeeID
ORDER BY a.AttendanceID, ae.EventNo;

-- ----------------------------------------------------------------------------
-- Q11 [Outer Join (LEFT JOIN ... IS NULL)]:
-- Question: Which active employees do not yet have a system user account?
-- ----------------------------------------------------------------------------
SELECT e.EmployeeID, e.FullName, d.DepartmentName, p.PositionName
FROM Employee e
JOIN Department d        ON e.DepartmentID = d.DepartmentID
JOIN `Position` p        ON e.PositionID = p.PositionID
LEFT JOIN UserAccount ua ON e.EmployeeID = ua.EmployeeID
WHERE e.EmploymentStatus = 'Active'
  AND ua.UserID IS NULL
ORDER BY e.EmployeeID;

-- ----------------------------------------------------------------------------
-- Q12 [VIEW Creation and Querying - Required by Section 2.2]:
-- Question: Create a reusable view summarizing attendance counts and overtime hours per department, and query it.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_DepartmentAttendanceSummary AS
SELECT d.DepartmentID,
       d.DepartmentName,
       COUNT(a.AttendanceID) AS TotalAttendanceRecords,
       SUM(CASE WHEN a.AttendanceStatus = 'Present' THEN 1 ELSE 0 END) AS PresentCount,
       SUM(CASE WHEN a.AttendanceStatus = 'Late'    THEN 1 ELSE 0 END) AS LateCount,
       SUM(CASE WHEN a.AttendanceStatus = 'Absent'  THEN 1 ELSE 0 END) AS AbsentCount,
       COALESCE(SUM(a.OvertimeHours), 0.00) AS TotalOvertimeHours
FROM Department d
LEFT JOIN Employee e         ON d.DepartmentID = e.DepartmentID
LEFT JOIN ShiftAssignment sa ON e.EmployeeID = sa.EmployeeID
LEFT JOIN Attendance a       ON sa.ShiftAssignmentID = a.ShiftAssignmentID
GROUP BY d.DepartmentID, d.DepartmentName;

SELECT * FROM vw_DepartmentAttendanceSummary
ORDER BY TotalAttendanceRecords DESC, DepartmentName ASC;

-- ============================================================================
-- PART B: APPLICATION DML STATEMENTS (INSERT, UPDATE, DELETE FOR MAIN TABLES)
-- ============================================================================

-- 1. Main Table: Employee (INSERT, UPDATE, DELETE)
INSERT INTO Employee (EmployeeID, FullName, Ssn, Sex, Phone, Email, HireDate, EmploymentStatus, DepartmentID, PositionID, ManagerID)
VALUES (99, 'Nguyen Van Test', '999999999', 'M', '0909999999', 'test.nv@company.vn', '2026-11-19', 'Active', 2, 5, 1);

UPDATE Employee
SET Phone = '0908888888', EmploymentStatus = 'OnLeave'
WHERE EmployeeID = 99;

DELETE FROM Employee
WHERE EmployeeID = 99;

-- 2. Main Table: ShiftAssignment (INSERT, UPDATE, DELETE)
INSERT INTO ShiftAssignment (ShiftAssignmentID, EmployeeID, ShiftID, WorkDate, AssignmentStatus)
VALUES (99, 10, 1, '2026-11-27', 'Scheduled');

UPDATE ShiftAssignment
SET AssignmentStatus = 'Confirmed'
WHERE ShiftAssignmentID = 99;

DELETE FROM ShiftAssignment
WHERE ShiftAssignmentID = 99;

-- 3. Main Table: Attendance (INSERT, UPDATE, DELETE)
INSERT INTO Attendance (AttendanceID, ShiftAssignmentID, ActualCheckIn, ActualCheckOut, AttendanceStatus, OvertimeHours, Notes)
VALUES (99, 21, '2026-11-19 08:28:00', '2026-11-19 17:35:00', 'Present', 0.50, 'Completed support tickets');

UPDATE Attendance
SET OvertimeHours = 1.00, Notes = 'Updated overtime after manager verification'
WHERE AttendanceID = 99;

DELETE FROM Attendance
WHERE AttendanceID = 99;

-- 4. Main Table: LeaveRequest (INSERT, UPDATE, DELETE)
INSERT INTO LeaveRequest (LeaveRequestID, EmployeeID, LeaveType, StartDate, EndDate, Reason, SubmittedAt, RequestStatus)
VALUES (99, 10, 'Personal', '2026-12-15', '2026-12-15', 'Family errand', '2026-11-19 10:00:00', 'Pending');

UPDATE LeaveRequest
SET Reason = 'Updated reason: medical checkup'
WHERE LeaveRequestID = 99;

DELETE FROM LeaveRequest
WHERE LeaveRequestID = 99;

-- ============================================================================
-- PART C: MULTI-TABLE TRANSACTION WITH COMMIT & ROLLBACK (Section 2.3)
-- ============================================================================

-- Successful Transaction Example (COMMIT):
START TRANSACTION;

UPDATE LeaveRequest
SET RequestStatus = 'Approved',
    ReviewedBy    = 1,
    ReviewedAt    = NOW()
WHERE LeaveRequestID = 4
  AND RequestStatus  = 'Pending';

UPDATE LeaveBalance
SET UsedDays = UsedDays + 3.00
WHERE EmployeeID = 9
  AND LeaveYear  = 2026
  AND LeaveType  = 'Annual';

COMMIT;

-- Rollback Demonstration Example (Simulated Failure -> ROLLBACK):
START TRANSACTION;

UPDATE LeaveRequest
SET RequestStatus = 'Approved',
    ReviewedBy    = 1,
    ReviewedAt    = NOW()
WHERE LeaveRequestID = 3
  AND RequestStatus  = 'Pending';

ROLLBACK;
