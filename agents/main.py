from dotenv import load_dotenv
load_dotenv()

import json

from agents.orchestrator import search_prices


if __name__ == "__main__":
    result = search_prices("milk", "GROCERY")
    print(json.dumps(result, indent=2))
