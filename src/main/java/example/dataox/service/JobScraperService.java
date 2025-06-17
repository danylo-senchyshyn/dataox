package example.dataox.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class JobScraperService {
    private final ListPageScraperService listPageScraperService;
    private final ItemScraperService itemScraperService;

    public void scrapeAll(Map<String, String> jobFunctionUrls) {
        //listPageScraperService.scrapeAllJobs(jobFunctionUrls);
        itemScraperService.scrapeAllJobs(jobFunctionUrls);
    }
}