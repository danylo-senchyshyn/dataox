package example.dataox.repository;

import example.dataox.entity.ListPage;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ListPageRepository extends JpaRepository<ListPage, Long> {
    Optional<ListPage> findByJobPageUrl(String jobPageUrl);
    boolean existsByJobPageUrl(String jobPageUrl);
    int countByJobFunction(String jobFunction);

    @Modifying
    @Transactional
    @Query("UPDATE ListPage lp SET lp.numberOfFilteredJobs = :count WHERE lp.jobFunction = :jobFunction")
    void updateNumberOfFilteredJobsByJobFunction(@Param("jobFunction") String jobFunction, @Param("count") int count);
}