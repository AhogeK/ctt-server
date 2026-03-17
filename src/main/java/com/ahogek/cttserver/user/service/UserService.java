package com.ahogek.cttserver.user.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.dto.UserRegisterRequest;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;
import com.ahogek.cttserver.user.validator.UserValidator;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User application service.
 *
 * <p>Coordinates domain logic for user-related operations. Responsible for:
 *
 * <ul>
 *   <li>Validating domain rules via UserValidator
 *   <li>Building and persisting domain entities
 *   <li>Publishing audit events
 *   <li>Orchestrating transactional boundaries
 * </ul>
 *
 * <p>This is a thin orchestration layer - all business rules are delegated to the domain validator.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserValidator userValidator;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public UserService(
            UserRepository userRepository,
            UserValidator userValidator,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.userValidator = userValidator;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    /**
     * Registers a new user.
     *
     * <p>Process:
     *
     * <ol>
     *   <li>Domain validation (email uniqueness)
     *   <li>Build user entity with encoded password
     *   <li>Persist user
     *   <li>Audit log registration event
     * </ol>
     *
     * @param request the registration request
     */
    @Transactional
    public void registerUser(UserRegisterRequest request) {
        // 1. Domain rule validation (Fail-Fast)
        userValidator.assertEmailUnique(request.email());

        // 2. Build domain entity
        // Status defaults to PENDING_VERIFICATION via entity initialization
        User newUser = new User();
        newUser.setEmail(request.email());
        newUser.setDisplayName(request.displayName());
        newUser.setPasswordHash(passwordEncoder.encode(request.password()));

        // 3. Persist
        User savedUser = userRepository.save(newUser);

        // 4. Audit log
        auditLogService.logSuccess(
                savedUser.getId(),
                AuditAction.REGISTER_REQUESTED,
                ResourceType.USER,
                savedUser.getId().toString());
    }
}
