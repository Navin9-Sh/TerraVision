package ai.terravision.prediction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per /predict call. Not a record: JPA entities need a mutable, non-final
 * class with a no-arg constructor so Hibernate can proxy and lazily populate them.
 */
@Entity
@Table(name = "predictions")
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 64)
    private String imageHash;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String predictedClass;

    @Column(nullable = false)
    private double confidence;

    // Explicit name: Hibernate's default CamelCaseToUnderscoresNamingStrategy doesn't
    // insert an underscore at a digit-to-uppercase boundary, so "top3Json" would
    // otherwise map to "top3json", not the "top3_json" column the Flyway migration
    // actually creates.
    @Column(name = "top3_json", columnDefinition = "text", nullable = false)
    private String top3Json;

    @Column(nullable = false)
    private long inferenceTimeMs;

    @Column(nullable = false)
    private String modelVersion;

    @Column(nullable = false)
    private boolean lowConfidence;

    // Nullable: rows created before user accounts existed have no owner. Not a
    // @ManyToOne relation on purpose -- callers only ever need the id, and avoiding
    // the association sidesteps lazy-loading/proxy concerns for a value nothing here
    // navigates through.
    @Column(name = "user_id")
    private Long userId;

    protected Prediction() {
        // for Hibernate
    }

    public Prediction(String imageHash, String filename, String predictedClass, double confidence,
                       String top3Json, long inferenceTimeMs, String modelVersion, boolean lowConfidence,
                       Long userId) {
        this.createdAt = Instant.now();
        this.imageHash = imageHash;
        this.filename = filename;
        this.predictedClass = predictedClass;
        this.confidence = confidence;
        this.top3Json = top3Json;
        this.inferenceTimeMs = inferenceTimeMs;
        this.modelVersion = modelVersion;
        this.lowConfidence = lowConfidence;
        this.userId = userId;
    }

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getImageHash() {
        return imageHash;
    }

    public String getFilename() {
        return filename;
    }

    public String getPredictedClass() {
        return predictedClass;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getTop3Json() {
        return top3Json;
    }

    public long getInferenceTimeMs() {
        return inferenceTimeMs;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public boolean isLowConfidence() {
        return lowConfidence;
    }

    public Long getUserId() {
        return userId;
    }
}
