package com.sidru.sidru_api.iam.domain.model.exceptions;

/** Una cuenta ADMIN no puede abrirse con login social. */
public class SocialAdminNotAllowedException extends RuntimeException {
    public SocialAdminNotAllowedException() {
        super();
    }

    public SocialAdminNotAllowedException(String message, Throwable cause) {
        super(message, cause);
    }
}
