# Research Helper

A Multi-Agent Research Pipeline built with Spring Boot and Anthropic Claude. It orchestrates multiple AI agents to perform deep research on user queries, producing structured reports with citations.

## Prerequisites

- Java 17
- Maven

## Environment Setup

Create a `.env` file in the project root:

```
ANTHROPIC_API_KEY=your-api-key-here
```

## Running Locally

```bash
# Using Maven
mvn spring-boot:run
```

The app starts at `http://localhost:8080`.

API docs available at `http://localhost:8080/swagger-ui/index.html`.

## IntelliJ IDEA Setup

1. **Set Java version**: Go to `File > Project Structure > Project` and set SDK to Java 17.

2. **Install EnvFile plugin**: Go to `File > Settings > Plugins`, search for "EnvFile" and install it.

3. **Configure Run Configuration**:
   - Open `Run > Edit Configurations`
   - Select your Spring Boot run config
   - Check **Enable EnvFile**
   - Click `+` and add your `.env` file

   ![Run Configuration](docs/run-config.png)

4. Click **Run**.
