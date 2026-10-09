package org.growforge.jalmarg.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

/**
 * Estimates water depth from a street photo using a vision model on Amazon Bedrock
 * (Converse API, default Amazon Nova Lite). The model is asked to use reference
 * objects in the frame (tyres, kerbs, legs, doors) and to answer in strict JSON.
 */
@Service
public class AiDepthService {

    private static final Logger log = LoggerFactory.getLogger(AiDepthService.class);

    private static final String PROMPT = """
            You are a flood-safety assistant for Indian cities during the monsoon.
            Look at this street photo and estimate how deep the standing water is.
            Use reference objects you can see to judge depth, for example:
            - two-wheeler tyre is about 1.8 ft tall, silencer/exhaust about 1 ft above road
            - car tyre about 2 ft tall, car door bottom about 1.2 ft
            - kerb / footpath edge about 0.5 ft, an adult knee about 1.5 ft, waist about 3 ft
            If there is no standing water on the road, set water_visible to false and depth_ft to 0.
            Answer with ONLY this JSON object and nothing else:
            {"water_visible": true|false,
             "depth_ft": number,
             "confidence": number between 0 and 1,
             "reference": "the object you used to judge depth",
             "summary_en": "one short sentence for a commuter, in English",
             "summary_hi": "the same sentence in simple Hindi (Devanagari)"}
            """;

    private final BedrockRuntimeClient bedrock;
    private final ObjectMapper mapper = new ObjectMapper();
    private final boolean enabled;
    private final String modelId;

    public AiDepthService(BedrockRuntimeClient bedrock,
                          @Value("${jalmarg.ai.enabled}") boolean enabled,
                          @Value("${jalmarg.ai.model-id}") String modelId) {
        this.bedrock = bedrock;
        this.enabled = enabled;
        this.modelId = modelId;
        log.info("AI depth estimation: {}", enabled ? "Amazon Bedrock model " + modelId : "disabled");
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Result of the AI look at a photo. */
    public record AiResult(boolean waterVisible, double depthFt, double confidence,
                           String reference, String summaryEn, String summaryHi) {}

    /** Returns null if AI is off or the call fails, so reporting never breaks. */
    public AiResult analyze(byte[] image, String contentType) {
        if (!enabled || image == null || image.length == 0) return null;
        try {
            ImageFormat format = switch (contentType == null ? "" : contentType) {
                case "image/png" -> ImageFormat.PNG;
                case "image/webp" -> ImageFormat.WEBP;
                case "image/gif" -> ImageFormat.GIF;
                default -> ImageFormat.JPEG;
            };

            Message message = Message.builder()
                    .role(ConversationRole.USER)
                    .content(
                            ContentBlock.fromImage(ImageBlock.builder()
                                    .format(format)
                                    .source(ImageSource.fromBytes(SdkBytes.fromByteArray(image)))
                                    .build()),
                            ContentBlock.fromText(PROMPT))
                    .build();

            ConverseResponse response = bedrock.converse(ConverseRequest.builder()
                    .modelId(modelId)
                    .messages(message)
                    .inferenceConfig(InferenceConfiguration.builder()
                            .maxTokens(400)
                            .temperature(0.1f)
                            .build())
                    .build());

            if (response == null || response.output() == null || response.output().message() == null) {
                log.warn("Bedrock returned empty response structure");
                return null;
            }

            var contentList = response.output().message().content();
            if (contentList == null || contentList.isEmpty()) {
                log.warn("Bedrock returned empty content list");
                return null;
            }

            String text = contentList.stream()
                    .filter(c -> c.text() != null && !c.text().isBlank())
                    .map(ContentBlock::text)
                    .findFirst()
                    .orElse(null);

            return parse(text);
        } catch (ThrottlingException e) {
            log.warn("Bedrock depth estimation throttled (rate limit): {}", e.getMessage());
            return null;
        } catch (AccessDeniedException e) {
            log.warn("Bedrock access denied (check IAM permissions/model access): {}", e.getMessage());
            return null;
        } catch (ModelTimeoutException e) {
            log.warn("Bedrock model timeout: {}", e.getMessage());
            return null;
        } catch (ValidationException e) {
            log.warn("Bedrock request validation failed (check model ID / image format): {}", e.getMessage());
            return null;
        } catch (ResourceNotFoundException e) {
            log.warn("Bedrock resource/model not found: {}", e.getMessage());
            return null;
        } catch (BedrockRuntimeException e) {
            log.warn("Bedrock runtime error: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Bedrock depth estimation failed: {}", e.getMessage());
            return null;
        }
    }

    /** Pulls the JSON object out of the model text (models sometimes add ``` fences or chat text). */
    public AiResult parse(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start < 0 || end <= start) return null;

            JsonNode n = mapper.readTree(text.substring(start, end + 1));
            if (!n.isObject()) return null;

            double depth = Math.max(0, Math.min(8.0, n.path("depth_ft").asDouble(0)));
            double conf = Math.max(0, Math.min(1.0, n.path("confidence").asDouble(0.5)));
            boolean waterVisible = n.has("water_visible")
                    ? n.path("water_visible").asBoolean(depth > 0)
                    : depth > 0;

            String ref = n.path("reference").asText("").trim();
            String summaryEn = n.path("summary_en").asText("").trim();
            String summaryHi = n.path("summary_hi").asText("").trim();

            return new AiResult(
                    waterVisible,
                    Math.round(depth * 10) / 10.0,
                    conf,
                    ref,
                    summaryEn,
                    summaryHi);
        } catch (Exception e) {
            log.warn("Failed to parse Bedrock response text: {}", e.getMessage());
            return null;
        }
    }
}
