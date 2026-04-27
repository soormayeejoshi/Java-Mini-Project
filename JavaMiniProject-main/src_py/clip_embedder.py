"""
CLIP image embedder using open_clip (drop-in for openai/clip).
"""
from __future__ import annotations

import numpy as np
import torch
import open_clip
from PIL import Image


class CLIPEmbedder:
    """
    Wraps open_clip ViT-B/32 for image embedding.
    Embeddings are L2-normalised float32 vectors of dimension 512.
    """

    def __init__(self, model_name: str = "ViT-B-32", pretrained: str = "openai") -> None:
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.model, _, self.preprocess = open_clip.create_model_and_transforms(
            model_name, pretrained=pretrained
        )
        self.model = self.model.to(self.device).eval()

    # ------------------------------------------------------------------
    def embed_batch(self, imgs: list[Image.Image]) -> np.ndarray:
        """
        Embed a list of PIL images.
        Returns float32 array of shape (N, D), L2-normalised.
        """
        tensors = torch.stack([self.preprocess(img) for img in imgs]).to(self.device)
        with torch.no_grad():
            feats = self.model.encode_image(tensors)
        feats = feats / feats.norm(dim=-1, keepdim=True)
        return feats.cpu().float().numpy()

    def embed_pil(self, img: Image.Image) -> np.ndarray:
        """Embed a single PIL image. Returns 1-D float32 array."""
        return self.embed_batch([img])[0]
