package ai.terravision.inference;

import ai.terravision.auth.AuthenticatedUser;
import ai.terravision.common.BadRequestException;
import ai.terravision.common.Sha256;
import ai.terravision.config.InferenceProperties;
import ai.terravision.inference.dto.PredictionResult;
import ai.terravision.prediction.PredictionHistoryService;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Prediction", description = "Run land-use classification on an uploaded image")
public class PredictionController {

    private static final Logger log = LoggerFactory.getLogger(PredictionController.class);

    private final ClassificationService classificationService;
    private final PredictionHistoryService historyService;
    private final InferenceProperties properties;
    private final MeterRegistry meterRegistry;

    public PredictionController(ClassificationService classificationService,
                                 PredictionHistoryService historyService,
                                 InferenceProperties properties,
                                 MeterRegistry meterRegistry) {
        this.classificationService = classificationService;
        this.historyService = historyService;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Operation(summary = "Classify an uploaded image",
            description = "Returns the top predicted EuroSAT class with its confidence score. "
                    + "Predictions below the configured confidence threshold are flagged as low-confidence.")
    @ApiResponse(responseCode = "200", description = "Classification succeeded")
    @ApiResponse(responseCode = "400", description = "Missing, empty, or non-image upload")
    @PostMapping(value = "/predict", consumes = "multipart/form-data")
    public ResponseEntity<PredictionResult> predict(@RequestParam("image") MultipartFile image,
                                                      @AuthenticationPrincipal AuthenticatedUser currentUser) throws IOException {
        validate(image);

        byte[] bytes = image.getBytes();
        String imageHash = Sha256.hash(bytes);
        PredictionResult result = classificationService.predictCached(imageHash, bytes);
        historyService.record(imageHash, image.getOriginalFilename(), result, properties.modelName(), currentUser.userId());

        meterRegistry.counter("terravision.predictions",
                "class", result.className(),
                "lowConfidence", String.valueOf(result.lowConfidence())).increment();

        log.info("predictedClass={} confidence={} lowConfidence={} inferenceTimeMs={}",
                result.className(), result.confidencePercent(), result.lowConfidence(), result.inferenceTimeMs());

        return ResponseEntity.ok(result);
    }

    private void validate(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BadRequestException("Uploaded file is empty");
        }
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException("Uploaded file must be an image");
        }
    }
}
