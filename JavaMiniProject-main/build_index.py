"""
Precompute CLIP + color + pattern embeddings and product metadata for retrieval.

Usage (run from the java_mini project root):
  python build_index.py
  python build_index.py --clip-batch 64 --max-images 500
"""

from __future__ import annotations

import argparse
import pickle
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

# ── Add src_py/ to path so imports work from any working directory ─────────────
ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))

import numpy as np
import pandas as pd
from PIL import Image
from tqdm import tqdm

from src_py.clip_embedder import CLIPEmbedder
from src_py.color_pattern_embedder import embed_pil


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--csv",
        type=Path,
        # Updated: Dataset/ (capital D) matches the actual folder in this project
        default=ROOT / "Dataset" / "Fashion Dataset v2.csv",
    )
    parser.add_argument(
        "--images-dir",
        type=Path,
        # Updated: Dataset/images/
        default=ROOT / "Dataset" / "images",
    )
    parser.add_argument(
        "--out-dir",
        type=Path,
        default=ROOT / "artifacts",
    )
    parser.add_argument("--max-images", type=int, default=None,
                        help="Limit to first N images (useful for a quick test run)")
    parser.add_argument("--clip-batch", type=int, default=32)
    args = parser.parse_args()

    args.out_dir.mkdir(parents=True, exist_ok=True)

    print(f"Reading CSV: {args.csv}")
    df = pd.read_csv(args.csv, encoding="utf-8")
    if "p_id" not in df.columns or "products" not in df.columns:
        raise SystemExit("CSV must contain 'p_id' and 'products'.")

    pid_to_products = df.drop_duplicates("p_id").set_index("p_id")["products"].to_dict()

    # Collect all image paths that actually exist on disk
    rows: list[tuple[int, Path]] = []
    for pid in sorted(df["p_id"].dropna().astype(int).unique()):
        p = args.images_dir / f"{pid}.jpg"
        if p.is_file():
            rows.append((pid, p))

    if args.max_images is not None:
        rows = rows[: args.max_images]

    if not rows:
        raise SystemExit(
            f"No images found under {args.images_dir}.\n"
            "Please make sure product images (named <p_id>.jpg) exist there.\n"
            "Note: the model works with the dataset's online image URLs too; "
            "images are not strictly required for the Flask API (URL-based) mode."
        )

    print(f"Embedding {len(rows)} images …")
    clip_e = CLIPEmbedder()
    clip_rows: list[np.ndarray] = []
    color_rows: list[np.ndarray] = []
    pattern_rows: list[np.ndarray] = []
    p_ids: list[int] = []
    products_map: dict[int, str] = {}

    bs = max(1, args.clip_batch)
    for start in tqdm(range(0, len(rows), bs), desc="CLIP + color/pattern batches"):
        chunk = rows[start : start + bs]
        imgs: list[Image.Image] = []
        chunk_pids: list[int] = []
        for pid, path in chunk:
            try:
                imgs.append(Image.open(path).convert("RGB"))
                chunk_pids.append(pid)
            except OSError:
                continue
        if not imgs:
            continue

        ce = clip_e.embed_batch(imgs)
        with ThreadPoolExecutor(max_workers=4) as ex:
            cp_list = list(ex.map(embed_pil, imgs))

        for i, pid in enumerate(chunk_pids):
            c, pat = cp_list[i]
            clip_rows.append(ce[i])
            color_rows.append(c)
            pattern_rows.append(pat)
            p_ids.append(pid)
            raw = pid_to_products.get(pid)
            products_map[pid] = "" if (raw is None or pd.isna(raw)) else str(raw)

    if not p_ids:
        raise SystemExit("No valid images embedded.")

    clip_emb    = np.stack(clip_rows,    axis=0).astype(np.float32)
    color_emb   = np.stack(color_rows,   axis=0).astype(np.float32)
    pattern_emb = np.stack(pattern_rows, axis=0).astype(np.float32)
    p_ids_arr   = np.array(p_ids,        dtype=np.int64)

    np.save(args.out_dir / "clip_embeddings.npy",    clip_emb)
    np.save(args.out_dir / "color_embeddings.npy",   color_emb)
    np.save(args.out_dir / "pattern_embeddings.npy", pattern_emb)
    np.save(args.out_dir / "p_ids.npy",              p_ids_arr)
    with open(args.out_dir / "pid_to_products.pkl", "wb") as f:
        pickle.dump(products_map, f)

    meta = df[df["p_id"].isin(p_ids)].drop_duplicates(subset=["p_id"], keep="first")
    meta.to_csv(args.out_dir / "metadata.csv", index=False)

    print(
        f"\n[OK] Saved {len(p_ids)} items:"
        f"  CLIP {clip_emb.shape[1]}-D,"
        f"  color {color_emb.shape[1]}-D,"
        f"  pattern {pattern_emb.shape[1]}-D  ->  {args.out_dir}"
    )


if __name__ == "__main__":
    main()
