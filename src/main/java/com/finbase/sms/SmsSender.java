package com.finbase.sms;

/**
 * Delivers an SMS via a DLT-registered template. The OTP flow depends only
 * on this interface, never on a specific provider (MSG91, Kaleyra, ...).
 */
public interface SmsSender {

    /**
     * @param mobile 10-digit Indian mobile number, no country code
     * @param message the fully rendered message text
     */
    void send(String mobile, String message);
}
