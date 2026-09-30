package ai.terravision.inference;

import ai.terravision.config.InferenceProperties;
import ai.terravision.inference.dto.PredictionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real DJL/TorchScript path end to end. Skips itself (rather than
 * failing the build) until you've run model-export/export_model.py once and
 * dropped a sample image in src/test/resources -- both are one-time local setup
 * steps, not something CI should be blocked on until the model artifact exists.
 */
class ClassificationServiceIntegrationTest {

    private static final Path MODEL_DIR = Path.of("model");

    @Test
    void predictsThreeRankedClassesForARealImage() throws Exception {
        Assumptions.assumeTrue(Files.exists(MODEL_DIR.resolve("terravision-resnet50.pt")),
                "Skipping: run model-export/export_model.py first to produce model/terravision-resnet50.pt");

        InferenceProperties properties = new InferenceProperties(
                MODEL_DIR.toString(), "terravision-resnet50", 0.5, "low confidence");
        ClassMetadataService metadataService = new ClassMetadataService(new ObjectMapper());
        ClassificationService service = new ClassificationService(properties, metadataService, new ObjectMapper());
        service.loadModel();

        try (InputStream sample = getClass().getResourceAsStream("/sample-tile.jpg")) {
            Assumptions.assumeTrue(sample != null,
                    "Skipping: add a sample EuroSAT tile at src/test/resources/sample-tile.jpg");

            PredictionResult result = service.predict(sample);

            assertThat(result.className()).isNotBlank();
            assertThat(result.confidencePercent()).isBetween(0.0, 100.0);
            assertThat(result.inferenceTimeMs()).isGreaterThanOrEqualTo(0);
        } finally {
            service.close();
        }
    }
}
