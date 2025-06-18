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
        boolean exists = itemRepository.existsByJobPageUrlAndLaborFunction(
                item.getJobPageUrl(), item.getLaborFunction());

        if (!exists) {
            itemRepository.save(item);
            log.info("Сохранена новая запись Item: url = {}, jobFunction = {}",
                    item.getJobPageUrl(), item.getLaborFunction());
        } else {
            log.info("Запись Item с url = {} и jobFunction = {} уже существует, пропускаем сохранение",
                    item.getJobPageUrl(), item.getLaborFunction());
        }
    }
}