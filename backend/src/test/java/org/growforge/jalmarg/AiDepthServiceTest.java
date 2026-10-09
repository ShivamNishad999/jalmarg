package org.growforge.jalmarg.service;

import org.growforge.jalmarg.model.Enums.DepthLevel;
import org.growforge.jalmarg.model.Enums.DepthSource;
import org.growforge.jalmarg.model.Report;
import org.growforge.jalmarg.service.AiDepthService.AiResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class AiDepthServiceTest {

    private static class FakeBedrockClient implements BedrockRuntimeClient {
        Function<ConverseRequest, ConverseResponse> handler;
        ConverseRequest lastRequest;
        int callCount = 0;

        @Override
        public ConverseResponse converse(ConverseRequest request) {
            callCount++;
            this.lastRequest = request;
            if (handler != null) {
                return handler.apply(request);
            }
            return null;
        }

        @Override
        public String serviceName() {
            return "bedrock";
        }

        @Override
        public void close() {}
    }

    private FakeBedrockClient fakeBedrock;
    private AiDepthService aiEnabledService;
    private AiDepthService aiDisabledService;

    @BeforeEach
    void setUp() {
        fakeBedrock = new FakeBedrockClient();
        aiEnabledService = new AiDepthService(fakeBedrock, true, "apac.amazon.nova-lite-v1:0");
        aiDisabledService = new AiDepthService(fakeBedrock, false, "apac.amazon.nova-lite-v1:0");
    }

    @Test
    void disabledServiceReturnsNullWithoutCallingBedrock() {
        assertFalse(aiDisabledService.isEnabled());
        AiResult result = aiDisabledService.analyze(new byte[]{1, 2, 3}, "image/jpeg");
        assertNull(result);
        assertEquals(0, fakeBedrock.callCount, "Disabled AI must not invoke Bedrock API");
    }

    @Test
    void nullOrEmptyImageReturnsNull() {
        assertNull(aiEnabledService.analyze(null, "image/jpeg"));
        assertNull(aiEnabledService.analyze(new byte[0], "image/jpeg"));
        assertEquals(0, fakeBedrock.callCount);
    }

    @Test
    void successfulAnalysisWithValidJson() {
        String jsonText = """
                {
                  "water_visible": true,
                  "depth_ft": 1.4,
                  "confidence": 0.85,
                  "reference": "car tyre",
                  "summary_en": "Water reaches car tyre half-way; avoid driving small cars.",
                  "summary_hi": "पानी कार के टायर के आधे हिस्से तक है; छोटी कारें न ले जाएं।"
                }
                """;

        ConverseResponse response = ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .role(ConversationRole.ASSISTANT)
                                .content(ContentBlock.fromText(jsonText))
                                .build())
                        .build())
                .build();

        fakeBedrock.handler = req -> response;

        byte[] img = new byte[]{1, 2, 3};
        AiResult result = aiEnabledService.analyze(img, "image/jpeg");

        assertNotNull(result);
        assertTrue(result.waterVisible());
        assertEquals(1.4, result.depthFt());
        assertEquals(0.85, result.confidence());
        assertEquals("car tyre", result.reference());
        assertEquals("Water reaches car tyre half-way; avoid driving small cars.", result.summaryEn());
        assertEquals("पानी कार के टायर के आधे हिस्से तक है; छोटी कारें न ले जाएं।", result.summaryHi());
        assertEquals(1, fakeBedrock.callCount);
    }

    @Test
    void analysisHandlesMarkdownFencesAndExtraText() {
        String text = """
                Here is the depth analysis:
                ```json
                {
                  "water_visible": true,
                  "depth_ft": 0.6,
                  "confidence": 0.7,
                  "reference": "kerb",
                  "summary_en": "Water overflowing kerb.",
                  "summary_hi": "पानी फुटपाथ के ऊपर है।"
                }
                ```
                Hope this helps!
                """;

        AiResult result = aiEnabledService.parse(text);
        assertNotNull(result);
        assertTrue(result.waterVisible());
        assertEquals(0.6, result.depthFt());
        assertEquals(0.7, result.confidence());
        assertEquals("kerb", result.reference());
    }

    @Test
    void depthAndConfidenceAreBounded() {
        String outOfBounds = """
                {
                  "water_visible": true,
                  "depth_ft": 15.5,
                  "confidence": 1.8,
                  "reference": "lamppost",
                  "summary_en": "Deep water",
                  "summary_hi": "गहरा पानी"
                }
                """;

        AiResult result = aiEnabledService.parse(outOfBounds);
        assertNotNull(result);
        assertEquals(8.0, result.depthFt(), "Depth must be clamped to max 8.0 ft");
        assertEquals(1.0, result.confidence(), "Confidence must be clamped to max 1.0");

        String negativeBounds = """
                {
                  "water_visible": false,
                  "depth_ft": -2.0,
                  "confidence": -0.5,
                  "reference": "dry road",
                  "summary_en": "No water",
                  "summary_hi": "कोई पानी नहीं"
                }
                """;

        AiResult negResult = aiEnabledService.parse(negativeBounds);
        assertNotNull(negResult);
        assertEquals(0.0, negResult.depthFt());
        assertEquals(0.0, negResult.confidence());
    }

    @Test
    void malformedOrNonJsonReturnsNull() {
        assertNull(aiEnabledService.parse(null));
        assertNull(aiEnabledService.parse(""));
        assertNull(aiEnabledService.parse("I cannot determine depth from this photo."));
        assertNull(aiEnabledService.parse("{ broken json: true, depth_ft: }"));
    }

    @Test
    void handlesMissingFieldsGracefully() {
        String partialJson = """
                {
                  "depth_ft": 1.2
                }
                """;
        AiResult result = aiEnabledService.parse(partialJson);
        assertNotNull(result);
        assertTrue(result.waterVisible(), "Missing water_visible defaults to depth > 0");
        assertEquals(1.2, result.depthFt());
        assertEquals(0.5, result.confidence(), "Missing confidence defaults to 0.5");
        assertEquals("", result.reference());
        assertEquals("", result.summaryEn());
    }

    @Test
    void imageFormatsAreCorrectlyMappedInConverseRequest() {
        ConverseResponse response = ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder().content(ContentBlock.fromText("{\"water_visible\":false,\"depth_ft\":0}")).build())
                        .build())
                .build();
        fakeBedrock.handler = req -> response;

        aiEnabledService.analyze(new byte[]{1, 2}, "image/png");
        ImageBlock pngBlock = fakeBedrock.lastRequest.messages().get(0).content().get(0).image();
        assertEquals(ImageFormat.PNG, pngBlock.format());

        aiEnabledService.analyze(new byte[]{1, 2}, "image/webp");
        ImageBlock webpBlock = fakeBedrock.lastRequest.messages().get(0).content().get(0).image();
        assertEquals(ImageFormat.WEBP, webpBlock.format());

        aiEnabledService.analyze(new byte[]{1, 2}, "image/gif");
        ImageBlock gifBlock = fakeBedrock.lastRequest.messages().get(0).content().get(0).image();
        assertEquals(ImageFormat.GIF, gifBlock.format());

        aiEnabledService.analyze(new byte[]{1, 2}, "application/octet-stream");
        ImageBlock defaultBlock = fakeBedrock.lastRequest.messages().get(0).content().get(0).image();
        assertEquals(ImageFormat.JPEG, defaultBlock.format());
    }

    @Test
    void handlesThrottlingExceptionGracefully() {
        fakeBedrock.handler = req -> {
            throw ThrottlingException.builder().message("Rate exceeded").build();
        };

        AiResult result = aiEnabledService.analyze(new byte[]{1, 2}, "image/jpeg");
        assertNull(result, "ThrottlingException should be caught and return null gracefully");
    }

    @Test
    void handlesAccessDeniedExceptionGracefully() {
        fakeBedrock.handler = req -> {
            throw AccessDeniedException.builder().message("User is not authorized").build();
        };

        AiResult result = aiEnabledService.analyze(new byte[]{1, 2}, "image/jpeg");
        assertNull(result, "AccessDeniedException should be caught and return null gracefully");
    }

    @Test
    void handlesModelTimeoutExceptionGracefully() {
        fakeBedrock.handler = req -> {
            throw ModelTimeoutException.builder().message("Inference timed out").build();
        };

        AiResult result = aiEnabledService.analyze(new byte[]{1, 2}, "image/jpeg");
        assertNull(result, "ModelTimeoutException should be caught and return null gracefully");
    }

    @Test
    void handlesValidationExceptionGracefully() {
        fakeBedrock.handler = req -> {
            throw ValidationException.builder().message("Invalid model identifier").build();
        };

        AiResult result = aiEnabledService.analyze(new byte[]{1, 2}, "image/jpeg");
        assertNull(result, "ValidationException should be caught and return null gracefully");
    }

    @Test
    void handlesResourceNotFoundExceptionGracefully() {
        fakeBedrock.handler = req -> {
            throw ResourceNotFoundException.builder().message("Model not found").build();
        };

        AiResult result = aiEnabledService.analyze(new byte[]{1, 2}, "image/jpeg");
        assertNull(result, "ResourceNotFoundException should be caught and return null gracefully");
    }

    @Test
    void handlesEmptyOrIncompleteBedrockResponseStructure() {
        // Null message
        ConverseResponse nullMessageResp = ConverseResponse.builder()
                .output(ConverseOutput.builder().build())
                .build();
        fakeBedrock.handler = req -> nullMessageResp;
        assertNull(aiEnabledService.analyze(new byte[]{1, 2}, "image/jpeg"));

        // Empty content list
        ConverseResponse emptyContentResp = ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder().content(Collections.<ContentBlock>emptyList()).build())
                        .build())
                .build();
        fakeBedrock.handler = req -> emptyContentResp;
        assertNull(aiEnabledService.analyze(new byte[]{1, 2}, "image/jpeg"));
    }

    // ------------------------------------------------------------- Depth Fusion & Source Labeling

    @Test
    void fuseDepthOnlyAiResultsInAiSource() {
        Report r = new Report();
        AiResult ai = new AiResult(true, 1.8, 0.8, "scooter tyre", "Water up to scooter seat", "पानी सीट तक है");

        ReportService.fuseDepth(r, null, ai);

        assertEquals(1.8, r.getDepthFt());
        assertEquals(DepthSource.AI, r.getDepthSource(), "Must be tagged AI when only AI provided depth");
        assertEquals(0.8, r.getAiConfidence());
        assertTrue(r.getAiSummary().contains("judged from: scooter tyre"));
    }

    @Test
    void fuseDepthCitizenOnlyResultsInCitizenSource() {
        Report r = new Report();
        ReportService.fuseDepth(r, DepthLevel.KNEE, null);

        assertEquals(1.5, r.getDepthFt());
        assertEquals(DepthSource.CITIZEN, r.getDepthSource(), "Must be tagged CITIZEN when AI is null/disabled");
        assertNull(r.getAiSummary());
    }

    @Test
    void fuseDepthBothAiAndCitizenCombinesWithConfidence() {
        Report r = new Report();
        // AI = 2.0 ft, Citizen KNEE = 1.5 ft. Weighted: 0.6 * 2.0 + 0.4 * 1.5 = 1.2 + 0.6 = 1.8 ft
        AiResult ai = new AiResult(true, 2.0, 0.75, "bus wheel", "Water covers lower bus step", "");

        ReportService.fuseDepth(r, DepthLevel.KNEE, ai);

        assertEquals(1.8, r.getDepthFt());
        assertEquals(DepthSource.AI_AND_CITIZEN, r.getDepthSource(), "Must be tagged AI_AND_CITIZEN when both contribute");
    }

    @Test
    void fuseDepthLowAiConfidencePreservesCitizenDepth() {
        Report r = new Report();
        AiResult aiUnsure = new AiResult(true, 3.0, 0.3, "shadow", "Unclear water reflection", "");

        ReportService.fuseDepth(r, DepthLevel.SHIN, aiUnsure);

        assertEquals(0.8, r.getDepthFt(), "Should trust citizen SHIN depth (0.8 ft) when AI confidence < 0.5");
        assertEquals(DepthSource.CITIZEN, r.getDepthSource(), "Should be labeled CITIZEN when AI confidence is too low");
    }
}
