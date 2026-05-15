from google.adk.agents import Agent
from google.adk.tools import FunctionTool
import requests
import os

SPRING_BOOT_URL = os.getenv("SPRING_BOOT_URL", "http://localhost:8080")


def compare_and_rank(prices: list, product_name: str) -> dict:
    """Compares prices and returns ranked results."""
    try:
        valid_prices = [p for p in prices if p.get("price", 0) > 0]

        if not valid_prices:
            return {"error": "No prices found", "product": product_name}

        ranked = sorted(valid_prices, key=lambda x: x["price"])

        result = {
            "product": product_name,
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
        except Exception:
            pass

        return result

    except Exception as e:
        return {"error": str(e)}


comparison_agent = Agent(
    name="ComparisonAgent",
    model="gemini-2.0-flash",
    description="Compares and ranks prices across stores",
    instruction="""
        You are a price comparison agent.
        When given a list of prices from different stores:
        - Find the cheapest option
        - Find the most expensive option
        - Calculate average price
        - Calculate potential savings
        - Return a clean ranked comparison
        Always show prices in ZAR.
    """,
    tools=[FunctionTool(compare_and_rank)],
)
