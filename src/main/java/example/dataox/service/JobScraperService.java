package example.dataox.service;

import example.dataox.entity.Job;
import example.dataox.repository.JobRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JobScraperService {

    private final JobRepository jobRepository;
    private static final String BASE_URL = "https://jobs.techstars.com";

    @Transactional
    public void scrapeAllJobs(Map<String, String> jobFunctionsAndUrls) {
        jobFunctionsAndUrls.forEach((jobFunction, url) -> {
            try {
                scrapeJobsFromUrl(url, jobFunction);
            } catch (IOException e) {
                System.out.println("Ошибка при парсинге функции: " + jobFunction);
                e.printStackTrace();
            }
        });
    }

    public void scrapeJobsFromUrl(String url, String jobFunction) throws IOException {
        System.out.println("Запускаем парсинг URL: " + url + " для функции: " + jobFunction);

        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(15_000)
                .get();

        // Пытаемся получить количество найденных вакансий
        Element totalJobsElement = doc.selectFirst("div.sc-beqWaB.eJrfpP");
        if (totalJobsElement != null) {
            String text = totalJobsElement.text(); // "Showing 95 jobs"
            String numberStr = text.replaceAll("\\D+", "");
            try {
                int totalJobs = Integer.parseInt(numberStr);
                System.out.println("Всего найдено вакансий: " + totalJobs);
            } catch (NumberFormatException e) {
                System.out.println("Не удалось распарсить количество вакансий: " + text);
            }
        }

        // Парсим карточки вакансий
        Elements jobCards = doc.select("div[itemtype='https://schema.org/JobPosting']");
        System.out.println("Найдено карточек вакансий: " + jobCards.size());

        List<Job> jobs = new ArrayList<>();

        for (Element jobCard : jobCards) {
            Element linkElem = jobCard.selectFirst("a[href*='/jobs/']");
            if (linkElem == null) continue;

            String jobPagePath = linkElem.attr("href");
            String jobPageUrl = jobPagePath.startsWith("http") ? jobPagePath : BASE_URL + jobPagePath;

            try {
                Job job = parseJobDetailPage(jobPageUrl);
                job.setLaborFunction(jobFunction); // устанавливаем вручную
                if (job.getJobPageUrl() == null || job.getJobPageUrl().isEmpty()) continue;
                jobs.add(job);
            } catch (Exception e) {
                System.out.println("Ошибка при парсинге страницы вакансии: " + jobPageUrl);
                e.printStackTrace();
            }
        }

        System.out.println("Сохраняем " + jobs.size() + " вакансий для функции: " + jobFunction);

        try {
            jobRepository.saveAll(jobs);
            jobRepository.flush();
            System.out.println("Сохранено успешно");
        } catch (Exception e) {
            System.out.println("Ошибка при сохранении:");
            e.printStackTrace();
        }
    }

    private Job parseJobDetailPage(String jobPageUrl) throws IOException {
        Document jobDoc = Jsoup.connect(jobPageUrl)
                .userAgent("Mozilla/5.0")
                .timeout(10_000)
                .get();

        Job job = new Job();
        job.setJobPageUrl(jobPageUrl); // Обязательное поле

        // 1 position name
        Element name = jobDoc.selectFirst("div[class=jqWDOR]");
        job.setPositionName(name != null ? name.text() : "Unknown Position");
        System.out.println("Парсим вакансию: " + job.getPositionName() + " по URL: " + jobPageUrl);

        // 2 url to organization
        Element orgLink = jobDoc.selectFirst("a.bpnNuw");
        String href = orgLink.attr("href");
        job.setOrganizationUrl((href != null && !href.isEmpty())
                ? (href.startsWith("http") ? href : BASE_URL + href)
                : "");
        job.setOrganizationTitle(orgLink.text());
        System.out.println("Организация: " + job.getOrganizationTitle() + ", URL: " + job.getOrganizationUrl());

        // 3 logo
        Element logo = jobDoc.selectFirst("img[class=sc-dmqHEX eTCoCQ]");
        job.setLogoUrl(logo != null ? logo.attr("src") : "");
        System.out.println("Логотип: " + job.getLogoUrl());

        // 4 organization title
        Element title = jobDoc.selectFirst("p[class=sc-beqWaB bpXRKw]");
        job.setOrganizationTitle(title != null ? title.text() : "");
        System.out.println("Название организации: " + job.getOrganizationTitle());

        // 5 labor function
        Elements infoBlocks = jobDoc.select("div[class=sc-beqWaB bpXRKw]");
        System.out.println("Найдено блоков информации: " + infoBlocks.size());
        if (infoBlocks.size() > 4) {
            Element third = infoBlocks.get(3);  // laborFunction
            Element fourth = infoBlocks.get(4); // location
            job.setLaborFunction(third.text());
            job.setLocation(fourth.text());
            System.out.println("Локация: " + job.getLocation() + ", Функция: " + job.getLaborFunction());
        } else {
            System.out.println("Не удалось найти достаточное количество блоков для локации и функции.");
            job.setLaborFunction("");
            job.setLocation("");
        }

        // 7 posted date
        Element postedDate = jobDoc.selectFirst("div[class=gRXpLa]");
        if (postedDate != null) {
            String postedText = postedDate.text();
            String dateStr = postedText.replace("Posted on ", "").trim();

            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.ENGLISH);
                LocalDate localDate = LocalDate.parse(dateStr, formatter);
                long timestamp = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                job.setPostedDate(timestamp);
            } catch (Exception e) {
                job.setPostedDate(System.currentTimeMillis());
            }
        } else {
            job.setPostedDate(System.currentTimeMillis());
        }
        System.out.println("Дата публикации: " + job.getPostedDate());


        // 8 description
        Element desc = jobDoc.selectFirst("div[data-testid=careerPage]");
        job.setDescription(desc != null ? desc.html() : "");
        System.out.println("Описание вакансии: " + desc);

        return job;
    }
}