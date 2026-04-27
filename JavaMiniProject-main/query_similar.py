"""
Hybrid retrieval: CLIP + color/pattern + garment type (from CSV `products`).
Prefers same clothing category, color, and print as the query.

Usage:
  python query_similar.py --query dataset/images/17048614.jpg --exclude-pid 17048614
  python query_similar.py --query path/to/outside.jpg   # infers type from CLIP neighbors
"""

from __future__ import annotations

import argparse
import pickle
import re
import sys
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from matplotlib import gridspec
from PIL import Image

# ── resolve project root so imports work from any working directory ────────────
ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))

from src_py.color_pattern_embedder import embed_pil
from src_py.clip_embedder import CLIPEmbedder
from src_py.garment_tokens import (
    family_from_tokens,
    garment_family,
    product_tokens,
    type_compatible,
    type_similarity,
)

# CLIP / color / pattern / garment-type (sum = 1)
W_CLIP = 0.44
W_COLOR = 0.28
W_PATTERN = 0.16
W_TYPE = 0.12


def _pid_from_filename(path: Path) -> int | None:
    m = re.match(r"^(\d+)\.(jpe?g|png)$", path.name, re.I)
    return int(m.group(1)) if m else None


def _infer_query_tokens(
    clip_q: np.ndarray,
    clip_emb: np.ndarray,
    p_ids: np.ndarray,
    pid_to_products: dict[int, str],
    topk: int = 5,
) -> frozenset:
    sims = clip_emb @ clip_q
    top = np.argsort(-sims)[:topk]
    merged: set[str] = set()
    for ix in top:
        pid = int(p_ids[ix])
        pr = pid_to_products.get(pid, "")
        merged |= set(product_tokens(pr))
    return frozenset(merged)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--query", type=Path, required=True)
    parser.add_argument("--topk", type=int, default=10)
    parser.add_argument(
        "--artifacts",
        type=Path,
        default=Path(__file__).resolve().parent / "artifacts",
    )
    parser.add_argument("--images-dir", type=Path, default=ROOT / "Dataset" / "images")
    parser.add_argument("--exclude-pid", type=int, default=None)
    parser.add_argument("--query-pid", type=int, default=None, help="Force CSV garment row (catalog id)")
    parser.add_argument("--out-figure", type=Path, default=None)
    parser.add_argument(
        "--infer-k",
        type=int,
        default=5,
        help="CLIP neighbors used to infer garment type for non-catalog queries",
    )
    args = parser.parse_args()

    clip_path = args.artifacts / "clip_embeddings.npy"
    c_path = args.artifacts / "color_embeddings.npy"
    p_path = args.artifacts / "pattern_embeddings.npy"
    pid_path = args.artifacts / "p_ids.npy"
    prod_path = args.artifacts / "pid_to_products.pkl"
    meta_path = args.artifacts / "metadata.csv"

    for req in (clip_path, c_path, p_path, pid_path, prod_path):
        if not req.is_file():
            raise SystemExit(f"Missing {req}. Run: python build_index.py")

    clip_emb = np.load(clip_path)
    color_emb = np.load(c_path)
    pattern_emb = np.load(p_path)
    p_ids = np.load(pid_path)
    with open(prod_path, "rb") as f:
        pid_to_products: dict[int, str] = pickle.load(f)

    meta_by_pid = None
    if meta_path.is_file():
        meta = pd.read_csv(meta_path, encoding="utf-8")
        meta_by_pid = meta.set_index("p_id")

    clip_model = CLIPEmbedder()
    qimg = Image.open(args.query).convert("RGB")
    clip_q = clip_model.embed_pil(qimg)
    qc, qp = embed_pil(qimg)

    # Query garment tokens: explicit pid > filename pid > CLIP inference
    q_pid = args.query_pid or _pid_from_filename(args.query)
    if q_pid is not None and q_pid in pid_to_products:
        q_tokens = product_tokens(pid_to_products[q_pid])
    else:
        q_tokens = _infer_query_tokens(clip_q, clip_emb, p_ids, pid_to_products, topk=args.infer_k)

    q_family = family_from_tokens(q_tokens)
    if q_pid is not None and q_pid in pid_to_products:
        gf = garment_family(pid_to_products[q_pid])
        if gf:
            q_family = gf

    clip_sims = clip_emb @ clip_q
    color_sims = color_emb @ qc
    pat_sims = pattern_emb @ qp

    hybrid = (
        W_CLIP * clip_sims
        + W_COLOR * color_sims
        + W_PATTERN * pat_sims
        + W_TYPE
        * np.array(
            [
                type_similarity(
                    q_tokens,
                    product_tokens(pid_to_products.get(int(pid), "")),
                )
                for pid in p_ids
            ],
            dtype=np.float32,
        )
    )

    # Hard filter: drop wrong garment families when we know query type
    mask = np.ones(len(p_ids), dtype=bool)
    for i, pid in enumerate(p_ids):
        c_str = pid_to_products.get(int(pid), "")
        c_tokens = product_tokens(c_str)
        c_fam = garment_family(c_str)
        if q_tokens and not type_compatible(q_tokens, q_family, c_tokens, c_fam):
            mask[i] = False

    hybrid = np.where(mask, hybrid, -1.0e9)

    order = np.argsort(-hybrid)

    out: list[tuple[int, float]] = []
    for idx in order:
        pid = int(p_ids[idx])
        if args.exclude_pid is not None and pid == args.exclude_pid:
            continue
        if hybrid[idx] < -1.0e8:
            continue
        out.append((pid, float(hybrid[idx])))
        if len(out) >= args.topk:
            break

    if not out:
        raise SystemExit(
            "No candidates passed the garment-type filter. Try a catalog query image, "
            "or --query-pid, or rebuild index with build_index.py."
        )

    tag = f"type={q_tokens}" if q_tokens else "type=inferred/unknown"
    print(f"Query garment tokens ({tag})\n")
    print(f"Top {len(out)} hybrid (CLIP + color + pattern + type) for {args.query}:\n")
    rank = 1
    for pid, score in out:
        line = f"  {rank}. p_id={pid}  score={score:.4f}"
        if meta_by_pid is not None and pid in meta_by_pid.index:
            row = meta_by_pid.loc[pid]
            name = str(row.get("name", ""))[:80]
            brand = str(row.get("brand", ""))
            line += f"  |  {brand}  {name}"
        line += f"\n      {args.images_dir / (str(pid) + '.jpg')}"
        print(line)
        rank += 1

    out_fig = args.out_figure or (args.artifacts / "last_query_similarity.png")
    out_fig.parent.mkdir(parents=True, exist_ok=True)
    _save_figure(
        query_path=args.query,
        query_pil=qimg,
        matches=out,
        images_dir=args.images_dir,
        meta_by_pid=meta_by_pid,
        subtitle=str(tag)[:120],
        out_path=out_fig,
    )
    print(f"\nSaved visualization: {out_fig.resolve()}")


