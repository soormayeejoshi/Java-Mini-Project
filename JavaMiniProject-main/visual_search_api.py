"""
Visual Search REST API — Flask server.

Loads the prebuilt CLIP/color/pattern index once at startup and serves:
  POST /search        multipart/form-data  field: "image" (any image file)
                      → JSON list of top-k products (p_id, score, name, brand, image_url, price)
  GET  /health        → {"status": "ok"} or {"status": "no_index"}
  GET  /status        → index statistics

Run BEFORE starting the Java app:
  python visual_search_api.py

Then the Java app sends image uploads to http://localhost:5000/search
"""

from __future__ import annotations

import io
import os
import pickle
import sys
from pathlib import Path

# ── resolve project root and module path ──────────────────────────────────────
ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))

import numpy as np
import pandas as pd
from PIL import Image
from flask import Flask, jsonify, request

from src_py.clip_embedder import CLIPEmbedder
from src_py.color_pattern_embedder import embed_pil
from src_py.garment_tokens import (
    family_from_tokens,
    garment_family,
    product_tokens,
    type_compatible,
    type_similarity,
)

# ── Hybrid scoring weights (must sum to 1) ────────────────────────────────────
W_CLIP    = 0.44
W_COLOR   = 0.28
W_PATTERN = 0.16
W_TYPE    = 0.12

ARTIFACTS = ROOT / "artifacts"

# ── App and global state ──────────────────────────────────────────────────────
app = Flask(__name__)

clip_emb:    np.ndarray | None = None
color_emb:   np.ndarray | None = None
pattern_emb: np.ndarray | None = None
p_ids:       np.ndarray | None = None
pid_to_products: dict[int, str] = {}
meta_by_pid = None
clip_model: CLIPEmbedder | None = None
index_loaded = False


def _load_index() -> None:
    global clip_emb, color_emb, pattern_emb, p_ids
    global pid_to_products, meta_by_pid, clip_model, index_loaded

    clip_path  = ARTIFACTS / "clip_embeddings.npy"
    c_path     = ARTIFACTS / "color_embeddings.npy"
    p_path     = ARTIFACTS / "pattern_embeddings.npy"
    pid_path   = ARTIFACTS / "p_ids.npy"
    prod_path  = ARTIFACTS / "pid_to_products.pkl"
    meta_path  = ARTIFACTS / "metadata.csv"

    for req in (clip_path, c_path, p_path, pid_path, prod_path):
        if not req.is_file():
            print(f"[WARNING] Index file missing: {req}")
            print("[WARNING] Run  python build_index.py  first to build the search index.")
            return

    print("[INFO] Loading CLIP model …")
    clip_model = CLIPEmbedder()

    print("[INFO] Loading index artifacts …")
    clip_emb    = np.load(clip_path)
    color_emb   = np.load(c_path)
    pattern_emb = np.load(p_path)
    p_ids       = np.load(pid_path)

    with open(prod_path, "rb") as f:
        pid_to_products = pickle.load(f)

    if meta_path.is_file():
        meta = pd.read_csv(meta_path, encoding="utf-8")
        meta_by_pid = meta.set_index("p_id")

    index_loaded = True
    print(f"[INFO] Index ready: {len(p_ids)} products embedded.")


def _infer_query_tokens(
    clip_q: np.ndarray, topk: int = 5
) -> frozenset:
    sims = clip_emb @ clip_q
    top  = np.argsort(-sims)[:topk]
    merged: set[str] = set()
    for ix in top:
        pid = int(p_ids[ix])
        merged |= set(product_tokens(pid_to_products.get(pid, "")))
    return frozenset(merged)


def _search(query_img: Image.Image, topk: int = 10) -> list[dict]:
    """
    Run hybrid retrieval on a PIL image and return a list of result dicts.
    """
    if not index_loaded:
        return []

    clip_q = clip_model.embed_pil(query_img)
    qc, qp = embed_pil(query_img)

    q_tokens = _infer_query_tokens(clip_q)
    q_family = family_from_tokens(q_tokens)

    clip_sims  = clip_emb  @ clip_q
    color_sims = color_emb @ qc
    pat_sims   = pattern_emb @ qp

    type_scores = np.array(
        [
            type_similarity(q_tokens, product_tokens(pid_to_products.get(int(pid), "")))
            for pid in p_ids
        ],
        dtype=np.float32,
    )

    hybrid = (
        W_CLIP    * clip_sims
        + W_COLOR   * color_sims
        + W_PATTERN * pat_sims
        + W_TYPE    * type_scores
    )

    # Hard filter: exclude incompatible garment families
    for i, pid in enumerate(p_ids):
        c_str    = pid_to_products.get(int(pid), "")
        c_tokens = product_tokens(c_str)
        c_fam    = garment_family(c_str)
        if q_tokens and not type_compatible(q_tokens, q_family, c_tokens, c_fam):
            hybrid[i] = -1.0e9

    order = np.argsort(-hybrid)

    results = []
    for idx in order:
        pid    = int(p_ids[idx])
        score  = float(hybrid[idx])
        if score < -1.0e8:
            continue

        row: dict = {"p_id": pid, "score": round(score, 4)}

        if meta_by_pid is not None and pid in meta_by_pid.index:
            m = meta_by_pid.loc[pid]
            row["name"]      = str(m.get("name",      ""))[:120]
            row["brand"]     = str(m.get("brand",     ""))
            row["price"]     = float(m.get("price",   0))
            row["image_url"] = str(m.get("img",       ""))
            row["category"]  = str(m.get("products",  ""))
            row["color"]     = str(m.get("colour",    ""))
        else:
            row.update({"name": "", "brand": "", "price": 0,
                        "image_url": "", "category": "", "color": ""})

        results.append(row)
        if len(results) >= topk:
            break

    return results


# ── Routes ────────────────────────────────────────────────────────────────────

@app.route("/health", methods=["GET"])
def health():
    if index_loaded:
        return jsonify({"status": "ok", "products_indexed": int(len(p_ids))})
    return jsonify({"status": "no_index",
                    "message": "Run python build_index.py first."}), 503


@app.route("/status", methods=["GET"])
def status():
    return jsonify({
        "index_loaded":      index_loaded,
        "products_indexed":  int(len(p_ids)) if p_ids is not None else 0,
        "artifacts_dir":     str(ARTIFACTS),
    })


@app.route("/search", methods=["POST"])
def search():
    if not index_loaded:
        return jsonify({"error": "Index not loaded. Run python build_index.py first."}), 503

    if "image" not in request.files:
        return jsonify({"error": "No 'image' field in request."}), 400

    file = request.files["image"]
    if file.filename == "":
        return jsonify({"error": "Empty filename."}), 400

    topk = int(request.args.get("topk", 10))

    try:
        img_bytes = file.read()
        img = Image.open(io.BytesIO(img_bytes)).convert("RGB")
    except Exception as e:
        return jsonify({"error": f"Could not decode image: {e}"}), 400

    results = _search(img, topk=topk)

    if not results:
        return jsonify({"error": "No results — garment type filter may be too strict."}), 404

    return jsonify(results)


# ── Entry point ───────────────────────────────────────────────────────────────

if __name__ == "__main__":
    port = int(os.environ.get("VS_PORT", 5000))
    _load_index()
    print(f"[INFO] Starting Visual Search API on http://localhost:{port}")
    # Threaded=True so Java's concurrent HTTP requests don't block each other
    app.run(host="0.0.0.0", port=port, threaded=True)
