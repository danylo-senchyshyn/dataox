package example.dataox;

import example.dataox.service.ItemScraperService;
import example.dataox.service.ListPageScraperService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JobScraperRunner implements CommandLineRunner {

    private final ListPageScraperService listPageScraperService;

    public JobScraperRunner(ListPageScraperService listPageScraperService) {
        this.listPageScraperService = listPageScraperService;
    }

    @Override
    public void run(String... args) {
        Map<String, String> jobFunctionUrls = new LinkedHashMap<>();

        jobFunctionUrls.put("Accounting & Finance", "https://jobs.techstars.com/jobs?filter=eyJqb2JfZnVuY3Rpb25zIjpbIkFjY291bnRpbmclMjAlMjYlMjBGaW5hbmNlIl19");
        jobFunctionUrls.put("Administration", "https://jobs.techstars.com/jobs?filter=eyJqb2JfZnVuY3Rpb25zIjpbIkFkbWluaXN0cmF0aW9uIl19");
        jobFunctionUrls.put("Compliance / Regulatory", "https://jobs.techstars.com/jobs?filter=eyJqb2JfZnVuY3Rpb25zIjpbIkNvbXBsaWFuY2UlMjAlMkYlMjBSZWd1bGF0b3J5Il19");
        jobFunctionUrls.put("Customer Service", "https://jobs.techstars.com/jobs?filter=eyJqb2JfZnVuY3Rpb25zIjpbIkN1c3RvbWVyJTIwU2VydmljZSJdfQ%3D%3D");
        jobFunctionUrls.put("Data Science", "https://jobs.techstars.com/jobs?filter=eyJqb2JfZnVuY3Rpb25zIjpbIkRhdGElMjBTY2llbmNlIl19");
        jobFunctionUrls.put("Design", "https://jobs.techstars.com/jobs?filter=eyJqb2JfZnVuY3Rpb25zIjpbIkRlc2lnbiJdfQ%3D%3D");
        jobFunctionUrls.put("IT", "https://jobs.techstars.com/jobs?filter=eyJqb2JfZnVuY3Rpb25zIjpbIklUIl19");
        jobFunctionUrls.put("Legal", "https://jobs.techstars.com/jobs?filter=eyJqb2JfZnVuY3Rpb25zIjpbIkxlZ2FsIl19");
        jobFunctionUrls.put("Marketing & Communications", "https://jobs.techstars.com/jobs?filter=eyJqb2JfZnVuY3Rpb25zIjpbIk1hcmtldGluZyUyMCUyNiUyMENvbW11bmljYXRpb25zIl19");
        jobFunctionUrls.put("Operations", "https://jobs.techstars.com/jobs?filter=eyJqb2JfZnVuY3Rpb25zIjpbIk9wZXJhdGlvbnMiXX0%3D");
        jobFunctionUrls.put("Other Engineering", "https://jobs.techstars.com/jobs?filter=eyJqb2JfZnVuY3Rpb25zIjpbIk90aGVyJTIwRW5naW5lZXJpbmciXX0%3D");
        jobFunctionUrls.put("People & HR", "https://jobs.techstars.com/jobs?filter=eyJqb2JfZnVuY3Rpb25zIjpbIlBlb3BsZSUyMCUyNiUyMEhSIl19");
        jobFunctionUrls.put("Product", "https://jobs.techstars.com/jobs?filter=eyJqb2JfZnVuY3Rpb25zIjpbIlByb2R1Y3QiXX0%3D");
        jobFunctionUrls.put("Quality Assurance", "https://jobs.techstars.com/jobs?filter=eyJqb2JfZnVuY3Rpb25zIjpbIlF1YWxpdHklMjBBc3N1cmFuY2UiXX0%3D");
        jobFunctionUrls.put("Sales & Business Development", "https://jobs.techstars.com/jobs?filter=eyJqb2JfZnVuY3Rpb25zIjpbIlNhbGVzJTIwJTI2JTIwQnVzaW5lc3MlMjBEZXZlbG9wbWVudCJdfQ%3D%3D");
        jobFunctionUrls.put("Software Engineering", "https://jobs.techstars.com/jobs?filter=eyJqb2JfZnVuY3Rpb25zIjpbIlNvZnR3YXJlJTIwRW5naW5lZXJpbmciXX0%3D");

        listPageScraperService.scrapeAllJobs(jobFunctionUrls);
    }
}