package com.jobscraper.services;

import com.jobscraper.controller.ApiResponse;
import com.jobscraper.entity.Item;
import com.jobscraper.entity.ListPage;
import com.jobscraper.repository.ItemRepository;
import com.jobscraper.repository.ListPageRepository;
import com.microsoft.playwright.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.*;

@Service
public class JobDataService {

    private static final String URL_JOBS = "https://api.getro.com/api/v2/collections/89/search/jobs";
    private static final int MAX_SCROLLS = 20;

    private final RestTemplate restTemplate;
    private final ListPageRepository listPageRepository;
    private final ItemRepository itemRepository;

    public JobDataService(RestTemplate restTemplate, ListPageRepository listPageRepository, ItemRepository itemRepository) {
        this.restTemplate = restTemplate;
        this.listPageRepository = listPageRepository;
        this.itemRepository = itemRepository;
    }

    public boolean fetchAndSaveListPagesByIndustryAndPage(String industry, int page) {
        HttpHeaders headers = createHeaders(industry);

        Map<String, Object> filters = new HashMap<>();
        filters.put("job_functions", new String[]{industry});

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("hitsPerPage", 12);
        requestBody.put("page", page);
        requestBody.put("query", "");
        requestBody.put("filters", filters);

        HttpEntity<Map<String, Object>> postRequest = new HttpEntity<>(requestBody, headers);
        ResponseEntity<ApiResponse> responseJobs = restTemplate.exchange(
                URL_JOBS,
                HttpMethod.POST,
                postRequest,
                ApiResponse.class
        );

        ApiResponse apiResponse = responseJobs.getBody();

        if (apiResponse == null || apiResponse.getResults() == null) {
            System.out.println("Нет данных по вакансиям для индустрии " + industry);
            return false;
        }

        List<ApiResponse.Job> jobs = apiResponse.getResults().getJobs();
        if (jobs == null || jobs.isEmpty()) {
            System.out.println("Пустой список вакансий для индустрии " + industry + " на странице " + page);
            return false;
        }

        int savedCount = 0;
        for (ApiResponse.Job job : jobs) {
            if (job == null) {
                System.out.println("Job is null, пропускаем...");
                continue;
            }

            Document doc;
            try {
                doc = Jsoup.connect(jobFunctionUrls().get(industry)).get();
                System.out.println("Загружена страница вакансии: " + job.getUrl());
            } catch (IOException e) {
                System.err.println("Ошибка загрузки страницы: " + job.getUrl() + ", ошибка: " + e.getMessage());
                continue;
            }

            ApiResponse.Organization organization = job.getOrganization();

            ListPage listPage = new ListPage();
            listPage.setJobFunction(industry);
            listPage.setCountJobs(apiResponse.getResults().getCount());
            String url = "https://jobs.techstars.com/companies/" + job.getOrganization().getSlug() + "/jobs/" + job.getSlug();
            listPage.setUrl(url);
            listPage.setTags(getTags(organization, job));

            Item item = new Item();
            item.setPositionName(job.getTitle());
            item.setUrl(job.getUrl());
            item.setLogoUrl(organization.getLogoUrl());
            item.setOrganizationTitle(organization.getName());
            item.setAddress(String.join(", ", job.getSearchableLocations()));
            item.setPostedDate( new Date(job.getCreatedAt() * 1000));

            try {
                doc = Jsoup.connect(url).get();
                if (doc == null) {
                    System.err.println("Не удалось загрузить страницу вакансии: " + url);
                    continue;
                }

                Elements laborFunctions = doc.select("div.sc-beqWaB.bpXRKw");
                if (laborFunctions.size() > 1) {
                    item.setLaborFunction(laborFunctions.get(1).text());
                } else {
                    System.err.println("Не найден элемент с классом 'sc-beqWaB bpXRKw' на странице: " + url);
                    item.setLaborFunction(null);
                }

                Element descriptionElement = doc.selectFirst("div[data-testid=careerPage]");
                if (descriptionElement != null) {
                    item.setDescription(descriptionElement.text().trim().replaceAll("\\s+", " "));
                } else {
                    System.err.println("Не найден элемент с data-testid='careerPage' на странице: " + url);
                    item.setDescription(null);
                }
            } catch (IOException e) {
                throw new NullPointerException();
            }

            listPageRepository.save(listPage);
            itemRepository.save(item);
            savedCount++;
        }

        System.out.println("Сохранено вакансий: " + savedCount + " для индустрии " + industry + ", страница " + page);
        return true;
    }

