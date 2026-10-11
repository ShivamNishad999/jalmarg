import io
import time
import hashlib
from pathlib import Path
from typing import Dict, Any, Optional

import torch
from torchvision import transforms
from PIL import Image

from .model_architecture import LightRoadWaterNet
from .label_manifest import LabelManifestGenerator

class VisionInferenceService:
    """Secure, reusable image inference service for visible road waterlogging detection."""

    MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024  # 10 MB
    MAX_IMAGE_DIMENSION = 4096  # Prevent decompression bombs
    MODEL_VERSION = "jalmarg-cv-lightroadwaternet-2026.1"

    def __init__(self, checkpoint_path: Optional[str] = "ml/artifacts/vision/best_checkpoint.pt"):
        self.checkpoint_path = Path(checkpoint_path) if checkpoint_path else None
        self.model: Optional[LightRoadWaterNet] = None
        self.transforms = transforms.Compose([
            transforms.Resize((256, 256)),
            transforms.ToTensor(),
            transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
        ])
        self._load_model()

    def _load_model(self):
        if self.checkpoint_path and self.checkpoint_path.exists():
            try:
                m = LightRoadWaterNet()
                m.load_state_dict(torch.load(self.checkpoint_path, map_location="cpu", weights_only=True))
                m.eval()
                self.model = m
            except Exception as e:
                self.model = None
        else:
            self.model = None

    def is_model_available(self) -> bool:
        return self.model is not None

    def predict(self, image_bytes: bytes) -> Dict[str, Any]:
        """Performs secure inference on raw image bytes."""
        t0 = time.perf_counter()
        
        # 1. Byte size validation
        if not image_bytes or len(image_bytes) == 0:
            return {
                "modelVersion": self.MODEL_VERSION,
                "status": "FAILED",
                "prediction": "INCONCLUSIVE",
                "confidence": None,
                "reviewRequired": True,
                "failureReason": "EMPTY_IMAGE_PAYLOAD",
                "limitations": ["No valid image bytes provided."]
            }

        if len(image_bytes) > self.MAX_IMAGE_SIZE_BYTES:
            return {
                "modelVersion": self.MODEL_VERSION,
                "status": "FAILED",
                "prediction": "INCONCLUSIVE",
                "confidence": None,
                "reviewRequired": True,
                "failureReason": "PAYLOAD_TOO_LARGE",
                "limitations": [f"Image size exceeds maximum limit of {self.MAX_IMAGE_SIZE_BYTES // (1024*1024)} MB."]
            }

        image_sha256 = hashlib.sha256(image_bytes).hexdigest()

        # 2. Image decode & decompression bomb validation
        try:
            with Image.open(io.BytesIO(image_bytes)) as img:
                if img.width > self.MAX_IMAGE_DIMENSION or img.height > self.MAX_IMAGE_DIMENSION:
                    return {
                        "modelVersion": self.MODEL_VERSION,
                        "status": "FAILED",
                        "prediction": "INCONCLUSIVE",
                        "confidence": None,
                        "reviewRequired": True,
                        "failureReason": "DIMENSION_EXCEEDS_SAFE_LIMIT",
                        "limitations": [f"Image dimensions exceed maximum safe limit ({self.MAX_IMAGE_DIMENSION}px)."]
                    }
                rgb_img = img.convert("RGB")
        except Exception:
            return {
                "modelVersion": self.MODEL_VERSION,
                "status": "FAILED",
                "prediction": "INCONCLUSIVE",
                "confidence": None,
                "reviewRequired": True,
                "failureReason": "INVALID_IMAGE_ENCODING",
                "limitations": ["Could not decode valid image pixels."]
            }

        # 3. Model availability check
        if self.model is None:
            return {
                "modelVersion": self.MODEL_VERSION,
                "status": "MODEL_UNAVAILABLE",
                "prediction": "INCONCLUSIVE",
                "confidence": None,
                "reviewRequired": True,
                "imageSha256": image_sha256,
                "failureReason": "MODEL_CHECKPOINT_NOT_LOADED",
                "limitations": ["Vision model checkpoint is not loaded. Fall back to manual municipal review."]
            }

        # 4. Run inference
        try:
            tensor_x = self.transforms(rgb_img).unsqueeze(0)
            with torch.no_grad():
                logits = self.model(tensor_x)
                prob = torch.sigmoid(logits).item()

            # Phase 25C: Dual-threshold abstention policy
            if 0.35 <= prob <= 0.65:
                pred_label = "INDETERMINATE"
                inference_status = "UNCERTAINTY_ABSTENTION_REQUIRED"
                confidence = max(prob, 1.0 - prob)
            else:
                is_waterlogged = prob > 0.65
                pred_label = (
                    LabelManifestGenerator.LABEL_POSITIVE
                    if is_waterlogged
                    else LabelManifestGenerator.LABEL_NEGATIVE
                )
                confidence = prob if is_waterlogged else (1.0 - prob)
                inference_status = "INFERENCE_COMPLETED"

            dt_ms = (time.perf_counter() - t0) * 1000.0

            return {
                "modelVersion": self.MODEL_VERSION,
                "status": inference_status,
                "prediction": pred_label,
                "confidence": round(confidence, 4),
                "rawProbability": round(prob, 4),
                "reviewRequired": True,  # Critical rule: AI must never bypass municipal verification
                "latencyMs": round(dt_ms, 2),
                "imageSha256": image_sha256,
                "limitations": [
                    "Visual assessment only; does not measure physical depth.",
                    "Does not verify capture timestamp or geographic coordinate accuracy.",
                    "Must be independently verified by municipal authority officers.",
                    "Abstention policy active: probabilities between 0.35 and 0.65 require human decision."
                ]
            }
        except Exception as e:
            return {
                "modelVersion": self.MODEL_VERSION,
                "status": "FAILED",
                "prediction": "INCONCLUSIVE",
                "confidence": None,
                "reviewRequired": True,
                "imageSha256": image_sha256,
                "failureReason": f"INFERENCE_EXECUTION_ERROR: {str(e)}",
                "limitations": ["Execution error occurred during inference."]
            }
