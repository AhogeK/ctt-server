package com.ahogek.cttserver.auth.infrastructure.security;

import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.user.enums.UserStatus;

import org.jspecify.annotations.NonNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Converts JWT token to CurrentUser authentication object.
 *
 * <p>Extracts user identity from JWT claims and constructs CurrentUser for Spring Security context.
 * Supports status and authorities claims for complete authentication context.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-04
 */
@Component
public class JwtToCurrentUserConverter
        implements Converter<Jwt, UsernamePasswordAuthenticationToken> {

    @Override
    public UsernamePasswordAuthenticationToken convert(@NonNull Jwt jwt) {
        if (jwt == null) {
            throw new IllegalArgumentException("JWT token cannot be null");
        }

        if (jwt.getSubject() == null) {
            throw new IllegalArgumentException("JWT subject (user ID) cannot be null");
        }

        UUID userId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaimAsString("email");
        String statusStr = jwt.getClaimAsString("status");
        String authoritiesStr = jwt.getClaimAsString("authorities");

        UserStatus status = (statusStr != null) ? UserStatus.valueOf(statusStr) : UserStatus.ACTIVE;

        Set<String> authorityStrings =
                (authoritiesStr != null && !authoritiesStr.isBlank())
                        ? Arrays.stream(authoritiesStr.split(","))
                                .map(String::trim)
                                .collect(Collectors.toSet())
                        : Collections.emptySet();

        List<SimpleGrantedAuthority> grantedAuthorities =
                authorityStrings.stream().map(SimpleGrantedAuthority::new).toList();

        CurrentUser currentUser =
                new CurrentUser(
                        userId,
                        email,
                        status,
                        authorityStrings,
                        CurrentUser.AuthenticationType.WEB_SESSION);

        return new UsernamePasswordAuthenticationToken(
                currentUser, jwt.getTokenValue(), grantedAuthorities);
    }
}
