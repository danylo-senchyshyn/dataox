package example.dataox.service.save;

import example.dataox.entity.Item;
import example.dataox.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemSaveService {
    private final ItemRepository itemRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveItem(Item item) {
        if (!itemRepository.existsByJobPageUrl(item.getJobPageUrl())) {
            itemRepository.save(item);
        } else {
            log.warn("Duplicate jobPageUrl found (skipped): {}", item.getJobPageUrl());
        }
    }
}