# INSTALL.md

This guide explains how to install, configure, and run the Dataox Job Scraper project.

## Requirements

Make sure you have the following installed on your system:
	•	Java JDK 17 or higher
	•	Maven 3.x
	•	PostgreSQL 12 or higher
	•	Internet connection to download dependencies

## Database Setup

First, create a PostgreSQL database for the project. You can do this by connecting to your PostgreSQL server and running the following command:

```sql
CREATE DATABASE dataox_db;
```

## Next, configure the database connection settings in the application’s configuration file located at src/main/resources/application.properties. Replace the placeholders with your actual PostgreSQL username and password:
  spring.datasource.url=jdbc:postgresql://localhost:5432/dataox_db
  spring.datasource.username=your_username
  spring.datasource.password=your_password
  spring.jpa.hibernate.ddl-auto=update

This configuration ensures that the application connects to your database and updates the schema automatically when you run it.

## Building and Running the Project

After configuring the database, follow these steps to build and run the application:
	1.	Clone the project repository to your local machine and navigate into the project folder.
	2.	Use Maven to build the project and download all necessary dependencies:
	3.	Run the application either through Maven:
      	```bash
	mvn spring-boot:run
	```
 	 or by executing the generated JAR file:
      	```bash
	java -jar target/dataox-job-scraper.jar
 	```
Once the application starts, it will be available at http://localhost:8080. The job scraper will begin parsing job vacancies and saving unique entries to the PostgreSQL database. Duplicate job postings will be skipped and logged for your reference.
