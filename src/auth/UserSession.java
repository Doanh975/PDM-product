package auth;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The logged-in account and what it may do. Role comes from UserAccount.Role:
 *   ADMIN   - whole company, maintains employees, raw data browser, resets passwords
 *   MANAGER - their team (all direct and indirect reports via Employee.ManagerID), reviews the team's leave
 *   USER    - an employee: only their own shifts, attendance and leave requests
 */
public final class UserSession {

    public final int userId;
    public final int employeeId;
    public final String username;
    public final String fullName;
    public final String role;
    public final boolean placeholderPassword;
    /** Direct and indirect reports (MANAGER only; empty for others). Loaded at sign-in. */
    public final Set<Integer> team;

    public UserSession(int userId, int employeeId, String username, String fullName, String role,
                       boolean placeholderPassword, Set<Integer> team) {
        this.userId = userId;
        this.employeeId = employeeId;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.placeholderPassword = placeholderPassword;
        this.team = team == null ? Set.of() : Collections.unmodifiableSet(team);
    }

    public boolean isAdmin()    { return "ADMIN".equalsIgnoreCase(role); }
    public boolean isManager()  { return "MANAGER".equalsIgnoreCase(role); }
    public boolean isEmployee() { return !isAdmin() && !isManager(); }

    public boolean canManageEmployees() { return isAdmin(); }
    public boolean canBrowseData()      { return isAdmin(); }
    public boolean canSeeReports()      { return isAdmin() || isManager(); }
    public boolean canReviewLeave()     { return isAdmin() || isManager(); }
    public boolean canSetOtherPasswords() { return isAdmin(); }

    /** Review = approve/reject. Never your own request; managers only for their team. */
    public boolean canReview(int applicantId) {
        if (applicantId == employeeId) return false;
        return isAdmin() || (isManager() && team.contains(applicantId));
    }

    /** Edit/delete a Pending request: admins any, everyone else only their own. */
    public boolean canEditRequestOf(int applicantId) {
        return isAdmin() || applicantId == employeeId;
    }

    /**
     * Employees this user may see, or null for "everyone" (ADMIN).
     * MANAGER: team + themselves. USER: only themselves.
     */
    public Set<Integer> visibleEmployees() {
        if (isAdmin()) return null;
        Set<Integer> s = new HashSet<>(isManager() ? team : Set.of());
        s.add(employeeId);
        return s;
    }

    /** Applicants whose requests this user may review, or null for "anyone but me" (ADMIN). */
    public Set<Integer> reviewableEmployees() {
        if (isAdmin()) return null;
        return isManager() ? team : Set.of();
    }

    public String roleLabel() {
        return isAdmin() ? "Administrator" : isManager() ? "Manager" : "Employee";
    }
}
