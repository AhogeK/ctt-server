package com.ahogek.cttserver.user.validator;

import com.ahogek.cttserver.common.config.properties.TermsProperties;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.exception.ValidationException;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;
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
 *   <li>State machine transitions (e.g., only PENDING_VERIFICATION can verify email)
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
@Component
public class UserValidator {

    private final UserRepository userRepository;
    private final TermsProperties termsProperties;

    public UserValidator(UserRepository userRepository, TermsProperties termsProperties) {
        this.userRepository = userRepository;
        this.termsProperties = termsProperties;
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
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException(ErrorCode.USER_001, "Email is already registered");
        }
    }

    /**
     * Rule: Only users in PENDING_VERIFICATION status can verify email.
     *
     * @param user the user to check
     * @throws ConflictException if user is not in pending verification state
     */
    public void assertCanVerifyEmail(User user) {
        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new ConflictException(ErrorCode.USER_007);
        }
    }

    /**
     * Rule: User must exist for the given email.
     *
     * @param email the email to check
     * @throws NotFoundException if user not found
     */
    public void assertUserExists(String email) {
        if (userRepository.findByEmailIgnoreCase(email).isEmpty()) {
            throw new NotFoundException(ErrorCode.AUTH_001, "User not found");
        }
    }

    /**
     * Rule: User's accepted terms version must match the current active version.
     *
     * @param termsVersion the terms version accepted by user
     * @throws ValidationException if terms version does not match current version
     */
    public void assertTermsVersionValid(String termsVersion) {
        if (!termsProperties.currentVersion().equals(termsVersion)) {
            throw new ValidationException(ErrorCode.USER_008, "Terms version mismatch. Please refresh the page and try again.");
        }
    }
}
