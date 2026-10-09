package org.growforge.jalmarg.service;

import org.growforge.jalmarg.dto.Dtos.*;
import org.growforge.jalmarg.model.Enums.Severity;
import org.growforge.jalmarg.model.Report;
import org.growforge.jalmarg.model.Subscription;
import org.growforge.jalmarg.repo.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Alerts people about flooding near places they care about (home, college, office).
 *
 * Delivery: one Amazon SNS topic. Each subscriber's email is subscribed to the topic with a
 * filter policy on "subscriptionId", so a single publish reaches exactly one person.
 * The check runs hourly (internal scheduler) or whenever Amazon EventBridge Scheduler
 * calls POST /api/alerts/run.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final SubscriptionRepository subs;
    private final ReportService reportService;
    private final WeatherService weather;
    private final SnsClient sns;
    private final String topicArn;
    private final boolean internalScheduler;

    public AlertService(SubscriptionRepository subs, ReportService reportService, WeatherService weather,
                        SnsClient sns,
                        @Value("${jalmarg.alerts.sns-topic-arn}") String topicArn,
                        @Value("${jalmarg.alerts.internal-scheduler}") boolean internalScheduler) {
        this.subs = subs;
        this.reportService = reportService;
        this.weather = weather;
        this.sns = sns;
        this.topicArn = topicArn == null ? "" : topicArn.trim();
        this.internalScheduler = internalScheduler;
        log.info("Alerts via SNS: {}", snsEnabled() ? this.topicArn : "disabled (log only)");
    }

    public boolean snsEnabled() {
        return !topicArn.isEmpty();
    }

    // ---------------------------------------------------------------- subscriptions

    @Transactional
    public SubscriptionView subscribe(SubscribeRequest req) {
        if (req.email() == null || !req.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Enter a valid email");
        }
        Subscription s = new Subscription();
        s.setName(req.name());
        s.setEmail(req.email().trim().toLowerCase(Locale.ROOT));
        s.setLabel(req.label() == null || req.label().isBlank() ? "Home" : req.label().trim());
        s.setLatitude(req.latitude());
        s.setLongitude(req.longitude());
        s.setRadiusKm(req.radiusKm() == null ? 3 : Math.max(0.5, Math.min(15, req.radiusKm())));
        s.setLang("hi".equalsIgnoreCase(req.lang()) ? "hi" : "en");
        s.setCreatedAt(Instant.now());
        s = subs.save(s);

        if (snsEnabled()) {
            try {
                // AWS sends a "Confirm subscription" email; alerts start after the user clicks it.
                sns.subscribe(software.amazon.awssdk.services.sns.model.SubscribeRequest.builder()
                        .topicArn(topicArn)
                        .protocol("email")
                        .endpoint(s.getEmail())
                        .attributes(Map.of("FilterPolicy",
                                "{\"subscriptionId\":[\"" + s.getId() + "\"]}"))
                        .returnSubscriptionArn(true)
                        .build());
                s.setSnsSubscribed(true);
                s = subs.save(s);
            } catch (Exception e) {
                log.warn("SNS subscribe failed for {}: {}", s.getEmail(), e.getMessage());
            }
        }
        return view(s);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionView> byEmail(String email) {
        return subs.findByEmailIgnoreCase(email == null ? "" : email.trim()).stream().map(this::view).toList();
    }

    @Transactional
    public void unsubscribe(long id, String email) {
        subs.findById(id)
                .filter(s -> s.getEmail().equalsIgnoreCase(email == null ? "" : email.trim()))
                .ifPresent(subs::delete);
    }

    private SubscriptionView view(Subscription s) {
        return new SubscriptionView(s.getId(), s.getName(), s.getEmail(), s.getLabel(),
                s.getLatitude(), s.getLongitude(), s.getRadiusKm(), s.getLang(), s.isSnsSubscribed());
    }

    // ---------------------------------------------------------------- live feed for the app

    /** What the app shows on the home screen for the user's current location. */
    public AlertFeed feed(double lat, double lng, double radiusKm, String lang) {
        boolean hi = "hi".equalsIgnoreCase(lang);
        WeatherService.RainForecast rain = weather.forecast(lat, lng);
        List<AlertItem> items = new ArrayList<>();

        if ("HEAVY".equals(rain.level()) || "VERY_HEAVY".equals(rain.level())) {
            items.add(new AlertItem(rain.level(),
                    hi ? "अगले 6 घंटे में भारी बारिश" : "Heavy rain in the next 6 hours",
                    hi ? String.format(Locale.ROOT, "लगभग %.0f mm बारिश का अनुमान। अंडरपास से बचें।", rain.next6hMm())
                       : String.format(Locale.ROOT, "About %.0f mm expected. Avoid underpasses and low roads.", rain.next6hMm()),
                    null, lat, lng));
        }

        for (Report r : reportService.activeNear(lat, lng, radiusKm)) {
            if (r.getSeverity() == Severity.LOW) continue;
            double km = Geo.distanceM(lat, lng, r.getLatitude(), r.getLongitude()) / 1000.0;
            items.add(new AlertItem(r.getSeverity().name(),
                    (hi ? "जलभराव: " : "Waterlogging: ") + r.getLocationName(),
                    describe(r, km, hi), r.getId(), r.getLatitude(), r.getLongitude()));
        }
        return new AlertFeed(rain.next6hMm(), rain.maxHourlyMm(), rain.level(), items);
    }

    static String describe(Report r, double km, boolean hi) {
        String vehicles;
        if (hi) {
            vehicles = !r.isBikeSafe() && !r.isCarSafe() ? "बाइक और कार दोनों के लिए ज़्यादा खतरा"
                    : !r.isBikeSafe() ? "बाइक/स्कूटर के लिए ज़्यादा खतरा" : "धीरे चलें";
            return String.format(Locale.ROOT, "%.1f km दूर, अनुमानित %.1f फीट पानी। %s।", km, r.getDepthFt(), vehicles);
        }
        vehicles = !r.isBikeSafe() && !r.isCarSafe() ? "high risk for bikes and cars"
                : !r.isBikeSafe() ? "high risk for bikes/scooters" : "drive slowly";
        return String.format(Locale.ROOT, "%.1f km away, estimated %.1f ft of water, %s.", km, r.getDepthFt(), vehicles);
    }

    // ---------------------------------------------------------------- scheduled push

    @Scheduled(cron = "${jalmarg.alerts.cron}")
    public void scheduledRun() {
        if (internalScheduler) runAll();
    }

    /** Checks every subscription and sends an alert if something new and serious is nearby. */
    @Transactional
    public int runAll() {
        int sent = 0;
        for (Subscription s : subs.findAll()) {
            try {
                if (checkOne(s)) sent++;
            } catch (Exception e) {
                log.warn("Alert check failed for subscription {}: {}", s.getId(), e.getMessage());
            }
        }
        log.info("Alert run finished: {} alerts sent", sent);
        return sent;
    }

    private boolean checkOne(Subscription s) {
        AlertFeed f = feed(s.getLatitude(), s.getLongitude(), s.getRadiusKm(), s.getLang());
        if (f.alerts().isEmpty()) return false;

        String signature = f.alerts().stream()
                .map(a -> a.reportId() == null ? a.level() : String.valueOf(a.reportId()))
                .sorted().collect(Collectors.joining(","));
        boolean recentlySent = s.getLastAlertAt() != null
                && s.getLastAlertAt().isAfter(Instant.now().minus(Duration.ofHours(3)));
        if (signature.equals(s.getLastAlertSignature()) && recentlySent) return false;

        boolean hi = "hi".equals(s.getLang());
        // SNS email subjects must be ASCII, so the subject stays English; the body can be Hindi.
        String subject = ("JalMarg alert near your " + s.getLabel()).replaceAll("[^\\x20-\\x7E]", "");
        StringBuilder body = new StringBuilder();
        body.append(hi ? "नमस्ते " : "Hi ").append(s.getName() == null ? "" : s.getName()).append(",\n\n");
        for (AlertItem a : f.alerts()) {
            body.append("• ").append(a.title()).append("\n  ").append(a.message()).append("\n");
        }
        body.append(hi ? "\nरास्ता देखने के लिए JalMarg ऐप खोलें। सुरक्षित रहें।\n"
                : "\nOpen JalMarg to see a safe route. Stay safe.\n");

        send(s, subject, body.toString());
        s.setLastAlertAt(Instant.now());
        s.setLastAlertSignature(signature.length() > 500 ? signature.substring(0, 500) : signature);
        subs.save(s);
        return true;
    }

    private void send(Subscription s, String subject, String body) {
        if (!snsEnabled()) {
            log.info("[ALERT -> {}] {}\n{}", s.getEmail(), subject, body);
            return;
        }
        sns.publish(PublishRequest.builder()
                .topicArn(topicArn)
                .subject(subject.length() > 99 ? subject.substring(0, 99) : subject)
                .message(body)
                .messageAttributes(Map.of("subscriptionId", MessageAttributeValue.builder()
                        .dataType("String").stringValue(String.valueOf(s.getId())).build()))
                .build());
    }
}
