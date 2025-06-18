package example.dataox.service.save;

import example.dataox.entity.Item;
import example.dataox.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
        try {
            if (!itemRepository.existsByJobPageUrl(item.getJobPageUrl())) {
                itemRepository.saveAndFlush(item);
            } else {
                log.warn("Duplicate jobPageUrl found (skipped): {}", item.getJobPageUrl());
            }
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate jobPageUrl detected by DB constraint (skipped): {}", item.getJobPageUrl());
        }
    }
}