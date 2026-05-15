import json
import os
import re

from groq import Groq

GROQ_MODEL = os.getenv("GROQ_MODEL", "llama-3.3-70b-versatile")

DEFAULT_STORES = [
    {
        "name": "Checkers",
        "searchUrl": "https://www.checkers.co.za/search?q=",
        "category": "GROCERY",
    },
    {
        "name": "Pick n Pay",
        "searchUrl": "https://www.pnp.co.za/search?q=",
        "category": "GROCERY",
    },
    {
        "name": "Woolworths",
        "searchUrl": "https://www.woolworths.co.za/cat?Ntt=",
        "category": "GROCERY",
    },
]


def _groq_client() -> Groq:
    api_key = os.getenv("GROQ_API_KEY")
    if not api_key or api_key.startswith("your_"):
        raise ValueError("GROQ_API_KEY is missing in agents/.env")
    return Groq(api_key=api_key)


def _extract_json_array(text: str) -> list:
    match = re.search(r"\[.*\]", text, re.DOTALL)
    if not match:
        return []
    return json.loads(match.group(0))


def discover_stores(category: str) -> list:
    """Discovers South African stores for a given category using Groq."""
    prompt = f"""
    List South African online stores that sell {category} products.
    Return JSON array only, no explanation, no markdown:
    [{{"name": "StoreName", "searchUrl": "https://...", "category": "{category}"}}]
    Only include stores with working search URLs.
    """

    try:
        response = _groq_client().chat.completions.create(
            model=GROQ_MODEL,
            messages=[{"role": "user", "content": prompt}],
        )
        stores = _extract_json_array(response.choices[0].message.content)
    except Exception as e:
        print(f"Store discovery failed, using defaults: {e}")
        stores = []

    fallback = [
        store for store in DEFAULT_STORES
        if store["category"].lower() == category.lower()
    ]

    seen = set()
    combined = []
    for store in fallback + stores:
        name = store.get("name", "").strip()
        search_url = store.get("searchUrl", "").strip()
        if name and search_url and name.lower() not in seen:
            seen.add(name.lower())
            combined.append({
                "name": name,
                "searchUrl": search_url,
                "category": store.get("category", category),
            })

    return combined
