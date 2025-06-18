package example.dataox.service.scraper;

import example.dataox.entity.ListPage;
import example.dataox.repository.ItemRepository;
import example.dataox.repository.ListPageRepository;
import example.dataox.service.save.ListPageSaveService;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ListPageScraperService {

    private final ItemRepository itemRepository;
    private final ListPageRepository listPageRepository;
    private final ListPageSaveService listPageSaveService;
    private final ItemScraperService itemScraperService;

    private static final String BASE_URL = "https://jobs.techstars.com";

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
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
            driver.get(url);
            Thread.sleep(2000);

            // Принятие куков
            try {
                WebElement acceptCookiesButton = driver.findElement(By.id("onetrust-accept-btn-handler"));
                if (acceptCookiesButton.isDisplayed()) {
                    acceptCookiesButton.click();
                    Thread.sleep(1000);
                }
            } catch (NoSuchElementException ignored) {
            }

            // Получаем количество вакансий
            int expectedJobCount = 0;
            try {
                WebElement showingJobsElement = driver.findElement(By.cssSelector("div.sc-beqWaB.eJrfpP"));
                String showingText = showingJobsElement.getText();
                Matcher matcher = Pattern.compile("(\\d+)\\s+jobs").matcher(showingText);
                if (matcher.find()) {
                    expectedJobCount = Integer.parseInt(matcher.group(1));
                    System.out.println("Ожидаемое количество вакансий: " + expectedJobCount);
                }
            } catch (NoSuchElementException e) {
                System.out.println("Не удалось получить количество вакансий");
            }

            int currentCount = driver.findElements(By.cssSelector("div[itemtype='https://schema.org/JobPosting']")).size();
            loadAllJobs(driver, wait, expectedJobCount);

            // Получаем список jobCards
            List<WebElement> jobCards = driver.findElements(By.cssSelector("div[itemtype='https://schema.org/JobPosting']"));
            for (WebElement jobCard : jobCards) {
                try {
                    WebElement link = jobCard.findElement(By.cssSelector("a[data-testid='job-title-link']"));
                    String jobUrl = link.getAttribute("href");
                    if (jobUrl != null && !jobUrl.isEmpty()) {
                        ListPage listPage = new ListPage();
                        listPage.setJobFunction(jobFunction);
                        listPage.setNumberOfFilteredJobs(expectedJobCount);
                        listPage.setJobPageUrl(jobUrl);

                        List<WebElement> tagDivs = jobCard.findElements(By.cssSelector("div.sc-beqWaB.sc-gueYoa.jIjsZd.MYFxR"));
                        List<String> tagTexts = new ArrayList<>();
                        for (WebElement tagDiv : tagDivs) {
                            try {
                                String text = tagDiv.getText().trim();
                                if (!text.isEmpty()) {
                                    tagTexts.add(text);
                                }
                            } catch (Exception ignored) {
                            }
                        }
                        String tags = String.join(", ", tagTexts);
                        listPage.setTags(tags);

                        listPageSaveService.saveItem(listPage);
                        System.out.println("Сохранено: " + jobUrl);
                    }
                } catch (NoSuchElementException ignored) {
                }
            }
            System.err.println("Обработано " + jobCards.size() + " вакансий для функции: " + jobFunction);
            listPageRepository.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
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
                    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", loadMoreButton);
                    loadMoreButton.click();
                    // Ожидаем появления новых вакансий
                    Thread.sleep(2000);

                    int newCount = driver.findElements(By.cssSelector("div[itemtype='https://schema.org/JobPosting']")).size();

                    // Если новых вакансий не добавилось, увеличиваем попытки, чтобы не зациклиться
                    if (newCount <= currentCount) {
                        attempts++;
                    } else {
                        attempts = 0; // сброс попыток, т.к. загрузились новые вакансии
                    }
                    currentCount = newCount;
                } else {
                    break; // кнопка неактивна или загрузка идет, завершаем
                }
            } catch (NoSuchElementException e) {
                System.out.println("Кнопка 'Load more' не найдена — возможно, все вакансии загружены.");
                break;
            }
        }
    }
}
