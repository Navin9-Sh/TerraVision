package ai.terravision.inference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
public class ClassMetadataService {

    public record ClassMetadata(String description, String emoji) {
    }

    private static final ClassMetadata FALLBACK = new ClassMetadata("", "🛰️");

    private final Map<String, ClassMetadata> metadata;

    public ClassMetadataService(ObjectMapper objectMapper) throws IOException {
        try (var stream = new ClassPathResource("class-metadata.json").getInputStream()) {
            this.metadata = objectMapper.readValue(stream,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, ClassMetadata.class));
        }
    }

    public ClassMetadata get(String className) {
        return metadata.getOrDefault(className, FALLBACK);
    }
}
