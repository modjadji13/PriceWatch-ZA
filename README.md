# PriceWatchZA

PriceWatchZA is an open source price comparison platform for South African consumers. It combines a Spring Boot scraping backend with a React dashboard UI for comparing prices, viewing sale items, and preparing future watchlist and alert workflows.

## Features

- Dashboard-style product discovery UI with a top navigation bar, filter sidebar, and compact product cards.
- Home page sale-items feed with discount badges, old prices, current prices, store logos, and store price ranges.
- Scraper-backed product search by product name and category.
- Product images and store logos when they can be read from supported store pages.
- Live-vs-estimated price indicators when store scraping is incomplete or blocked.
- Generic store marketing copy filtering so result cards do not show text like "Shop securely online..." as a product title.
- PostgreSQL persistence for live scraped prices.
- Protected watchlist, alerts, profile, and admin routes ready for authenticated workflows.

## Supported Categories

- Grocery
- Electronics
- Household
- Health
- Beauty

## Tech Stack

- Java 21
- Spring Boot 3.5
- Spring Data JPA
- PostgreSQL
- Docker Compose
- Jsoup
- React 18
- Vite
- TypeScript
- TanStack Query
- Lucide React

## Project Structure

```text
PriceWatchZA/
├── src/                         # Spring Boot backend
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
│           ├── application.properties
│           └── stores.json
├── frontend/                    # React + Vite frontend
│   └── src/
│       ├── app/
│       ├── components/
│       ├── features/
│       └── lib/
├── agents/                      # Python agent experiments
├── docker-compose.yml           # Local PostgreSQL
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

### 2. Configure Environment

If AI extraction is enabled, set a Groq API key:

```powershell
$env:GROQ_API_KEY="your_groq_api_key_here"
```

You can also create `agents/.env` for the Python agent experiments:

```env
GROQ_API_KEY=your_groq_api_key_here
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

## Scraping Notes

Many South African store sites block scraping, hide product cards behind JavaScript, or return generic SEO text. PriceWatchZA handles this by:

- showing estimated prices when live store prices are unavailable,
- not saving estimated prices as real price history,
- filtering generic store descriptions from product card titles,
- falling back to placeholders when product images cannot be scraped.

Store configuration lives in:

```text
src/main/resources/stores.json
```

## Validation

Backend compile:

```powershell
mvn -q -DskipTests compile
```

Frontend production build:

```powershell
cd frontend
npm run build
```

## Roadmap

- Add store-specific scrapers for more reliable images, names, and prices.
- Add real sale/deal endpoints instead of static home page examples.
- Add watchlist persistence and price-drop alerts.
- Add price history charts.
- Improve product matching across stores.
- Expand categories and store coverage.
- Add deployment configuration.

## License

Open source. License file to be added.
