package com.flmentalhealth.exception;

/** Custom exceptions grouped in one file instead of four. */
public class ApiExceptions {

    /** -> 404 */
    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) { super(message); }
    }

    /** -> 409 (duplicate email, duplicate license number, duplicate name) */
    public static class DuplicateResourceException extends RuntimeException {
        public DuplicateResourceException(String message) { super(message); }
    }

    /** -> 409 (an open request to this provider already exists) */
    public static class DuplicateReferralException extends RuntimeException {
        public DuplicateReferralException(String message) { super(message); }
    }

    /** -> 400 (tried to accept a referral with no remaining capacity) */
    public static class NoCapacityException extends RuntimeException {
        public NoCapacityException(String message) { super(message); }
    }

    /** -> 403 (tried to read or change someone else's record) */
    public static class ForbiddenOperationException extends RuntimeException {
        public ForbiddenOperationException(String message) { super(message); }
    }
}
