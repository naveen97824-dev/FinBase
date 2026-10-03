package com.finbase.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Dev-only stand-in for a real SMS provider. Logs that a message was sent,
 * never the message body — the OTP flow's "never log the OTP" rule applies
 * here too, not just to the hash in {@code otp_requests}.
 */
@Component
@Profile({"default", "local", "dev"})
@Slf4j
public class ConsoleSmsSender implements SmsSender {

    @Override
    public void send(String mobile, String message) {
        log.info("[DEV SMS] would send {} chars to {}", message.length(), mobile);
        System.out.println("---- DEV SMS to " + mobile + " ----");
        System.out.println(message);
        System.out.println("-----------------------------------");
    }
}
