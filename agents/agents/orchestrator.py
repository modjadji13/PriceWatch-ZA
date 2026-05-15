from google.adk.agents import ParallelAgent, SequentialAgent
from .store_discovery import store_discovery_agent
from .scraper import scraper_agent
from .price_extraction import price_extraction_agent
from .comparison import comparison_agent

parallel_scraper = ParallelAgent(
    name="ParallelScraperAgent",
    description="Scrapes multiple stores simultaneously",
    sub_agents=[scraper_agent],
)

orchestrator = SequentialAgent(
    name="OrchestratorAgent",
    description="""
        Orchestrates the full price comparison pipeline:
        1. Discover stores for the category
        2. Scrape all stores in parallel
        3. Extract prices from HTML
        4. Compare and rank results
    """,
    sub_agents=[
        store_discovery_agent,
        parallel_scraper,
        price_extraction_agent,
        comparison_agent,
    ],
)
