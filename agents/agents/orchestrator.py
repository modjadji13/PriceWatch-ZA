from .comparison import compare_and_rank
from .price_extraction import extract_price_from_html
from .scraper import fetch_store_html
from .store_discovery import discover_stores


def search_prices(product_name: str, category: str = "GROCERY") -> dict:
    """
    Runs the Groq-only price comparison pipeline.

    Flow:
    1. Discover stores with Groq
    2. Fetch each store search page
    3. Extract prices with Groq
    4. Compare and optionally save to Spring Boot
    """
    stores = discover_stores(category)
    scraped_pages = [fetch_store_html(store, product_name) for store in stores]

    prices = [
        extract_price_from_html(
            page["html"],
            product_name,
            page["store"],
        )
        for page in scraped_pages
    ]

    result = compare_and_rank(prices, product_name, category)
    result["stores_checked"] = [store["name"] for store in stores]
    result["scrape_status"] = [
        {
            "store": page["store"],
            "status": page["status"],
        }
        for page in scraped_pages
    ]
    return result
