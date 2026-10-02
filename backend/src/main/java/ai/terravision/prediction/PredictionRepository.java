package ai.terravision.prediction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PredictionRepository extends JpaRepository<Prediction, Long>, JpaSpecificationExecutor<Prediction> {

    @Query("select count(p) from Prediction p where (:userId is null or p.userId = :userId)")
    long countTotal(@Param("userId") Long userId);

    @Query("select count(p) from Prediction p where p.lowConfidence = true and (:userId is null or p.userId = :userId)")
    long countLowConfidence(@Param("userId") Long userId);

    @Query("select avg(p.confidence) from Prediction p where (:userId is null or p.userId = :userId)")
    Double averageConfidence(@Param("userId") Long userId);

    @Query("select avg(p.inferenceTimeMs) from Prediction p where (:userId is null or p.userId = :userId)")
    Double averageInferenceTimeMs(@Param("userId") Long userId);

    @Query("""
            select p.predictedClass as className, count(p) as total from Prediction p
            where (:userId is null or p.userId = :userId)
            group by p.predictedClass
            """)
    List<ClassCount> countGroupedByClass(@Param("userId") Long userId);

    @Query("select p.userId as userId, count(p) as total from Prediction p where p.userId is not null group by p.userId")
    List<UserCount> countGroupedByUser();

    interface ClassCount {
        String getClassName();

        long getTotal();
    }

    interface UserCount {
        Long getUserId();

        long getTotal();
    }
}
