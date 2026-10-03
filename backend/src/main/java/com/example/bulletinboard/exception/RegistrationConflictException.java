package com.example.bulletinboard.exception;

/** ユーザー名またはメールアドレスが既存登録と競合したことを表す。 */
public class RegistrationConflictException extends RuntimeException {
    public enum Field {
        USERNAME("username", "このユーザー名はすでに登録されています。"),
        EMAIL("email", "このメールアドレスはすでに登録されています。");

        private final String name;
        private final String message;

        Field(String name, String message) {
            this.name = name;
            this.message = message;
        }

        public String getName() { return name; }
        public String getMessage() { return message; }
    }

    private final Field field;

    public RegistrationConflictException(Field field) {
        super(field.getMessage());
        this.field = field;
    }

    public Field getField() { return field; }
}
