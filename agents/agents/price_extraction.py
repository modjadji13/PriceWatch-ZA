from google.adk.agents import Agent
from google.adk.tools import FunctionTool
from groq import Groq
import os
import json

client = Groq(api_key=os.getenv("GROQ_API_KEY"))


def extract_price_from_html(html: str, product_name: str, store_name: str) -> dict:
    """Uses Groq to extract price from raw HTML."""
    try:
        response = client.chat.completions.create(
            model="llama-3.3-70b-versatile",
            messages=[
                {
                    "role": "user",
                    "content": f"""
                    Extract the price of "{product_name}" from this HTML.
                    Return JSON only, no explanation:
                    {{"store": "{store_name}", "price": 15.99, "currency": "ZAR"}}
                    If no price found return:
                    {{"store": "{store_name}", "price": 0, "currency": "ZAR"}}
                    HTML: {html}
                    """,
                }
            ],
        )

        content = response.choices[0].message.content
        return json.loads(content)

    except Exception as e:
        return {
            "store": store_name,
            "price": 0,
            "currency": "ZAR",
            "error": str(e),
        }


price_extraction_agent = Agent(
    name="PriceExtractionAgent",
    model="gemini-2.0-flash",
    description="Extracts prices from raw HTML using Groq AI",
    instruction="""
        You are a price extraction agent.
        When given raw HTML and a product name, extract the price.
        Always return structured JSON with store name and price.
        Handle currency symbols and formatting.
        Return 0 if no price is found.
    """,
    tools=[FunctionTool(extract_price_from_html)],
)
