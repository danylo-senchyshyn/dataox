package example.dataox.service.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import example.dataox.entity.ListPage;
import example.dataox.repository.ItemRepository;
import example.dataox.repository.ListPageRepository;
import example.dataox.service.save.ListPageSaveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListPageScraperService {

    private final ItemRepository itemRepository;
    private final ListPageRepository listPageRepository;
    private final ListPageSaveService listPageSaveService;
    private final ItemScraperService itemScraperService;

    public void scrapeAllJobs(Map<String, String> jobFunctionsAndUrls) {
        for (Map.Entry<String, String> entry : jobFunctionsAndUrls.entrySet()) {
            try {
                processByJobFunction(entry.getValue(), entry.getKey());  // передаём ключ - название функции, не url
            } catch (Exception e) {
                System.out.println("Ошибка при парсинге функции: " + entry.getKey());
                e.printStackTrace();
            }
        }
    }

    public void processByJobFunction(String url, String jobFunction) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--user-data-dir=/tmp/chrome-profile-" + System.currentTimeMillis());

        options.addArguments("--headless=new"); // или просто "--headless"
        options.addArguments("--disable-gpu"); // на всякий случай

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
            driver.get(url);

            acceptCookies(wait);

            // Определяем ожидаемое количество вакансий
            int expectedJobCount = extractExpectedJobCount(driver);

            // Загружаем все вакансии на странице
            loadAllJobs(driver, wait, expectedJobCount);

            List<WebElement> jobCards = driver.findElements(By.cssSelector("div[itemtype='https://schema.org/JobPosting']"));
            int savedCount = 0;

            for (WebElement jobCard : jobCards) {
                try {
                    WebElement link = jobCard.findElement(By.cssSelector("a[data-testid='job-title-link']"));
                    String jobUrl = link.getAttribute("href");

                    if (jobUrl != null && !jobUrl.isEmpty()) {
                        if (listPageRepository.existsByJobPageUrlAndJobFunction(jobUrl, jobFunction)) {
                            continue;
                        }

                        List<String> tagTexts = jobCard.findElements(By.cssSelector("div.sc-beqWaB.sc-gueYoa.jIjsZd.MYFxR")).stream()
                                .map(WebElement::getText)
                                .map(text -> text.replace("\n", " ").trim())
                                .filter(text -> !text.isEmpty())
                                .collect(Collectors.toList());

                        ListPage listPage = new ListPage();
                        listPage.setJobFunction(jobFunction);
                        listPage.setJobPageUrl(jobUrl);
                        listPage.setNumberOfFilteredJobs(expectedJobCount == 0 ? jobCards.size() : expectedJobCount);
                        listPage.setTags(String.join(", ", tagTexts));

                        listPageSaveService.saveItem(listPage);
                        savedCount++;
                        log.info("Сохранена вакансия: {}", jobUrl);
                    }
                } catch (NoSuchElementException e) {
                    log.warn("Не удалось найти ссылку на вакансию в карточке", e);
                }
            }

            int totalJobs = expectedJobCount == 0 ? jobCards.size() : expectedJobCount;
            listPageRepository.updateNumberOfFilteredJobsByJobFunction(jobFunction, totalJobs);
            listPageRepository.flush();

            log.info("Обработано {} вакансий, сохранено {} для направления: {}", jobCards.size(), savedCount, jobFunction);
        } catch (Exception e) {
            log.error("Ошибка при обработке направления: " + jobFunction, e);
        } finally {
            driver.quit();
        }
    }

    private int extractExpectedJobCount(WebDriver driver) {
        try {
            WebElement showingJobsElement = driver.findElement(By.cssSelector("div.sc-beqWaB.eJrfpP"));
            String showingText = showingJobsElement.getText();
            Matcher matcher = Pattern.compile("(\\d+)\\s+jobs").matcher(showingText);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (NoSuchElementException e) {
            log.warn("Не удалось получить количество вакансий", e);
        }
        return 0;
    }

    private void acceptCookies(WebDriverWait wait) {
        try {
            WebElement acceptCookiesButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("onetrust-accept-btn-handler")));
            Thread.sleep(1000);
            acceptCookiesButton.click();
            wait.until(ExpectedConditions.invisibilityOf(acceptCookiesButton));
        } catch (TimeoutException ignored) {
        } catch (InterruptedException e) {
            System.out.println("Cookie banner accepted");
            Thread.currentThread().interrupt();
        }
    }

    private void loadAllJobs(WebDriver driver, WebDriverWait wait, int expectedJobCount) throws InterruptedException {
        int currentCount = driver.findElements(By.cssSelector("div[itemtype='https://schema.org/JobPosting']")).size();
        int maxAttempts = 5;
        int attempts = 0;

        while (currentCount < expectedJobCount && attempts < maxAttempts) {
            try {
                WebElement loadMoreButton = driver.findElement(By.cssSelector("button[data-testid='load-more']"));
                if (loadMoreButton.isDisplayed() && "false".equals(loadMoreButton.getAttribute("data-loading"))) {
                    for (int i = 0; i < 2; i++) {
                        currentCount = driver.findElements(By.cssSelector("div[itemtype='https://schema.org/JobPosting']")).size();
                        if (currentCount == expectedJobCount) break;
                        ((JavascriptExecutor) driver).executeScript("location.reload();");

                        loadMoreButton = driver.findElement(By.cssSelector("button[data-testid='load-more']"));
                        if (loadMoreButton.isDisplayed()) {
                            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", loadMoreButton);
                            loadMoreButton.click();
                        }
                        for (int j = 0; j < 15; j++) {
                            currentCount = driver.findElements(By.cssSelector("div[itemtype='https://schema.org/JobPosting']")).size();
                            if (currentCount == expectedJobCount) break;

                            WebElement footer = driver.findElement(By.cssSelector(
                                    "div.sc-beqWaB.sc-gueYoa.kVZzjT.MYFxR.powered-by-footer"));
                            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({behavior: 'smooth'});", footer);
                            wait.until(ExpectedConditions.invisibilityOfElementLocated(
                                    By.cssSelector("div.sc-beqWaB.kEdPIN")
                            ));
                        }
                    }

                    int newCount = driver.findElements(
                            By.cssSelector("div[itemtype='https://schema.org/JobPosting']")).size();

                    if (newCount <= currentCount) {
                        attempts++;
                    } else {
                        attempts = 0;
                    }
                    currentCount = newCount;
                } else {
                    break;
                }
            } catch (NoSuchElementException e) {
                System.out.println("Кнопка 'Load more' не найдена — возможно, все вакансии загружены.");
                break;
            } catch (TimeoutException e) {
                System.out.println("Превышено время ожидания загрузки новых вакансий.");
                ((JavascriptExecutor) driver).executeScript("location.reload();");
                attempts++;
            } catch (StaleElementReferenceException e) {
                System.out.println("Элемент стал неактуальным — повтор попытки.");
                attempts++;
            }
            System.err.println("Текущее количество вакансий: " + currentCount + ", ожидаемое: " + expectedJobCount);
        }
    }

    public static JsonNode fetchJobsFromApi(String jobFunction, int limit, int page) throws Exception {
        String url = "https://api.getro.com/api/v2/collections/89/search/jobs";

        String jsonBody = String.format(
                "{ \"hitsPerPage\": %d, \"page\": %d, \"filters\": { \"job_functions\": [\"%s\"] }, \"query\": \"\" }",
                limit, page, jobFunction
        );

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Origin", "https://jobs.techstars.com")
                .header("Referer", "https://jobs.techstars.com")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();

        System.out.println("Ответ API: " + responseBody);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(responseBody);

        if (root.has("jobs")) {
            return root.get("jobs");
        } else {
            System.err.println("В ответе API отсутствует поле 'jobs'");
            System.err.println("Полный ответ:\n" + root.toPrettyString());
            return null;
        }
    }
}
