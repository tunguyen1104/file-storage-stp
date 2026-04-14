package com.filestorage.infrastructure.security;

/**
 * Validation message constants used by custom constraint annotations.
 */
public final class Messages {

    private Messages() {
    }

    public static final String INVALID_BODY = "Invalid request body";

    public static final class Validation {
        public static final String PASSWORD =
                "Password must be 8–16 characters, containing at least 1 uppercase, "
                + "1 lowercase, 1 special character, and 1 digit";
        public static final String EMAIL = "Email not valid";

        private Validation() {
        }
    }
}

