package example.dataox.repository;

import example.dataox.entity.ListPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ListPageRepository extends JpaRepository<ListPage, Long> {
}