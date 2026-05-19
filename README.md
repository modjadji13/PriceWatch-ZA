# PriceWatchZA

PriceWatchZA is an open source price comparison platform for South Africans. It compares product prices across local stores, shows the lowest and highest offers, tracks price history, and prepares the foundation for sale alerts and automated price updates.

## What It Does

- Search for a product by name and category.
- Compare prices across supported South African stores.
- Show store logos, product details, and product images when they can be scraped.
- Mark fallback prices as estimates when live store scraping is blocked.
- Save live scraped prices to PostgreSQL for price history.
- Run automated scheduled price updates from the Spring Boot backend.

## Current Categories

- Groceries
- Electronics
- Household
- Health
- Beauty

## Tech Stack

- Java 21
- Spring Boot
- Spring Data JPA
- PostgreSQL
- Docker Compose
- Jsoup
- Groq API
- React
- Vite
- TypeScript
- TanStack Query

## Project Structure

```text
PriceWatchZA/
├── src/                    # Spring Boot backend
│   └── main/
│       ├── java/com/pricewatch/
│       │   ├── controller/
│       │   ├── dto/
│       │   ├── model/
│       │   ├── repository/
│       │   ├── scheduler/
│       │   ├── scraper/
│       │   └── service/
│       └── resources/
├── frontend/               # React frontend
├── agents/                 # Python Groq agent experiments
├── docker-compose.yml      # Local PostgreSQL
└── pom.xml
```

## Local Setup

### 1. Start PostgreSQL

```powershell
docker compose up -d postgres
```

The local database runs on:

```text
localhost:5433
```

### 2. Set Groq API Key

Create or update `agents/.env`:

```env
GROQ_API_KEY=your_groq_api_key_here
```

For Spring Boot, you can also set it in PowerShell:

```powershell
$env:GROQ_API_KEY="your_groq_api_key_here"
```

### 3. Run Backend

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.arguments=--server.port=8081"
```

Backend URL:

```text
http://localhost:8081
```

Example API call:

```text
http://localhost:8081/api/prices/compare?product=rice&category=GROCERY
```

### 4. Run Frontend

```powershell
cd frontend
npm install
npm run dev
```

Frontend URL:

```text
http://127.0.0.1:5173
```

Example results page:

```text
http://127.0.0.1:5173/results?product=rice&category=GROCERY
```

## Important Notes

Many South African store sites block scraping, hide products behind JavaScript, or return limited HTML. When PriceWatchZA cannot get a live price, it displays estimated prices and marks them clearly as estimates. Estimated prices are not saved as real live price history.

## Build Checks

Backend:

```powershell
mvn clean compile
```

Frontend:

```powershell
cd frontend
npm run build
```

## Roadmap

- Add store-specific scrapers for more reliable product images and prices.
- Add user watchlists.
- Add sale and price drop alerts.
- Add better product matching across stores.
- Add price history charts.
- Add more categories, including pharmacy, transport, flights, accommodation, and fuel.

## License

Open source. License file to be added.
