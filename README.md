# 📌 Job Scraper

**Job Scraper** is a Java application built with Spring Boot for automatically scraping and storing job listings from the [Techstars](https://www.techstars.com/) website.

---

## 🚀 Features

- Retrieve job listings by industry via API
- Parse HTML pages using **Jsoup**
- Save job and company data to the database
- Flexible configuration via `application.properties`

---

## 🛠️ Technologies

- Java 17+
- Spring Boot
- Maven
- Jsoup
- Microsoft Playwright
- REST API

---

## 📂 Project Structure

```bash
src/
├── main/
│   ├── java/
│   │   └── com/
│   │       └── jobscraper/
│   │           ├── controller/      # API controllers and response models
│   │           ├── entity/          # Data entities
│   │           ├── repository/      # Spring Data repositories
│   │           └── services/        # Business logic and data parsing
│   └── resources/
│       └── application.properties   # Application configuration
```

---

## ▶️ Getting Started

1. Install dependencies:
   ```bash
   mvn clean install
   ```

2. Configure database connection:
   ```
   src/main/resources/application.properties
   ```

3. Run the application:
   ```bash
   mvn spring-boot:run
   ```

---

## 📘 Usage

Main service: `JobDataService`  
Implements the business logic for parsing and storing job listings from **Techstars**.

### 🔧 Key Methods:

- `fetchAndSaveAllListPages()`  
  Iterates through all industries, loads and saves paginated job listings.

- `fetchAndSaveListPagesByIndustryAndPage(industry, page)`  
  Loads and saves jobs for a specific industry and page.

- `createHeaders(refererIndustry)`  
  Builds HTTP headers for API requests.

- `getTags(organization, job)`  
  Gathers tags such as industry, company size, stage, and seniority.

- `formatTag(tag)`  
  Formats tags into human-readable strings.

- `jobFunctionUrls()`  
  Returns a map of industries and their corresponding job listing URLs.

- `laborFunctions()`  
  Returns a list of all supported job functions.

---

## 💾 Data Persistence

Jobs (`Item`) and job list pages (`ListPage`) are stored in the database via the following repositories:

- `ItemRepository`
- `ListPageRepository`

---

## 🏁 Start Scraping

To start scraping job listings, use:

```java
jobDataService.fetchAndSaveAllListPages();
```

---
