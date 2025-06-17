package example.dataox.service;

import example.dataox.entity.Item;
import example.dataox.entity.ListPage;
import example.dataox.repository.ItemRepository;
import example.dataox.repository.ListPageRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
import java.util.regex.Matcher;

@Service
@RequiredArgsConstructor
public class ItemScraperService {

    private final ItemRepository itemRepository;
    private final ItemSaveService itemSaveService;
    private final ListPageRepository listPageRepository;
    private static final String BASE_URL = "https://jobs.techstars.com";

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
        System.out.println("Парсим направление: " + jobFunction);

        ChromeOptions options = new ChromeOptions();
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/114.0.0.0 Safari/537.36");
        options.addArguments("--disable-gpu", "--no-sandbox");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(10));

        try {
            driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(5));
            driver.get(url);
            Thread.sleep(2000);

            // Принятие куков
            try {
                WebElement acceptCookiesButton = driver.findElement(By.id("onetrust-accept-btn-handler"));
                if (acceptCookiesButton.isDisplayed()) {
                    acceptCookiesButton.click();
                    System.out.println("Куки приняты.");
                    Thread.sleep(1000);
                }
            } catch (NoSuchElementException ignored) {}

            // Получаем количество вакансий
            int expectedJobCount = 0;
            try {
                WebElement showingJobsElement = driver.findElement(By.cssSelector("div.sc-beqWaB.eJrfpP"));
                String showingText = showingJobsElement.getText();
                Matcher matcher = java.util.regex.Pattern.compile("(\\d+)\\s+jobs").matcher(showingText);
                if (matcher.find()) {
                    expectedJobCount = Integer.parseInt(matcher.group(1));
                    System.out.println("Ожидаемое количество вакансий: " + expectedJobCount);
                }
            } catch (NoSuchElementException e) {
                System.out.println("Не удалось получить количество вакансий");
            }

            // Загружаем все вакансии (через кнопку Load More)
            int currentCount = driver.findElements(By.cssSelector("div[itemtype='https://schema.org/JobPosting']")).size();
            int attempts = 0;
            int maxAttempts = 5;
            try {
                while (currentCount < expectedJobCount && attempts < maxAttempts) {
                    try {
                        WebElement loadMoreButton = driver.findElement(By.cssSelector("button[data-testid='load-more']"));
                        String loading = loadMoreButton.getAttribute("data-loading");

                        if ("false".equals(loading)) {
                            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", loadMoreButton);
                            loadMoreButton.click();
                            Thread.sleep(1500);

                            for (int j = 0; j < 10; j++) {
                                List<WebElement> jobCards = driver.findElements(By.cssSelector("div[itemtype='https://schema.org/JobPosting']"));
                                currentCount = jobCards.size();
                                System.err.println("Current loaded: " + currentCount);

                                ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight);");
                                ((JavascriptExecutor) driver).executeScript("window.scrollBy(0, -300);");

                                if (currentCount >= expectedJobCount) break;
                                Thread.sleep(2000);
                            }

                            // Перезагрузка страницы
                            ((JavascriptExecutor) driver).executeScript("location.reload();");
                            Thread.sleep(3000); // подождать пока страница перезагрузится

                            // Обновить счётчики и попытки
                            currentCount = driver.findElements(By.cssSelector("div[itemtype='https://schema.org/JobPosting']")).size();
                            attempts++;
                        }

                    } catch (NoSuchElementException e) {
                        System.out.println("Кнопка 'Load more' не найдена — возможно, всё загружено.");
                        break;
                    }
                }

            } catch (NoSuchElementException e) {
                System.out.println("Кнопка 'Load more' не найдена — возможно, всё загружено.");
            }

            // Собираем ссылки на вакансии
            List<WebElement> jobCards = driver.findElements(By.cssSelector("div[itemtype='https://schema.org/JobPosting']"));
            System.out.println("Найдено вакансий: " + jobCards.size());

            for (WebElement jobCard : jobCards) {
                try {
                    WebElement link = jobCard.findElement(By.cssSelector("a[data-testid='job-title-link']"));
                    String jobUrl = link.getAttribute("href");
                    if (jobUrl != null && !jobUrl.isEmpty()) {
                        // Проверяем есть ли уже в базе, чтобы не дублировать
                        if (listPageRepository.findByJobPageUrl(jobUrl).isEmpty()) {
                            ListPage listPage = new ListPage();
                            listPage.setJobFunction(jobFunction);
                            listPage.setJobPageUrl(jobUrl);
                            listPage.setNumberOfFilteredJobs(expectedJobCount);
                            // Можно подставить теги, если есть (например пусто)
                            listPage.setTags("");
                            listPageRepository.save(listPage);
                        }
                    }
                } catch (NoSuchElementException ignored) {}
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }

    // Можно добавить метод для запуска парсинга деталей из ListPage
    public void scrapeJobDetailsFromListPage() {
        List<ListPage> pages = listPageRepository.findAll();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/114.0.0.0 Safari/537.36");
        options.addArguments("--disable-gpu", "--no-sandbox");
        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(10));

        try {
            for (ListPage page : pages) {
                try {
                    String jobUrl = page.getJobPageUrl();
                    driver.get(jobUrl);
                    wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("h2.jqWDOR")));
                    Thread.sleep(1000);
                    Item item = parseJobDetailPage(jobUrl, driver);
                    if (item != null) {
                        itemSaveService.saveItem(item);
                    }
                } catch (Exception ex) {
                    System.out.println("Ошибка при парсинге вакансии: " + page.getJobPageUrl());
                }
            }
            itemRepository.flush();
        } finally {
            driver.quit();
        }
    }

    public Item parseJobDetailPage(String jobPageUrl, WebDriver driver) {
        System.out.println("Парсим вакансию: " + jobPageUrl);
        Item item = new Item();
        item.setJobPageUrl(jobPageUrl);

        Document jobDoc;
        try {
            jobDoc = Jsoup.connect(jobPageUrl)
                    .userAgent("Mozilla/5.0")
                    .timeout(15_000)
                    .get();
        } catch (Exception e) {
            System.out.println("Ошибка загрузки страницы вакансии");
            e.printStackTrace();
            return null;
        }

        try {
            // 1. position name
            Element name = jobDoc.selectFirst("h2.jqWDOR");
            item.setPositionName(name != null ? name.text() : "Unknown Position");

            // 2. url to organization и 4. organization title
            Element orgLink = jobDoc.selectFirst("a.bpnNuw");
            Element title = jobDoc.selectFirst("p.sc-beqWaB.bpXRKw");
            if (orgLink != null) {
                String href = orgLink.attr("href");
                item.setOrganizationUrl(href.startsWith("http") ? href : BASE_URL + href);
                item.setOrganizationTitle(title != null ? title.text() : "Unknown Organization");
            } else {
                item.setOrganizationTitle("");
                item.setOrganizationUrl("");
            }

            // 3. logo
            Element logo = jobDoc.selectFirst("img.sc-dmqHEX.eTCoCQ");
            item.setLogoUrl(logo != null ? logo.attr("src") : "");

            // 5. labor function
            List<String> laborFunctionsKeywords = List.of(
                    "Accounting & Finance", "Administration", "Compliance / Regulatory", "Customer Service",
                    "Data Science", "Design", "IT", "Legal", "Marketing & Communications", "Operations",
                    "Other Engineering", "People & HR", "Product", "Quality Assurance",
                    "Sales & Business Development", "Software Engineering"
            );
            String laborFunction = null;
            for (Element block : jobDoc.select("div.sc-beqWaB.sc-gueYoa.dmdAKU.MYFxR")) {
                String text = block.text();
                for (String keyword : laborFunctionsKeywords) {
                    if (text.contains(keyword)) {
                        laborFunction = keyword;
                        break;
                    }
                }
                if (laborFunction != null) break;
            }
            item.setLaborFunction(laborFunction != null ? laborFunction : "");

            // 6. address
            List<String> knownLocations = List.of(
                    "United States", "USA", "Canada", "United Kingdom", "UK", "Germany", "France",
                    "Luxembourg", "Spain", "Italy", "Netherlands", "Belgium", "Sweden", "Norway", "Denmark",
                    "Finland", "Poland", "Czech Republic", "Slovakia", "Hungary", "Austria",
                    "Switzerland", "Ireland", "Portugal", "Greece", "Turkey", "Russia", "Ukraine",
                    "China", "India", "Japan", "South Korea", "Australia", "New Zealand",
                    "Brazil", "Mexico", "Argentina", "Chile", "South Africa",
                    "Europe", "Asia", "North America", "South America", "Multiple locations"
            );
            String location = null;
            for (Element block : jobDoc.select("div.sc-beqWaB.sc-gueYoa.dmdAKU.MYFxR")) {
                String text = block.text();
                for (String loc : knownLocations) {
                    if (text.contains(loc)) {
                        location = loc;
                        break;
                    }
                }
                if (location != null) break;
            }
            item.setLocation(location != null ? location : "");

            // 7. posted date
            Element postedDateElement = jobDoc.selectFirst("div.sc-beqWaB.gRXpLa");
            if (postedDateElement != null) {
                String postedDateText = postedDateElement.text().replace("Posted on ", "").trim();
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
                    LocalDate localDate = LocalDate.parse(postedDateText, formatter);
                    item.setPostedDate(Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));
                } catch (Exception ex) {
                    System.out.println("Не удалось распарсить дату публикации: " + postedDateText);
                    item.setPostedDate(new Date());
                }
            }

            // 8. description (вся текстовая часть)
            Element descElement = jobDoc.selectFirst("div.job-description");
            if (descElement == null) descElement = jobDoc.selectFirst("section.sc-htpNat.ijOyR");
            item.setDescription(descElement != null ? descElement.text() : "");

            return item;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}