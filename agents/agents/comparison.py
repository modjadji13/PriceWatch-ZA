import os

import requests

SPRING_BOOT_URL = os.getenv("SPRING_BOOT_URL", "http://localhost:8080")


def compare_and_rank(prices: list, product_name: str, category: str) -> dict:
    """Compares prices and returns ranked results."""
    valid_prices = [p for p in prices if p.get("price", 0) > 0]

    if not valid_prices:
        return {
            "product": product_name,
            "category": category,
            "error": "No prices found",
            "all_prices": [],
        }

    ranked = sorted(valid_prices, key=lambda x: x["price"])

    result = {
        "product": product_name,
        "category": category,
        "cheapest": ranked[0],
        "most_expensive": ranked[-1],
        "all_prices": ranked,
        "average": sum(p["price"] for p in ranked) / len(ranked),
        "savings": ranked[-1]["price"] - ranked[0]["price"],
    }

    try:
        requests.post(
            f"{SPRING_BOOT_URL}/api/prices/save",
            json=result,
            timeout=5,
        )
    except Exception as e:
        result["spring_boot_save_warning"] = str(e)

    return result