    private HttpHeaders createHeaders(String refererIndustry) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("accept", "application/json");
        headers.set("accept-language", "uk,ru-UA;q=0.9,ru-RU;q=0.8,ru;q=0.7,en-US;q=0.6,en;q=0.5");
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("dnt", "1");
        headers.set("origin", "https://jobs.techstars.com");
        headers.set("priority", "u=1, i");
        if (refererIndustry != null) {
            headers.set("referer", jobFunctionUrls().getOrDefault(refererIndustry, ""));
        }
        headers.set("sec-ch-ua", "\"Not)A;Brand\";v=\"8\", \"Chromium\";v=\"138\", \"Google Chrome\";v=\"138\"");
        headers.set("sec-ch-ua-mobile", "?0");
        headers.set("sec-ch-ua-platform", "\"macOS\"");
        headers.set("sec-fetch-dest", "empty");
        headers.set("sec-fetch-mode", "cors");
        headers.set("sec-fetch-site", "cross-site");
        headers.set("sec-gpc", "1");
        headers.set("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36");
        return headers;
    }

    public void fetchAndSaveAllListPages() {
        List<String> industries = List.of(
                "Accounting & Finance",
                "Administration",
                "Compliance / Regulatory",
                "Customer Service",
                "Data Science",
                "Design",
                "IT",
                "Legal",
                "Marketing & Communications",
                "Operations",
                "Other Engineering",
                "People & HR",
                "Product",
                "Quality Assurance",
                "Sales & Business Development",
                "Software Engineering"
        );

        for (String industry : industries) {
            int page = 0;
            boolean hasMore = true;

            while (hasMore) {
                hasMore = fetchAndSaveListPagesByIndustryAndPage(industry, page);
                if (hasMore) {
                    page++;
                }
            }
            System.out.println("Данные по индустрии " + industry + " загружены и сохранены!");
        }
    }

    public String getTags(ApiResponse.Organization organization, ApiResponse.Job job) {
        List<String> tags = new ArrayList<>();

        if (organization.getIndustryTags() != null && !organization.getIndustryTags().isEmpty()) {
            tags.addAll(organization.getIndustryTags());
        }

        int count = organization.getHeadCount();
        switch (count) {
            case 1 -> tags.add("1-10 employees");
            case 2 -> tags.add("11-50 employees");
            case 3 -> tags.add("51-200 employees");
            case 4 -> tags.add("201-1000 employees");
            case 5 -> tags.add("1000-5000 employees");
            case 6 -> tags.add("5001+ employees");
        }

        String stage = formatTag(organization.getStage());
        if (!stage.isBlank()) {
            tags.add(stage);
        }

        String seniority = job.getSeniority();
        if (seniority != null && !seniority.isBlank()) {
            tags.add(seniority);
        }

        return String.join(", ", tags);
    }

    public static String formatTag(String tag) {
        if (tag == null || tag.isBlank()) return "";
        tag = tag.replace("_plus", "+");
        String[] parts = tag.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (part.equals("+")) {
                sb.append("+");
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1).toLowerCase())
                    .append(" ");
        }
        String result = sb.toString().trim();
        result = result.replace(" +", "+");
        return result;
    }

    public Map<String, String> jobFunctionUrls() {
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

        return jobFunctionUrls;
    }

    public List<String> laborFunctions() {
        List<String> laborFunctions = List.of(
                "Software Engineering",
                "Sales &amp; Business Development",
                "Operations",
                "IT",
                "Product",
                "Marketing &amp; Communications",
                "Data Science",
                "Design",
                "Customer Service",
                "Accounting &amp; Finance",
                "Other Engineering",
                "Quality Assurance",
                "People &amp; HR",
                "Administration",
                "Legal",
                "Compliance / Regulatory"
        );
        return laborFunctions;
    }
}