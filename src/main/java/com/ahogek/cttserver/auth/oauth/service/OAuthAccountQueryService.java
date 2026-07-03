package com.ahogek.cttserver.auth.oauth.service;

import com.ahogek.cttserver.auth.oauth.dto.OAuthAccountBinding;
import com.ahogek.cttserver.auth.oauth.repository.UserOAuthAccountRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read-only query service for OAuth account bindings.
 *
 * <p>Centralizes binding-list operations so controllers stay thin and consistent with the project's
 * Controller → Service → Repository layering. Sensitive fields (access token, refresh token,
 * provider user ID) are intentionally excluded from the returned DTOs.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-06-28
 */
@Service
public class OAuthAccountQueryService {

    private final UserOAuthAccountRepository oauthAccountRepository;

    public OAuthAccountQueryService(UserOAuthAccountRepository oauthAccountRepository) {
        this.oauthAccountRepository = oauthAccountRepository;
    }

    /**
     * Lists all OAuth account bindings for the given user.
     *
     * @param userId the core user ID
     * @return list of OAuth account binding DTOs (empty if no bindings exist)
     */
    @Transactional(readOnly = true)
    public List<OAuthAccountBinding> listBindings(UUID userId) {
        return oauthAccountRepository.findAllByUserId(userId).stream()
                .map(OAuthAccountBinding::fromEntity)
                .toList();
    }
}
