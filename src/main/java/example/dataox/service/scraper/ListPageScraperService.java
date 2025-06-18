package example.dataox.service.scraper;

import example.dataox.entity.ListPage;
import example.dataox.repository.ItemRepository;
import example.dataox.repository.ListPageRepository;
import example.dataox.service.save.ListPageSaveService;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    public int getExpectedJobCountFromListPage(String url) {
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        int count = 0;
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
            driver.get(url);

            try {
                WebElement acceptCookiesButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("onetrust-accept-btn-handler")));
                Thread.sleep(1000);
                acceptCookiesButton.click();
                wait.until(ExpectedConditions.invisibilityOf(acceptCookiesButton));
            } catch (TimeoutException ignored) {
            }

            try {
                WebElement showingJobsElement = driver.findElement(By.cssSelector("div.sc-beqWaB.eJrfpP"));
                String showingText = showingJobsElement.getText();
                Matcher matcher = Pattern.compile("(\\d+)\\s+jobs").matcher(showingText);
                if (matcher.find()) {
                    count = Integer.parseInt(matcher.group(1));
                }
            } catch (NoSuchElementException e) {
                System.out.println("Не удалось получить количество вакансий");
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
        return count;
    }

    public void processByJobFunction(String url, String jobFunction) {
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
            driver.get(url);

            // Куки через wait
            acceptCookies(wait);

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

            // Загружаем все вакансии
            loadAllJobs(driver, wait, expectedJobCount);

            // Получаем список вакансий
            List<WebElement> jobCards = driver.findElements(By.cssSelector("div[itemtype='https://schema.org/JobPosting']"));
            boolean countSaved = false;

            for (WebElement jobCard : jobCards) {
                try {
                    WebElement link = jobCard.findElement(By.cssSelector("a[data-testid='job-title-link']"));
                    String jobUrl = link.getAttribute("href");
                    if (jobUrl != null && !jobUrl.isEmpty()) {
                        ListPage listPage = new ListPage();
                        listPage.setJobFunction(jobFunction);
                        listPage.setJobPageUrl(jobUrl);

                        if (!countSaved) {
                            listPage.setNumberOfFilteredJobs(expectedJobCount == 0 ? jobCards.size() : expectedJobCount);
                            countSaved = true;
                        }

                        List<String> tagTexts = jobCard.findElements(By.cssSelector("div.sc-beqWaB.sc-gueYoa.jIjsZd.MYFxR")).stream()
                                .map(WebElement::getText)
                                .map(text -> text.replace("\n", " ").trim())
                                .filter(text -> !text.isEmpty())
                                .collect(Collectors.toList());

                        listPage.setTags(String.join(", ", tagTexts));
                        listPageSaveService.saveItem(listPage);
                        System.out.println("Сохранено: " + jobUrl);
                    }
                } catch (NoSuchElementException ignored) {
                }
            }
            System.err.println("Обработано " + jobCards.size() + " из " + expectedJobCount + " вакансий для функции: " + jobFunction);
            listPageRepository.updateNumberOfFilteredJobsByJobFunction(jobFunction, expectedJobCount == 0 ? jobCards.size() : expectedJobCount);
            listPageRepository.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
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
}
