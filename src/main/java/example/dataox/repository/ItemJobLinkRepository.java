package example.dataox.repository;

import example.dataox.entity.ItemJobLink;
import example.dataox.entity.ItemJobLinkId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemJobLinkRepository extends JpaRepository<ItemJobLink, ItemJobLinkId> {
}