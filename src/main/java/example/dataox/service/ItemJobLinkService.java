package example.dataox.service;

import example.dataox.entity.ItemJobLink;
import example.dataox.entity.ItemJobLinkId;
import example.dataox.repository.ItemJobLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemJobLinkService {

    private final ItemJobLinkRepository itemJobLinkRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveItemJobLink(String jobPageUrl, String jobPageUrlList) {
        ItemJobLinkId id = new ItemJobLinkId();
        id.setJobPageUrl(jobPageUrl);
        id.setJobPageUrlList(jobPageUrlList);

        if (!itemJobLinkRepository.existsById(id)) {
            ItemJobLink link = new ItemJobLink();
            link.setJobPageUrl(jobPageUrl);
            link.setJobPageUrlList(jobPageUrlList);
            itemJobLinkRepository.save(link);
            log.info("Saved ItemJobLink: jobPageUrl={}, jobPageUrlList={}", jobPageUrl, jobPageUrlList);
        } else {
            log.warn("Duplicate ItemJobLink found (skipped): jobPageUrl={}, jobPageUrlList={}", jobPageUrl, jobPageUrlList);
        }
    }
}