package com.ahogek.cttserver.auth;

import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Spring Security adapter for CurrentUserProvider.
 *
 * <p>Single point of contact with SecurityContextHolder. Extracts CurrentUser from Spring
 * Security's Authentication principal.
 *
 * <p>This is the ONLY place in the system allowed to access SecurityContextHolder directly. All
 * other components use CurrentUserProvider interface.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
@Component
public class SpringSecurityCurrentUserProvider implements CurrentUserProvider {

    @Override
    public Optional<CurrentUser> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();

        // Core assembly logic: JWT filters will populate CurrentUser as principal
        if (principal instanceof CurrentUser currentUser) {
            return Optional.of(currentUser);
        }

        return Optional.empty();
    }

    @Override
    public CurrentUser getCurrentUserRequired() {
        return getCurrentUser()
                .orElseThrow(
                        () ->
                                new UnauthorizedException(
                                        ErrorCode.AUTH_001, "Authentication required"));
    }

    @Override
    public CurrentUser getActiveUserRequired() {
        CurrentUser user = getCurrentUserRequired();
        if (!user.isActive()) {
            throw createInactiveUserException(user);
        }
        return user;
    }

    /**
     * Creates appropriate exception for inactive user status.
     *
     * <p>Maps specific user statuses to semantic error codes:
     *
     * <ul>
     *   <li>PENDING_VERIFICATION -> AUTH_006 (Email not verified)
     *   <li>LOCKED -> AUTH_004 (Account locked)
     *   <li>SUSPENDED -> AUTH_005 (Account suspended)
     *   <li>DELETED -> AUTH_009 (Insufficient permissions)
     * </ul>
     *
     * @param user the inactive user
     * @return ForbiddenException with appropriate error code
     */
    private ForbiddenException createInactiveUserException(CurrentUser user) {
        return switch (user.status()) {
            case PENDING_VERIFICATION ->
                    new ForbiddenException(ErrorCode.AUTH_006, "Email verification required");
            case LOCKED -> new ForbiddenException(ErrorCode.AUTH_004, "Account is locked");
            case SUSPENDED -> new ForbiddenException(ErrorCode.AUTH_005, "Account is suspended");
            case DELETED -> new ForbiddenException(ErrorCode.AUTH_009, "Account is deactivated");
            default ->
                    new ForbiddenException(
                            ErrorCode.AUTH_009, "Account is not active: " + user.status());
        };
    }
}
