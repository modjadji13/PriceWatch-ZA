# PriceWatchZA

PriceWatchZA is an open source price comparison platform for South African consumers. It combines a Spring Boot scraping backend with a React dashboard UI for comparing prices across major South African stores, saving products to a watchlist, and registering price-drop alerts.

## Features

- Dashboard-style product discovery UI with a top navigation bar, filter sidebar (stores, price range, availability, sort), and compact product cards.
- Home page sale-items feed with discount badges, old prices, current prices, store logos, and store price ranges.
- Scraper-backed product search by product name and category across 15+ stores.
- Cross-store grouping: the same product sold by several stores merges into one card showing "found at N stores" with the price range.
- Results auto-refresh for the first minute after a search, so prices from slower stores appear as the background scrape completes.
- Watchlist: save products per user and manage them from the dashboard.
- Price alerts: register a target price per product and toggle or delete alerts.
- Product images and store logos when they can be read from supported store pages.
- Live-vs-estimated price indicators when store scraping is incomplete or blocked.
- Generic store marketing copy filtering so result cards do not show text like "Shop securely online..." as a product title.
- PostgreSQL persistence for live scraped prices and cached search results.

## Supported Categories

- Grocery
- Electronics
- Household
- Health
- Beauty

## Supported Stores

Live scraping (HTML or public search APIs):

| Store | Source |
|---|---|
| Checkers | Sixty60 catalog API |
| Shoprite | Sixty60 catalog API |
| Pick n Pay | Server-rendered search page |
| Woolworths | Constructor.io search API |
| Takealot | Public product search API |
| Loot | HTML |
| HiFi Corp | Klevu search API |
| Clicks | HTML |
| Dis-Chem | HTML |
| Faithful to Nature | HTML (HTTP/2) |
| Wellness Warehouse | HTML |
| Incredible Connection | HTML |
| Computer Mania | HTML |
| Matrix Warehouse | HTML |

Spar and Makro cannot be scraped (no national online prices / bot wall) and appear through curated fallback pricing for popular grocery searches only.

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

Create `src/main/resources/application-local.properties` (gitignored) with your database connection:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/pricewatchza
spring.datasource.username=postgres
spring.datasource.password=pricewatch_dev_password
```

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

## Deployment

The app runs as two independently-deployed services:

| Piece | Host | Live URL |
| --- | --- | --- |
| Frontend (React / Vite) | Vercel | https://price-watch-za.vercel.app |
| Backend (Spring Boot) + PostgreSQL | Railway | https://pricewatch-za-production.up.railway.app |

**Backend (Railway).** Datasource and port come from the environment, so the same jar runs locally and in the cloud with no code change:

- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` — point at the managed Postgres. On Railway these reference the Postgres service, e.g. `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}`.
- `PORT` — injected by the host; the app binds to it (falls back to `8080` locally).
- `APP_CORS_ALLOWED_ORIGIN_PATTERNS` — optional; browser origins allowed to call the API. Defaults to `http://localhost:5173,https://*.vercel.app`; override it to add a custom domain.

The schema is created on boot (`spring.jpa.hibernate.ddl-auto=update`), so no manual migration is required for a fresh database.

**Frontend (Vercel).** Framework preset Vite (`npm run build`, output `dist/`). Set the API base so the browser calls the Railway backend instead of a same-origin `/api`:

```
VITE_API_BASE_URL=https://pricewatch-za-production.up.railway.app
```

Since the two live on different origins, the backend allows CORS from the Vercel origin (see `APP_CORS_ALLOWED_ORIGIN_PATTERNS`).

**Scraping from a cloud host.** Many retailers block datacenter IPs. The stores that work reliably in production are the ones reachable through a JSON search API rather than HTML scraping — Takealot, Checkers/Shoprite (Sixty60), Woolworths (Constructor), HiFi Corp and Dis-Chem (Klevu), and Clicks (Algolia). Purely HTML-scraped stores (and bot-walled ones like Makro/Game/Pick n Pay) may be unavailable from the cloud. The scraper rate-limits per host and caps total in-flight fetches to stay polite and within the container's memory.

## Scraping Notes

Many South African store sites block scraping, hide product cards behind JavaScript, or return generic SEO text. PriceWatchZA handles this by:

- answering repeat searches instantly from a cached database result (10-minute freshness window) while a background re-scrape updates it,
- scraping in two timeout tiers: a fast tier bounds first-time searches so they stay snappy, and a thorough background tier lets slow stores finish and be stored for next time,
- preferring stores' own public search APIs (Sixty60, Constructor.io, Klevu, Takealot) over fragile HTML selectors where available,
- retrying over HTTP/2 when a store's WAF blocks the default HTTP/1.1 client,
- skipping stores that fail repeatedly for a cooldown period instead of burning their timeout on every search,
- showing estimated prices when live store prices are unavailable,
- not saving estimated prices as real price history,
- filtering generic store descriptions from product card titles,
- falling back to placeholders when product images cannot be scraped.

Store configuration (search URL, parser type or CSS selectors, optional per-store user agent) lives in:

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

Live store diagnostic (hits real store websites, reports how many products every configured store returns):

```powershell
mvn test "-Dtest=StoreProbeTest" "-DprobeStores=true"
```

## Roadmap

- Add real sale/deal endpoints instead of static home page examples.
- Trigger alert notifications when a watched price drops below its threshold.
- Add price history charts.
- Improve product matching across stores.
- Expand categories and store coverage.
- Add deployment configuration.

## License

Open source. License file to be added.
