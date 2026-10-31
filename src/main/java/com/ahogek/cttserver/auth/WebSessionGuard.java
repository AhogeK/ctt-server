package com.ahogek.cttserver.auth;

import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;

/**
 * Checks that an operation is performed by a browser session rather than an API key.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-30
 */
public final class WebSessionGuard {

    private WebSessionGuard() {}

    public static CurrentUser requireWebSession(CurrentUser user) {
        if (user.authType() != CurrentUser.AuthenticationType.WEB_SESSION) {
            throw new ForbiddenException(ErrorCode.AUTH_025);
        }
        return user;
    }
}
