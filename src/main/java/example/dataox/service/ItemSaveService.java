package example.dataox.service;

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

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = DataIntegrityViolationException.class
    )
    public void saveItem(Item item) {
        try {
            itemRepository.save(item);
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate item detected: {}", item);
        }
    }
}