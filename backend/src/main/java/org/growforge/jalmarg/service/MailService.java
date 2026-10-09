package org.growforge.jalmarg.service;

import org.growforge.jalmarg.model.Enums.OtpPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends OTP emails over SMTP (MAIL_HOST etc.). Amazon SES works through its SMTP endpoint.
 *
 * Development-only fallback: with JALMARG_DEV_OTP=true and no SMTP configured, the code is
 * printed to the server console instead (marked DEV-ONLY). With neither, sending fails and
 * the API says email is not configured; an account is never marked verified without a code.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final String host;
    private final String from;
    private final boolean devLog;
    /** Last dev code per email, only filled in dev mode; used by automated tests. */
    private final Map<String, String> devCodes = new ConcurrentHashMap<>();

    public MailService(ObjectProvider<JavaMailSender> sender,
                       @Value("${spring.mail.host:}") String host,
                       @Value("${jalmarg.mail.from}") String from,
                       @Value("${jalmarg.auth.dev-otp-log}") boolean devLog) {
        this.sender = sender;
        this.host = host == null ? "" : host.trim();
        this.from = from;
        this.devLog = devLog;
        if (smtpConfigured()) log.info("Email OTP: SMTP via {}", this.host);
        else if (devLog) log.warn("Email OTP: DEV-ONLY mode, codes are printed to this console. Do not use in production.");
        else log.warn("Email OTP: NOT CONFIGURED. Set MAIL_HOST/MAIL_USERNAME/MAIL_PASSWORD (or JALMARG_DEV_OTP=true for local testing).");
    }

    public boolean smtpConfigured() {
        return !host.isEmpty() && sender.getIfAvailable() != null;
    }

    public boolean canSend() {
        return smtpConfigured() || devLog;
    }

    public String mode() {
        return smtpConfigured() ? "smtp" : devLog ? "dev-console" : "not-configured";
    }

    public void sendOtp(String email, String name, String code, OtpPurpose purpose) {
        String what = purpose == OtpPurpose.VERIFY_EMAIL ? "verify your email" : "reset your password";
        if (smtpConfigured()) {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(from);
            m.setTo(email);
            m.setSubject("Your JalMarg code: " + code);
            m.setText("Hi " + (name == null ? "" : name) + ",\n\n"
                    + "Use this code to " + what + " in JalMarg:\n\n    " + code + "\n\n"
                    + "It expires in 5 minutes and works once. If you did not ask for it, ignore this email.\n\n"
                    + "— JalMarg (Team Growforge)\n");
            sender.getObject().send(m);
            return;
        }
        if (devLog) {
            devCodes.put(email, code);
            log.warn("[DEV-ONLY OTP] {} code for {} = {}", purpose, email, code);
            return;
        }
        throw new IllegalStateException("Email is not configured on the server");
    }

    /** Dev/test helper. Returns null unless dev mode is on. Never exposed over HTTP. */
    public String lastDevCode(String email) {
        return devLog ? devCodes.get(email) : null;
    }
}
