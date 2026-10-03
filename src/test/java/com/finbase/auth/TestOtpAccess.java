package com.finbase.auth;

import com.finbase.sms.SmsSender;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Test-only {@link SmsSender}: captures the OTP sent to each mobile so
 * tests can verify with the real code, without production code ever
 * exposing the plaintext OTP anywhere (which would violate "never logged,
 * never in plaintext outside Redis").
 */
@Component
@Profile("test")
public class TestOtpAccess implements SmsSender {

    static java.util.Map<String, String> captured = new ConcurrentHashMap<>();
    private static final Pattern OTP_PATTERN = Pattern.compile("OTP is (\\d{6})");

    @Override
    public void send(String mobile, String message) {
        Matcher matcher = OTP_PATTERN.matcher(message);
        if (matcher.find()) {
            captured.put(mobile, matcher.group(1));
        }
    }
}