def _save_figure(
    query_path: Path,
    query_pil: Image.Image,
    matches: list[tuple[int, float]],
    images_dir: Path,
    meta_by_pid,
    subtitle: str,
    out_path: Path,
) -> None:
    fig = plt.figure(figsize=(18, 7), dpi=120)
    fig.patch.set_facecolor("#f5f5f5")
    gs = gridspec.GridSpec(2, 7, figure=fig, wspace=0.22, hspace=0.35, left=0.03, right=0.99, top=0.90, bottom=0.06)

    ax_q = fig.add_subplot(gs[:, 0:2])
    ax_q.imshow(query_pil)
    ax_q.set_title(f"Query\n{query_path.name}", fontsize=11, fontweight="bold")
    ax_q.axis("off")

    for i, (pid, score) in enumerate(matches):
        r = i // 5
        c = 2 + (i % 5)
        ax = fig.add_subplot(gs[r, c])
        fp = images_dir / f"{pid}.jpg"
        try:
            im = Image.open(fp).convert("RGB")
            ax.imshow(im)
        except OSError:
            ax.text(0.5, 0.5, "missing", ha="center", va="center", transform=ax.transAxes)
        title = f"#{i + 1}  {score:.3f}\np_id {pid}"
        if meta_by_pid is not None and pid in meta_by_pid.index:
            row = meta_by_pid.loc[pid]
            brand = str(row.get("brand", ""))[:18]
            title = f"#{i + 1}  {score:.3f}\n{brand}"
        ax.set_title(title, fontsize=8)
        ax.axis("off")

    fig.suptitle(
        f"CLIP + color/pattern + garment type  |  {subtitle}",
        fontsize=11,
        y=0.98,
    )
    plt.savefig(out_path, bbox_inches="tight", facecolor=fig.get_facecolor())
    plt.close(fig)


if __name__ == "__main__":
    main()
