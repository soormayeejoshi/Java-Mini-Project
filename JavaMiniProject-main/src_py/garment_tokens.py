"""
Garment-type tokeniser and compatibility helpers.

The CSV `products` column contains comma-separated garment names like:
  "Kurta, Palazzos, Dupatta"
  "Blazer, Trousers"
  "T-Shirt"

We normalise them to lower-case tokens and group them into coarse families
(tops / bottoms / dresses / outerwear / accessories) so that a query image
of a kurta only retrieves other tops/kurtas rather than trousers.
"""
from __future__ import annotations

import re

# ── Coarse garment families ────────────────────────────────────────────────────

_FAMILY_MAP: dict[str, str] = {
    # Tops / upper body
    "kurta": "top", "kurti": "top", "top": "top", "shirt": "top",
    "t-shirt": "top", "tshirt": "top", "blouse": "top", "crop top": "top",
    "tank top": "top", "bralette": "top", "tunic": "top", "shrug": "top",
    "sweatshirt": "top", "hoodie": "top", "vest": "top",
    # Bottoms / lower body
    "trouser": "bottom", "trousers": "bottom", "palazzo": "bottom",
    "palazzos": "bottom", "jeans": "bottom", "skirt": "bottom",
    "shorts": "bottom", "legging": "bottom", "leggings": "bottom",
    "jogger": "bottom", "joggers": "bottom", "capri": "bottom",
    # Dresses / full-length
    "dress": "dress", "gown": "dress", "jumpsuit": "dress",
    "romper": "dress", "saree": "dress", "anarkali": "dress",
    "maxi": "dress",
    # Outerwear
    "jacket": "outer", "blazer": "outer", "coat": "outer",
    "bomber": "outer", "windcheater": "outer", "hoodie": "outer",
    # Ethnic sets (treat as top for retrieval purposes)
    "dupatta": "accessory", "stole": "accessory", "scarf": "accessory",
    "belt": "accessory", "bag": "accessory", "clutch": "accessory",
    "socks": "accessory", "cap": "accessory", "hat": "accessory",
}

_COMPATIBLE: dict[str, frozenset[str]] = {
    "top":       frozenset({"top", "dress", "outer"}),
    "bottom":    frozenset({"bottom"}),
    "dress":     frozenset({"dress", "top"}),
    "outer":     frozenset({"outer", "top"}),
    "accessory": frozenset({"accessory", "top", "bottom", "dress", "outer"}),
}


# ── Helpers ────────────────────────────────────────────────────────────────────

def product_tokens(products_str: str) -> frozenset[str]:
    """
    Tokenise the `products` CSV field into a frozen set of lower-case strings.
    E.g. "Kurta, Palazzos, Dupatta"  →  frozenset({'kurta', 'palazzos', 'dupatta'})
    """
    if not products_str:
        return frozenset()
    tokens: set[str] = set()
    for part in re.split(r"[,\|&]+", products_str):
        t = part.strip().lower()
        if t:
            tokens.add(t)
    return frozenset(tokens)


def garment_family(products_str: str) -> str | None:
    """
    Return the coarse family for the *first* recognised token in the product string,
    or None if nothing is recognised.
    """
    for token in product_tokens(products_str):
        fam = _FAMILY_MAP.get(token)
        if fam:
            return fam
    return None


def family_from_tokens(tokens: frozenset[str]) -> str | None:
    for token in tokens:
        fam = _FAMILY_MAP.get(token)
        if fam:
            return fam
    return None


def type_compatible(
    q_tokens: frozenset[str],
    q_family: str | None,
    c_tokens: frozenset[str],
    c_family: str | None,
) -> bool:
    """
    Return True if a catalog item (c_*) is compatible with the query (q_*).
    Compatibility is symmetric at the family level; if either family is unknown
    we allow the match.
    """
    if q_family is None or c_family is None:
        return True
    allowed = _COMPATIBLE.get(q_family, frozenset({q_family}))
    return c_family in allowed


def type_similarity(
    q_tokens: frozenset[str],
    c_tokens: frozenset[str],
) -> float:
    """
    Jaccard similarity in token space [0, 1].
    Used as a soft type score in the hybrid ranking formula.
    """
    if not q_tokens or not c_tokens:
        return 0.5  # neutral when unknown
    inter = len(q_tokens & c_tokens)
    union = len(q_tokens | c_tokens)
    return inter / union if union else 0.0
