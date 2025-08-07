# 📌 Job Scraper

**Job Scraper** — это Java-приложение на Spring Boot для автоматического сбора и сохранения вакансий с сайта [Techstars](https://www.techstars.com/).

---

## 🚀 Возможности

- Получение вакансий по индустриям через API
- Парсинг HTML-страниц с помощью **Jsoup**
- Сохранение информации о вакансиях и компаниях в базу данных
- Гибкая настройка конфигурации через `application.properties`

---

## 🛠️ Технологии

- Java 17+
- Spring Boot
- Maven
- Jsoup
- Microsoft Playwright
- REST API

---

## 📂 Структура проекта

```bash
src/
├── main/
│   ├── java/
│   │   └── com/
│   │       └── jobscraper/
│   │           ├── controller/      # Контроллеры и модели ответов API
│   │           ├── entity/          # Сущности для хранения данных
│   │           ├── repository/      # Репозитории Spring Data
│   │           └── services/        # Бизнес-логика и парсинг данных
│   └── resources/
│       └── application.properties   # Конфигурация приложения
```

---

## ▶️ Запуск

1. Установите зависимости:
   ```bash
   mvn clean install
   ```

2. Настройте параметры подключения к базе данных:
   ```
   src/main/resources/application.properties
   ```

3. Запустите приложение:
   ```bash
   mvn spring-boot:run
   ```

---

## 📘 Использование

Основной сервис: `JobDataService`  
Реализует бизнес-логику парсинга и сохранения вакансий с сайта **Techstars**.

### 🔧 Основные методы:

- `fetchAndSaveAllListPages()`  
  Перебирает все индустрии, загружает и сохраняет вакансии постранично.

- `fetchAndSaveListPagesByIndustryAndPage(industry, page)`  
  Загружает и сохраняет вакансии для выбранной индустрии и страницы.

- `createHeaders(refererIndustry)`  
  Формирует HTTP-заголовки для API-запросов.

- `getTags(organization, job)`  
  Собирает теги: индустрия, размер компании, стадия, seniority.

- `formatTag(tag)`  
  Форматирует строку-тег, делая её читабельной.

- `jobFunctionUrls()`  
  Возвращает карту индустрий и соответствующих URL для поиска вакансий.

- `laborFunctions()`  
  Возвращает список всех поддерживаемых функций труда.

---

## 💾 Хранение данных

Вакансии (`Item`) и страницы списков вакансий (`ListPage`) сохраняются в базу данных через репозитории:

- `ItemRepository`
- `ListPageRepository`

---

## 🏁 Запуск сбора вакансий

Для запуска основного процесса сбора используйте:

```java
jobDataService.fetchAndSaveAllListPages();
```

---
