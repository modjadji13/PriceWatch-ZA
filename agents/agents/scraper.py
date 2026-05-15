import requests
from bs4 import BeautifulSoup


def fetch_store_html(store: dict, product_name: str) -> dict:
    """Fetches HTML from a store search page."""
    store_url = store.get("searchUrl", "")
    store_name = store.get("name", "Unknown Store")

    try:
        url = f"{store_url}{product_name}"
        headers = {"User-Agent": "Mozilla/5.0"}
        response = requests.get(url, headers=headers, timeout=10)
        response.raise_for_status()

        soup = BeautifulSoup(response.text, "html.parser")

        for tag in soup(["script", "style", "nav", "footer"]):
            tag.decompose()

        return {
            "store": store_name,
            "store_url": store_url,
            "product": product_name,
            "html": str(soup)[:3000],
            "status": "success",
        }

    except Exception as e:
        return {
            "store": store_name,
            "store_url": store_url,
            "product": product_name,
            "html": "",
            "status": f"failed: {str(e)}",
        }
