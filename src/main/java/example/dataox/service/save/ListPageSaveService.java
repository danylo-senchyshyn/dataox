package example.dataox.service.save;

import example.dataox.entity.ListPage;
import example.dataox.repository.ListPageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ListPageSaveService {
    private final ListPageRepository listPageRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveItem(ListPage listPage) {
        if (!listPageRepository.existsByJobPageUrl(listPage.getJobPageUrl())) {
            listPageRepository.save(listPage);
        } else {
            log.warn("Duplicate jobPageUrl found (skipped): {}", listPage.getJobPageUrl());
        }
    }

}