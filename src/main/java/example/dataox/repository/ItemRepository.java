package example.dataox.repository;

import example.dataox.entity.Item;
import example.dataox.entity.ListPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {
    boolean existsByJobPageUrlAndLaborFunction(String jobPageUrl, String laborFunction);
}