from dotenv import load_dotenv
load_dotenv()

import asyncio
from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService
from google.genai import types
from agents.orchestrator import orchestrator

session_service = InMemorySessionService()

runner = Runner(
    agent=orchestrator,
    app_name="PriceWatchZA",
    session_service=session_service,
)


async def search_prices(product: str, category: str) -> list:
    """Main entry point to search prices."""
    session = await session_service.create_session(
        app_name="PriceWatchZA",
        user_id="user_1",
    )

    message = types.Content(
        role="user",
        parts=[
            types.Part(
                text=f"Find prices for {product} in category {category} in South Africa"
            )
        ],
    )

    events = []
    async for event in runner.run_async(
        user_id="user_1",
        session_id=session.id,
        new_message=message,
    ):
        events.append(event)

    return events


if __name__ == "__main__":
    result = asyncio.run(search_prices("milk", "GROCERY"))
    print(result)
