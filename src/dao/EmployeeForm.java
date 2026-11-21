package dao;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Data of one employee as edited on the Employee CRUD tab (Employee + its FullTime/PartTime row). */
public final class EmployeeForm {

    public static final String FULL_TIME = "FullTime";
    public static final String PART_TIME = "PartTime";

    public Integer employeeId;
    public String fullName;
    public String ssn;
    public String sex;
    public String phone;
    public String email;
    public LocalDate hireDate;
    public String employmentStatus;
    public int departmentId;
    public int positionId;
    public Integer managerId;

    /** FULL_TIME or PART_TIME (ISA subclass). */
    public String type;
    /** MonthlyBaseSalary for full-time, HourlyRate for part-time. */
    public BigDecimal pay;
    /** StandardMonthlyHours for full-time, ExpectedWeeklyHours for part-time. */
    public int hours;
}
