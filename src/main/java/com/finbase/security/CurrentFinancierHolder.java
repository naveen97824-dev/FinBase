package com.finbase.security;

import java.util.UUID;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

/**
 * Holds the {@code financier_id} resolved from the current request's JWT,
 * set by {@link FinancierContextInterceptor} before the controller runs.
 *
 * <p>Request-scoped, not a ThreadLocal set by hand: the interceptor and the
 * controller both resolve this same proxy within one HTTP request, and
 * Spring tears it down automatically at request end — no risk of a stale
 * value leaking onto a thread that later serves a different financier.
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class CurrentFinancierHolder {

    private UUID financierId;

    public UUID getFinancierId() {
        if (financierId == null) {
            throw new IllegalStateException(
                    "No financier_id resolved for this request — FinancierContextInterceptor "
                            + "did not run, or the request has no valid JWT.");
        }
        return financierId;
    }

    public void setFinancierId(UUID financierId) {
        this.financierId = financierId;
    }
}
