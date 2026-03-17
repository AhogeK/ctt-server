package com.ahogek.cttserver.user.validator;

import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.springframework.stereotype.Component;

/**
 * User domain rules validator.
 *
 * <p>Responsible for semantic assertions that depend on external state or complex business context.
 * These validations go beyond DTO syntax validation and enforce domain invariants.
 *
 * <p>Key Rules:
 *
 * <ul>
 *   <li>Email uniqueness at registration
 *   <li>Login attempt limits for brute force protection
 *   <li>State machine transitions (e.g., only PENDING_VERIFICATION can verify email)
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
@Component
public class UserValidator {

    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;

    private final UserRepository userRepository;

    public UserValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Rule: Email must be globally unique during registration.
     *
     * <p>Time complexity: O(1) relying on database index scan.
     *
     * @param email the email to validate
     * @throws ConflictException if email already exists
     */
    public void assertEmailUnique(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException(ErrorCode.AUTH_002, "Email is already registered");
        }
    }

    /**
     * Rule: Login attempts must not exceed threshold to prevent brute force attacks.
     *
     * @param user the user to check
     * @throws UnauthorizedException if account is locked due to too many failed attempts
     */
    public void assertLoginAttemptsNotExceeded(User user) {
        if (user.getFailedLoginAttempts() != null
                && user.getFailedLoginAttempts() >= MAX_FAILED_LOGIN_ATTEMPTS) {
            throw new UnauthorizedException(
                    ErrorCode.AUTH_004, "Account is locked due to too many failed attempts");
        }
    }

    /**
     * Rule: Only users in PENDING_VERIFICATION status can verify email.
     *
     * @param user the user to check
     * @throws ConflictException if user is not in pending verification state
     */
    public void assertCanVerifyEmail(User user) {
        if (!"PENDING_VERIFICATION".equals(user.getStatus())) {
            throw new ConflictException(
                    ErrorCode.COMMON_003, "User is not in pending verification state");
        }
    }

    /**
     * Rule: User must exist for the given email.
     *
     * @param email the email to check
     * @throws NotFoundException if user not found
     */
    public void assertUserExists(String email) {
        if (userRepository.findByEmail(email).isEmpty()) {
            throw new NotFoundException(ErrorCode.AUTH_001, "User not found");
        }
    }
}
