package db;

/** A rule checked in Java (validation, transaction pre-checks). Its message is shown to the user as-is. */
public class BusinessException extends Exception {
    public BusinessException(String message) { super(message); }
}
