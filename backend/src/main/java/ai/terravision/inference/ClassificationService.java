package ai.terravision.inference;

import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.terravision.config.InferenceProperties;
import ai.terravision.inference.dto.PredictionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads the TorchScript model exported by model-export/export_model.py once at
 * startup, then serves predictions through DJL's PyTorch engine. Nothing here
 * ever shells out to Python -- the .pt file is a self-contained graph.
 */
@Service
public class ClassificationService {

    private final InferenceProperties properties;
    private final ClassMetadataService metadataService;
    private final ObjectMapper objectMapper;

    private ZooModel<Image, Classifications> model;

    public ClassificationService(InferenceProperties properties,
                                  ClassMetadataService metadataService,
                                  ObjectMapper objectMapper) {
        this.properties = properties;
        this.metadataService = metadataService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadModel() throws IOException, ModelNotFoundException, MalformedModelException {
        Path modelDir = Path.of(properties.modelDir());
        Path classesFile = modelDir.resolve("classes.json");
        List<String> classNames = objectMapper.readValue(
                Files.readString(classesFile), new TypeReference<List<String>>() {
                });

        Criteria<Image, Classifications> criteria = Criteria.builder()
                .setTypes(Image.class, Classifications.class)
                .optModelPath(modelDir)
                .optModelName(properties.modelName())
                .optEngine("PyTorch")
                .optTranslator(new ResNetTranslator(classNames))
                .build();

        model = criteria.loadModel();
    }

    public PredictionResult predict(InputStream imageStream) throws IOException {
        Image image = ImageFactory.getInstance().fromInputStream(imageStream);

        long start = System.currentTimeMillis();
        Classifications classifications;
        try (Predictor<Image, Classifications> predictor = model.newPredictor()) {
            classifications = predictor.predict(image);
        } catch (Exception e) {
            throw new IOException("Inference failed", e);
        }
        long inferenceTimeMs = System.currentTimeMillis() - start;

        Classifications.Classification top = classifications.best();
        ClassMetadataService.ClassMetadata meta = metadataService.get(top.getClassName());
        double confidencePercent = Math.round(top.getProbability() * 1000) / 10.0;
        boolean lowConfidence = top.getProbability() < properties.confidenceThreshold();

        return new PredictionResult(
                top.getClassName(),
                confidencePercent,
                meta.description(),
                lowConfidence,
                lowConfidence ? properties.lowConfidenceMessage() : null,
                inferenceTimeMs);
    }

    /**
     * Same inference, keyed by the uploaded bytes' SHA-256 hash: classification is a
     * pure function of the image, so a repeat upload of identical bytes (common during
     * manual testing/demos) returns the cached result instantly instead of re-running
     * the model. Backed by Spring's default in-memory ConcurrentMapCacheManager, which
     * is unbounded -- fine for this project's expected traffic, but a real production
     * deployment would want a bounded/TTL'd cache (e.g. Caffeine) instead.
     */
    @Cacheable(cacheNames = "predictionsByHash", key = "#imageHash")
    public PredictionResult predictCached(String imageHash, byte[] imageBytes) throws IOException {
        return predict(new ByteArrayInputStream(imageBytes));
    }

    public boolean isReady() {
        return model != null;
    }

    @PreDestroy
    public void close() {
        if (model != null) {
            model.close();
        }
    }
}
