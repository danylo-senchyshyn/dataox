package example.dataox.service;

import example.dataox.entity.Item;
import example.dataox.entity.ListPage;
import example.dataox.repository.ItemRepository;
import example.dataox.repository.ListPageRepository;
import jakarta.transaction.Transactional; // заменим ниже
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ListPageScraperService {

    private final ItemRepository itemRepository;
    private final ItemScraperService itemScraperService; // Оставил один сервис
    private final ListPageRepository listPageRepository;
    private final JdbcTemplate jdbcTemplate;

    private static final String BASE_URL = "https://jobs.techstars.com";

    @Transactional()
    public void scrapeAllJobs(Map<String, String> jobFunctionsAndUrls) {
        for (Map.Entry<String, String> entry : jobFunctionsAndUrls.entrySet()) {
            try {
                processByJobFunction(entry.getKey());  // передаём ключ - название функции, не url
            } catch (Exception e) {
                System.out.println("Ошибка при парсинге функции: " + entry.getKey());
                e.printStackTrace();
            }
        }
    }

    public void processByJobFunction(String jobFunction) {
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        try {
            driver.get(BASE_URL + "/jobs");

            // Открываем dropdown "Job function"
            WebElement jobFunctionDropdown = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//div[contains(text(), 'Job function')]")));
            jobFunctionDropdown.click();

            // Выбираем нужный jobFunction из списка
            WebElement desiredFunction = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//span[contains(text(), '" + jobFunction + "')]")));
            desiredFunction.click();

            // Ждем обновления количества вакансий (элемент с текстом "Showing ... jobs")
            WebElement showingJobsElement = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//*[contains(text(), 'Showing')]")));

            String jobCountText = showingJobsElement.getText();
            int jobCount = extractNumberFromText(jobCountText);

            // Ждем появления карточек вакансий
            List<WebElement> jobCards = wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(
                    By.cssSelector("div.sc-beqWaB"))); // уточнить селектор при необходимости

            List<Long> itemIds = new ArrayList<>();
            for (WebElement card : jobCards) {
                try {
                    WebElement readMoreLink = card.findElement(By.xpath(".//a[contains(text(), 'Read more')]"));
                    String jobUrl = readMoreLink.getAttribute("href");

                    if (jobUrl != null && !jobUrl.isEmpty()) {
                        Item item = itemScraperService.parseJobDetailPage(jobUrl, driver);
                        if (item != null) {
                            itemIds.add(item.getId());
                        }
                    }
                } catch (NoSuchElementException e) {
                    System.out.println("Ссылка 'Read more' не найдена в карточке, пропускаем.");
                } catch (Exception e) {
                    System.out.println("Ошибка при парсинге вакансии из карточки: " + e.getMessage());
                }
            }

            // Сохраняем ListPage
            ListPage listPage = new ListPage();
            listPage.setJobFunction(jobFunction);
            listPage.setNumberOfFilteredJobs(jobCount);
            listPageRepository.save(listPage);

            // Связываем Item с ListPage через jdbcTemplate
            for (Long itemId : itemIds) {
                jdbcTemplate.update(
                        "INSERT INTO item_job_link (item_id, list_page_id) VALUES (?, ?)",
                        itemId, listPage.getId());
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }

    private int extractNumberFromText(String text) {
        return Integer.parseInt(text.replaceAll("[^\\d]", ""));
    }
}