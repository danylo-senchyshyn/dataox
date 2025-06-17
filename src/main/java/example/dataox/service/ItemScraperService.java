package example.dataox.service;

import example.dataox.entity.Item;
import example.dataox.repository.ItemRepository;
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

    private final ItemRepository jobRepository;
    private final ItemSaveService itemSaveService;
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
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/114.0.0.0 Safari/537.36");
        //options.addArguments("--headless=new");
        options.addArguments("--disable-gpu", "--no-sandbox");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(10));
        List<Item> jobs = new ArrayList<>();

        try {
            driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(5));
            driver.get(url);
            Thread.sleep(3000);

            try {
                WebElement acceptCookiesButton = driver.findElement(By.id("onetrust-accept-btn-handler"));
                if (acceptCookiesButton.isDisplayed()) {
                    acceptCookiesButton.click();
                    System.out.println("Куки приняты.");
                    Thread.sleep(1000); // Подождём, пока баннер исчезнет
                }
            } catch (NoSuchElementException e) {
                System.out.println("Баннер cookies не найден — возможно, уже принят.");
            }

            int expectedJobCount = 0;
            try {
                WebElement showingJobsElement = driver.findElement(By.cssSelector("div.sc-beqWaB.eJrfpP"));
                String showingText = showingJobsElement.getText();

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

            int currentCount = 0;
            while (true) {
                List<WebElement> jobCards = driver.findElements(By.cssSelector("div[itemtype='https://schema.org/JobPosting']"));
                currentCount = jobCards.size();

                try {
                    WebElement loadMoreButton = driver.findElement(By.cssSelector("button[data-testid='load-more']"));
                    String loading = loadMoreButton.getAttribute("data-loading");

                    if ("false".equals(loading)) {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", loadMoreButton);
                        loadMoreButton.click();
                        Thread.sleep(2000);
                        while (expectedJobCount != currentCount) {
                            jobCards = driver.findElements(By.cssSelector("div[itemtype='https://schema.org/JobPosting']"));
                            currentCount = jobCards.size();
                            ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight);");
                            System.err.println(currentCount);
                            Thread.sleep(1500);
                        }
                    }
                } catch (NoSuchElementException e) {
                    System.out.println("Кнопка 'Load more' не найдена — возможно, всё загружено.");
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
            for (String jobUrl : jobUrls) {
            //for (int i = 0; i < 1 && i < jobUrls.size(); i++) {
              //  String jobUrl = jobUrls.get(i); //
                try {
                    driver.get(jobUrl);
                    wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("h2.jqWDOR")));
                    Thread.sleep(1000);

                    Item item = parseJobDetailPage(jobUrl, driver);
                    if (item != null) {
                        jobs.add(item);
                        itemSaveService.saveItem(item);
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

    public Item parseJobDetailPage(String jobPageUrl, WebDriver driver) {
        System.out.println("Парсим вакансию по URL: " + jobPageUrl);
        Item item = new Item();
        item.setJobPageUrl(jobPageUrl);
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
            item.setPositionName(name != null ? name.text() : "Unknown Position");
            //System.err.println("position name: " + job.getPositionName());

            // 2 url to organization
            Element orgLink = jobDoc.selectFirst("a.bpnNuw");
            // 4 organization title
            Element title = jobDoc.selectFirst("p.sc-beqWaB.bpXRKw");
            if (orgLink != null) {
                String href = orgLink.attr("href");
                item.setOrganizationUrl((href != null && !href.isEmpty()) ? (href.startsWith("http") ? href : BASE_URL + href) : "");
                item.setOrganizationTitle((title != null) ? title.text() : "Unknown Organization");
            } else {
                item.setOrganizationTitle("");
                item.setOrganizationUrl("");
            }
            //System.err.println("organization url: " + job.getOrganizationUrl());
            //System.err.println("organization title: " + job.getOrganizationTitle());

            // 3 logo
            Element logo = jobDoc.selectFirst("img.sc-dmqHEX.eTCoCQ");
            item.setLogoUrl(logo != null ? logo.attr("src") : "");
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
                    item.setLaborFunction(directionLine);
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
                item.setLocation(location);
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

                item.setPostedDate(date);
            } else {
                item.setPostedDate(new Date());
            }
            //System.out.println("Date from timestamp: " + job.getPostedDate());

            // 8 description
            Element desc = jobDoc.selectFirst("div.sc-beqWaB.fmCCHr");
            String descriptionText = desc != null ? desc.text() : null;
            //System.out.println("Parsed description: " + descriptionText);
            item.setDescription(descriptionText);
        } catch (Exception ex) {
            System.out.println("Ошибка при парсинге страницы вакансии: " + jobPageUrl);
            ex.printStackTrace();
            return null;
        }

        return item;
    }
}