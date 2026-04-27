"""
Color histogram + LBP texture descriptor for a PIL image.

Color  : 32-bin HSV histogram (hue only) → 32-D, L2-normalised
Pattern: 59-bin uniform LBP histogram    → 59-D, L2-normalised

Both are cheap to compute and complementary to CLIP similarity.
"""
from __future__ import annotations

import numpy as np
from PIL import Image


# ──────────────────────────────────────────────────────────────────────────────
#  Color descriptor: HSV hue histogram
# ──────────────────────────────────────────────────────────────────────────────

def _color_descriptor(img: Image.Image, bins: int = 32) -> np.ndarray:
    """32-D L2-normalised hue histogram (ignores very dark / desaturated pixels)."""
    hsv = img.convert("HSV")
    h, s, v = [np.asarray(c, dtype=np.float32) for c in hsv.split()]
    # Mask out near-black and near-grey pixels so background doesn't dominate
    mask = (s > 25) & (v > 50)
    hue = h[mask]
    if hue.size == 0:
        # Fallback: use all pixels
        hue = h.ravel()
    hist, _ = np.histogram(hue, bins=bins, range=(0, 255))
    hist = hist.astype(np.float32)
    norm = np.linalg.norm(hist)
    return hist / (norm + 1e-8)


# ──────────────────────────────────────────────────────────────────────────────
#  Pattern descriptor: Local Binary Pattern (uniform, P=8, R=1)
# ──────────────────────────────────────────────────────────────────────────────

def _lbp_uniform(grey: np.ndarray) -> np.ndarray:
    """
    Compute uniform LBP (P=8, R=1) on a greyscale uint8 array.
    Returns a 59-D L2-normalised histogram.
    There are 58 uniform patterns + 1 non-uniform class = 59 bins.
    """
    h, w = grey.shape
    # Offsets for 8 neighbours (top-left clockwise)
    neighbours = [
        (-1, -1), (-1, 0), (-1, 1),
        ( 0,  1),
        ( 1,  1), ( 1,  0), ( 1, -1),
        ( 0, -1),
    ]
    center = grey[1:-1, 1:-1].astype(np.int32)
    lbp = np.zeros_like(center, dtype=np.int32)
    for bit, (dr, dc) in enumerate(neighbours):
        nb = grey[1 + dr: h - 1 + dr, 1 + dc: w - 1 + dc].astype(np.int32)
        lbp |= (nb >= center).astype(np.int32) << bit

    # Determine uniformity: number of 0→1 / 1→0 transitions in the circular bit string
    def transitions(v: np.ndarray) -> np.ndarray:
        t = np.zeros_like(v)
        for i in range(8):
            b0 = (v >> i) & 1
            b1 = (v >> ((i + 1) % 8)) & 1
            t += (b0 != b1).astype(np.int32)
        return t

    trans = transitions(lbp)
    uniform_label = np.where(trans <= 2, lbp, 58)  # 58 = non-uniform class

    hist = np.bincount(uniform_label.ravel(), minlength=59).astype(np.float32)
    norm = np.linalg.norm(hist)
    return hist / (norm + 1e-8)


# ──────────────────────────────────────────────────────────────────────────────
#  Public API
# ──────────────────────────────────────────────────────────────────────────────

def embed_pil(img: Image.Image) -> tuple[np.ndarray, np.ndarray]:
    """
    Returns (color_vec, pattern_vec):
      color_vec   float32 (32,)  – L2-normalised HSV hue histogram
      pattern_vec float32 (59,)  – L2-normalised uniform LBP histogram
    """
    # Resize to a fixed size for speed; keep aspect ratio
    img_sm = img.resize((128, 128), Image.BILINEAR)
    color_vec = _color_descriptor(img_sm)
    grey = np.asarray(img_sm.convert("L"), dtype=np.uint8)
    pattern_vec = _lbp_uniform(grey)
    return color_vec, pattern_vec
