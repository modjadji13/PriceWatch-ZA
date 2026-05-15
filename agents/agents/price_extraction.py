import json
import os
import re

from groq import Groq

GROQ_MODEL = os.getenv("GROQ_MODEL", "llama-3.3-70b-versatile")


def _groq_client() -> Groq:
    api_key = os.getenv("GROQ_API_KEY")
    if not api_key or api_key.startswith("your_"):
        raise ValueError("GROQ_API_KEY is missing in agents/.env")
    return Groq(api_key=api_key)


def _extract_json_object(text: str) -> dict:
    match = re.search(r"\{.*\}", text, re.DOTALL)
    if not match:
        return {}
    return json.loads(match.group(0))


def extract_price_from_html(html: str, product_name: str, store_name: str) -> dict:
    """Uses Groq to extract price from raw HTML."""
    if not html:
        return {"store": store_name, "price": 0, "currency": "ZAR"}

    prompt = f"""
    Extract the price of "{product_name}" from this HTML.
    Return JSON only, no explanation:
    {{"store": "{store_name}", "price": 15.99, "currency": "ZAR"}}
    If no price found return:
    {{"store": "{store_name}", "price": 0, "currency": "ZAR"}}
    HTML: {html[:3000]}
    """

    try:
        response = _groq_client().chat.completions.create(
            model=GROQ_MODEL,
            messages=[{"role": "user", "content": prompt}],
        )
        content = response.choices[0].message.content
        parsed = _extract_json_object(content)
        return {
            "store": parsed.get("store", store_name),
            "price": float(parsed.get("price", 0) or 0),
            "currency": parsed.get("currency", "ZAR"),
        }
    except Exception as e:
        return {
            "store": store_name,
            "price": 0,
            "currency": "ZAR",
            "error": str(e),
        }
