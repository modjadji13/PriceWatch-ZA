from google.adk.agents import Agent
from google.adk.tools import FunctionTool
import requests
from bs4 import BeautifulSoup


def fetch_store_html(store_url: str, product_name: str) -> dict:
    """Fetches HTML from a store search page."""
    try:
        url = f"{store_url}{product_name}"
        headers = {"User-Agent": "Mozilla/5.0"}
        response = requests.get(url, headers=headers, timeout=10)
        response.raise_for_status()

        soup = BeautifulSoup(response.text, "html.parser")

        for tag in soup(["script", "style", "nav", "footer"]):
            tag.decompose()

        trimmed_html = str(soup)[:3000]

        return {
            "store_url": store_url,
            "product": product_name,
            "html": trimmed_html,
            "status": "success",
        }

    except Exception as e:
        return {
            "store_url": store_url,
            "product": product_name,
            "html": "",
            "status": f"failed: {str(e)}",
        }


scraper_agent = Agent(
    name="ScraperAgent",
    model="gemini-2.0-flash",
    description="Fetches HTML from store websites for price extraction",
    instruction="""
        You are a web scraping agent.
        When given a store URL and product name, fetch the HTML from that store.
        Return the raw HTML trimmed to the most relevant section.
        Handle errors gracefully and report failed stores.
    """,
    tools=[FunctionTool(fetch_store_html)],
)
