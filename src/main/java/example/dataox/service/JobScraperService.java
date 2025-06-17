package example.dataox.service;

import example.dataox.entity.Job;
import example.dataox.repository.JobRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobScraperService {

    private final JobRepository jobRepository;
    private final JobSaveService jobSaveService;
    private static final String BASE_URL = "https://jobs.techstars.com";

    @Transactional
    public void scrapeAllJobs(Map<String, String> jobFunctionsAndUrls) {
        for (Map.Entry<String, String> entry : jobFunctionsAndUrls.entrySet()) {
            try {
                scrapeJobsFromUrl(entry.getValue(), entry.getKey());
            } catch (Exception e) {
                System.out.println("Ошибка при парсинге функции: " + entry.getKey());
                e.printStackTrace();
            }
        }
    }

    public void scrapeJobsFromUrl(String url, String jobFunction) {
        System.err.println("Начинаем парсинг направления: " + jobFunction);

        ChromeOptions options = new ChromeOptions();
        // Устанавливаем User-Agent как у обычного браузера (пример для Chrome)
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/114.0.0.0 Safari/537.36");

        // Запускаем без headless (если хочешь, можно включить, но сейчас лучше выключить)
        options.addArguments("--headless=new");

        // Отключаем GPU и Sandbox (если нужно)
        options.addArguments("--disable-gpu", "--no-sandbox");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(10));
        List<Job> jobs = new ArrayList<>();

        try {
            driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(5));
            driver.get(url);

            int expectedJobCount = 0;
            try {
                WebElement showingJobsElement = driver.findElement(By.cssSelector("div.sc-beqWaB.eJrfpP"));
                String showingText = showingJobsElement.getText();
                System.err.println("Текст с количеством вакансий: " + showingText);

                Matcher matcher = java.util.regex.Pattern.compile("(\\d+)\\s+jobs").matcher(showingText);
                if (matcher.find()) {
                    expectedJobCount = Integer.parseInt(matcher.group(1));
                    System.out.println("Ожидаемое количество вакансий: " + expectedJobCount);
                } else {
                    System.out.println("Не удалось извлечь количество из текста: " + showingText);
                }

            } catch (NoSuchElementException e) {
                System.out.println("Элемент с количеством вакансий не найден");
            }

            while (true) {
                List<WebElement> jobCards = driver.findElements(By.cssSelector("div[itemtype='https://schema.org/JobPosting']"));
                int currentCount = jobCards.size();

                if (expectedJobCount > 0 && currentCount >= expectedJobCount) {
                    break;
                }

                try {
                    WebElement loadMoreButton = driver.findElement(By.cssSelector("button[data-testid='load-more']"));
                    String loading = loadMoreButton.getAttribute("data-loading");

                    if ("false".equals(loading)) {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", loadMoreButton);
                        loadMoreButton.click();
                        Thread.sleep(3000);
                        while (expectedJobCount != currentCount) {
                            jobCards = driver.findElements(By.cssSelector("div[itemtype='https://schema.org/JobPosting']"));
                            currentCount = jobCards.size();
                            ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight);");
                            System.err.println(currentCount);
                            Thread.sleep(1500);
                        }
                    } else {
                        Thread.sleep(1000);
                    }
                } catch (NoSuchElementException e) {
                    System.out.println("Кнопка 'Load more' не найдена — возможно, всё загружено.");
                    break;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            List<WebElement> finalJobCards = driver.findElements(By.cssSelector("div[itemtype='https://schema.org/JobPosting']"));
            System.out.println("Итоговое количество карточек: " + finalJobCards.size());

            // Собираем ссылки на вакансии
            List<WebElement> jobCards = driver.findElements(By.cssSelector("div[itemtype='https://schema.org/JobPosting']"));
            List<String> jobUrls = new ArrayList<>();
            for (WebElement jobCard : jobCards) {
                try {
                    // Ищем ссылку на вакансию по data-testid внутри карточки
                    WebElement link = jobCard.findElement(By.cssSelector("a[data-testid='job-title-link']"));
                    String jobUrl = link.getAttribute("href");
                    if (jobUrl != null && !jobUrl.isEmpty()) {
                        jobUrls.add(jobUrl);
                    } else {
                        System.out.println("Ссылка пуста, пропускаем карточку.");
                    }
                } catch (NoSuchElementException e) {
                    System.out.println("Карточка не содержит ссылку на вакансию, пропускаем.");
                    System.out.println("Текст карточки: " + jobCard.getText().substring(0, Math.min(jobCard.getText().length(), 100)) + "...");
                }
            }

            // Парсим каждую вакансию по отдельности
            //for (String jobUrl : jobUrls) {
            for (int i = 0; i < 1 && i < jobUrls.size(); i++) {
                String jobUrl = jobUrls.get(i); //
                try {
                    driver.get(jobUrl);
                    wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("h2.jqWDOR")));
                    Thread.sleep(1000);

                    Job job = parseJobDetailPage(jobUrl, driver);
                    if (job != null) {
                        jobs.add(job);
                        jobSaveService.saveJob(job);
                    }
                } catch (Exception ex) {
                    System.out.println("Ошибка при парсинге вакансии: " + jobUrl);
                }
            }
            jobRepository.flush();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }

    private Job parseJobDetailPage(String jobPageUrl, WebDriver driver) {
        System.out.println("Парсим вакансию по URL: " + jobPageUrl);
        Job job = new Job();
        job.setJobPageUrl(jobPageUrl);
        driver.get(jobPageUrl);

        Document jobDoc;
        try {
            jobDoc = Jsoup.connect(jobPageUrl)
                    .userAgent("Mozilla/5.0")
                    .timeout(15_000)
                    .get();
        } catch (Exception e) {
            System.out.println("Ошибка загрузки страницы: " + jobPageUrl);
            e.printStackTrace();
            return null;
        }

        try {
            // 1 position name
            Element name = jobDoc.selectFirst("h2.jqWDOR");
            job.setPositionName(name != null ? name.text() : "Unknown Position");
            //System.err.println("position name: " + job.getPositionName());

            // 2 url to organization
            Element orgLink = jobDoc.selectFirst("a.bpnNuw");
            // 4 organization title
            Element title = jobDoc.selectFirst("p.sc-beqWaB.bpXRKw");
            if (orgLink != null) {
                String href = orgLink.attr("href");
                job.setOrganizationUrl((href != null && !href.isEmpty()) ? (href.startsWith("http") ? href : BASE_URL + href) : "");
                job.setOrganizationTitle((title != null) ? title.text() : "Unknown Organization");
            } else {
                job.setOrganizationTitle("");
                job.setOrganizationUrl("");
            }
            //System.err.println("organization url: " + job.getOrganizationUrl());
            //System.err.println("organization title: " + job.getOrganizationTitle());

            // 3 logo
            Element logo = jobDoc.selectFirst("img.sc-dmqHEX.eTCoCQ");
            job.setLogoUrl(logo != null ? logo.attr("src") : "");
            //System.err.println("logo: " + job.getLogoUrl());

            // 5 labor functions
            List<String> keywords = List.of(
                    "Accounting & Finance", "Administration", "Compliance / Regulatory", "Customer Service",
                    "Data Science", "Design", "IT", "Legal", "Marketing & Communications", "Operations",
                    "Other Engineering", "People & HR", "Product", "Quality Assurance",
                    "Sales & Business Development", "Software Engineering"
            );
            List<WebElement> blocks = driver.findElements(By.cssSelector("div.sc-beqWaB.sc-gueYoa.dmdAKU.MYFxR"));
            for (WebElement block : blocks) {
                String[] lines = block.getText().trim().split("\n");
                String directionLine = null;
                for (String line : lines) {
                    for (String keyword : keywords) {
                        if (line.contains(keyword)) {
                            directionLine = line;
                            break;
                        }
                    }
                    if (directionLine != null) break;
                }
                if (directionLine != null) {
//                    System.out.println("=== НАЙДЕНО НАПРАВЛЕНИЕ ===");
//                    System.out.println(directionLine);
//                    System.out.println("===========================");
                    job.setLaborFunction(directionLine);
                }
            }

            // 6 address
            List<String> knownLocations = List.of(
                    "United States", "USA", "Canada", "United Kingdom", "UK", "Germany", "France",
                    "Luxembourg", "Spain", "Italy", "Netherlands", "Belgium", "Sweden", "Norway", "Denmark",
                    "Finland", "Poland", "Czech Republic", "Slovakia", "Hungary", "Austria",
                    "Switzerland", "Ireland", "Portugal", "Greece", "Turkey", "Russia", "Ukraine",
                    "China", "India", "Japan", "South Korea", "Australia", "New Zealand",
                    "Brazil", "Mexico", "Argentina", "Chile", "South Africa",
                    "Europe", "Asia", "North America", "South America", "Multiple locations"
            );
            blocks.clear();
            blocks = driver.findElements(By.cssSelector("div.sc-beqWaB.sc-gueYoa.dmdAKU.MYFxR"));
            String location = null;
            for (WebElement block : blocks) {
                String[] lines = block.getText().trim().split("\n");
                for (String line : lines) {
                    for (String keyword : knownLocations) {
                        if (line.contains(keyword)) {
                            location = line;
                            break;
                        }
                    }
                    if (location != null) break;
                }
                if (location != null) break;
            }

            if (location != null) {
//                System.out.println("=== НАЙДЕНА ЛОКАЦИЯ ===");
//                System.out.println(location);
//                System.out.println("=======================");
                job.setLocation(location);
            } else {
                //System.out.println("Локация не найдена");
            }


            // 7 posted date
            Element postedDateElement = jobDoc.selectFirst("div.sc-beqWaB.gRXpLa");
            if (postedDateElement != null) {
                String postedDateText = postedDateElement.text();
                String dateString = postedDateText.replace("Posted on ", "").trim();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
                LocalDate localDate = LocalDate.parse(dateString, formatter);
                Date date = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

                job.setPostedDate(date);
            } else {
                job.setPostedDate(new Date());
            }
            //System.out.println("Date from timestamp: " + job.getPostedDate());

            // 8 description
            Element desc = jobDoc.selectFirst("div.sc-beqWaB.fmCCHr");
            String descriptionText = desc != null ? desc.text() : null;
            //System.out.println("Parsed description: " + descriptionText);
            job.setDescription(descriptionText);
        } catch (Exception ex) {
            System.out.println("Ошибка при парсинге страницы вакансии: " + jobPageUrl);
            ex.printStackTrace();
            return null;
        }

        return job;
    }
}