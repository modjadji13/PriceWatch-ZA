from google.adk.agents import Agent
from google.adk.tools import FunctionTool
from groq import Groq
import os

client = Groq(api_key=os.getenv("GROQ_API_KEY"))


def discover_stores(category: str) -> dict:
    """Discovers SA stores for a given category using Groq."""
    response = client.chat.completions.create(
        model="llama-3.3-70b-versatile",
        messages=[
            {
                "role": "user",
                "content": f"""
                List South African online stores that sell {category} products.
                Return JSON array only, no explanation, no markdown:
                [{{"name": "StoreName", "searchUrl": "https://...", "category": "{category}"}}]
                Only include stores with working search URLs.
                """,
            }
        ],
    )
    return {"stores": response.choices[0].message.content}


store_discovery_agent = Agent(
    name="StoreDiscoveryAgent",
    model="gemini-2.0-flash",
    description="Discovers South African stores for a given product category",
    instruction="""
        You are a store discovery agent for South Africa.
        When given a category, find all relevant SA online stores.
        Return a list of stores with their search URLs.
        Categories include: GROCERY, FLIGHT, ACCOMMODATION, EHAILING, TRANSPORT.
    """,
    tools=[FunctionTool(discover_stores)],
)
