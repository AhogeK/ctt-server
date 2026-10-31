package com.ahogek.cttserver.auth.dto;

/**
 * Result of a password login: either tokens or a second-factor challenge.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-30
 */
public sealed interface LoginOutcome {

    record Success(LoginResponse response) implements LoginOutcome {}

    record Challenge(LoginChallengeResponse challenge) implements LoginOutcome {}
}
