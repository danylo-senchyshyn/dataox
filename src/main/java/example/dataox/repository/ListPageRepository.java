package example.dataox.repository;

import example.dataox.entity.ListPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ListPageRepository extends JpaRepository<ListPage, Long> {
    Optional<ListPage> findByJobPageUrl(String jobPageUrl);
    boolean existsByJobPageUrl(String jobPageUrl);
}