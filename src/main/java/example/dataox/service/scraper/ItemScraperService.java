package example.dataox.service.scraper;

import example.dataox.entity.Item;
import example.dataox.entity.ItemJobLink;
import example.dataox.entity.ListPage;
import example.dataox.repository.ItemJobLinkRepository;
import example.dataox.repository.ItemRepository;
import example.dataox.repository.ListPageRepository;
import example.dataox.service.save.ItemSaveService;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ItemScraperService {

    private final ItemRepository itemRepository;
    private final ItemSaveService itemSaveService;
    private final ListPageRepository listPageRepository;
    private final ItemJobLinkRepository itemJobLinkRepository;
    private static final String BASE_URL = "https://jobs.techstars.com";

    public void scrapeAllJobs(Map<String, String> jobFunctionsAndUrls) {
        for (Map.Entry<String, String> entry : jobFunctionsAndUrls.entrySet()) {
            try {
                scrapeJobDetailsFromListPage();
            } catch (Exception e) {
                System.out.println("Ошибка при парсинге функции: " + entry.getKey());
                e.printStackTrace();
            }
        }
    }

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

                        ItemJobLink link = new ItemJobLink();
                        link.setJobPageUrl(item.getJobPageUrl());
                        link.setJobPageUrlList(page.getJobPageUrl());
                        link.setItem(item);
                        link.setListPage(page);

                        itemJobLinkRepository.save(link);
                    }
                } catch (Exception ex) {
                    System.out.println("Ошибка при парсинге вакансии: " + page.getJobPageUrl());
                    ex.printStackTrace();
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
            System.out.println("Position Name: " + item.getPositionName());

            // 2. url to organization и 4. organization title
            Element orgLink = jobDoc.selectFirst("a.bpnNuw");
            Element title = jobDoc.selectFirst("p.sc-beqWaB.bpXRKw");
            if (orgLink != null) {
                String href = orgLink.attr("href");
                item.setOrganizationUrl(href.startsWith("http") ? href : BASE_URL + href);
                item.setOrganizationTitle(title != null ? title.text() : "Unknown Organization");
            } else {
                item.setOrganizationTitle("Unknown Organization");
                item.setOrganizationUrl("Unknown URL");
            }
            System.out.println("Organization: " + item.getOrganizationTitle() + " (" + item.getOrganizationUrl() + ")");

            // 3. logo
            Element logo = jobDoc.selectFirst("img.sc-dmqHEX.eTCoCQ");
            item.setLogoUrl(logo != null ? logo.attr("src") : "Unknown Logo URL");
            System.out.println("Logo URL: " + item.getLogoUrl());

            // 5. labor function
            List<String> laborFunctionsKeywords = List.of(
                    "Accounting & Finance", "Administration", "Compliance / Regulatory", "Customer Service",
                    "Data Science", "Design", "IT", "Legal", "Marketing & Communications", "Operations",
                    "Other Engineering", "People & HR", "Product", "Quality Assurance",
                    "Sales & Business Development", "Software Engineering"
            );
            Set<String> matchedFunctions = new LinkedHashSet<>();
            for (Element groupBlock : jobDoc.select("div.sc-beqWaB.sc-gueYoa.dmdAKU.MYFxR")) {
                Elements innerDivs = groupBlock.select("div.sc-beqWaB.bpXRKw");

                if (!innerDivs.isEmpty()) {
                    String rawDirectionsText = innerDivs.get(0).text();
                    for (String keyword : laborFunctionsKeywords) {
                        if (rawDirectionsText.contains(keyword)) {
                            matchedFunctions.add(keyword);
                        }
                    }
                }
            }
            String laborFunction = matchedFunctions.isEmpty() ?
                    "Unknown Labor Function" :
                    String.join(", ", matchedFunctions);
            item.setLaborFunction(laborFunction);
            System.out.println("Labor Function: " + item.getLaborFunction());

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
            String location = "Unknown Location";

            outer:
            for (Element groupBlock : jobDoc.select("div.sc-beqWaB.sc-gueYoa.dmdAKU.MYFxR")) {
                Elements innerDivs = groupBlock.select("div.sc-beqWaB.bpXRKw");
                StringBuilder fullLocationText = new StringBuilder();
                for (Element div : innerDivs) {
                    if (fullLocationText.length() > 0) {
                        fullLocationText.append(", ");
                    }
                    fullLocationText.append(div.text().trim());
                }
                for (Element div : innerDivs) {
                    String text = div.text();
                    for (String keyword : knownLocations) {
                        if (text.contains(keyword)) {
                            location = fullLocationText.toString();
                            break outer;
                        }
                    }
                }
            }
            item.setLocation(location);
            System.out.println("Location: " + location);

            // 7. posted date
            Date postedDate = null;
            for (Element groupBlock : jobDoc.select("div.sc-beqWaB.sc-gueYoa.dmdAKU.MYFxR")) {
                Elements innerDivs = groupBlock.select("div.sc-beqWaB");
                for (Element div : innerDivs) {
                    String text = div.text();
                    if (text.startsWith("Posted on")) {
                        try {
                            String dateStr = text.replace("Posted on", "").trim();
                            SimpleDateFormat inputFormat = new SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH);
                            Date parsedDate = inputFormat.parse(dateStr);

                            Calendar cal = Calendar.getInstance();
                            cal.setTime(parsedDate);
                            cal.set(Calendar.HOUR_OF_DAY, 0);
                            cal.set(Calendar.MINUTE, 0);
                            cal.set(Calendar.SECOND, 0);
                            cal.set(Calendar.MILLISECOND, 0);

                            postedDate = cal.getTime();
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                }
                if (postedDate != null) break;
            }
            item.setPostedDate(postedDate);
            System.out.println("Posted Date: " + postedDate);

            // 8. description (вся текстовая часть)
            Element descElement = jobDoc.selectFirst("div[data-testid=careerPage]");
            String description = (descElement != null && !descElement.text().isEmpty())
                    ? descElement.text().trim().replaceAll("\\s+", " ")
                    : "Unknown Description";
            item.setDescription(description);
            String preview = description.length() > 50 ? description.substring(0, 50) : description;
            System.out.println("Description preview: " + preview);

            return item;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}