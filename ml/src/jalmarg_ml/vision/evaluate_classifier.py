import os
import json
import time
from pathlib import Path
from typing import Dict, Any, List

import torch
from torch.utils.data import DataLoader
from torchvision import transforms

from .model_architecture import LightRoadWaterNet
from .train_classifier import RoadWaterDataset
from .label_manifest import LabelManifestGenerator

class ModelEvaluator:
    """Evaluates trained LightRoadWaterNet checkpoint strictly on the held-out test split."""

    def __init__(self, checkpoint_path: str = "ml/artifacts/vision/best_checkpoint.pt"):
        self.checkpoint_path = Path(checkpoint_path)

    def evaluate(self, test_entries: List[Dict[str, Any]]) -> Dict[str, Any]:
        if not self.checkpoint_path.exists():
            raise FileNotFoundError(f"Checkpoint does not exist: {self.checkpoint_path}")

        # Deterministic transforms
        test_transforms = transforms.Compose([
            transforms.Resize((256, 256)),
            transforms.ToTensor(),
            transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
        ])

        test_ds = RoadWaterDataset(test_entries, transform=test_transforms)
        test_loader = DataLoader(test_ds, batch_size=1, shuffle=False)

        model = LightRoadWaterNet()
        model.load_state_dict(torch.load(self.checkpoint_path, map_location="cpu", weights_only=True))
        model.eval()

        tp, fp, fn, tn = 0, 0, 0, 0
        latencies = []
        detailed_results = []

        with torch.no_grad():
            for idx, (X, y) in enumerate(test_loader):
                t0 = time.perf_counter()
                logits = model(X)
                prob = torch.sigmoid(logits).item()
                dt = (time.perf_counter() - t0) * 1000.0  # ms
                latencies.append(dt)

                pred_label = 1.0 if prob >= 0.5 else 0.0
                true_label = y.item()

                if pred_label == 1.0 and true_label == 1.0: tp += 1
                elif pred_label == 1.0 and true_label == 0.0: fp += 1
                elif pred_label == 0.0 and true_label == 1.0: fn += 1
                else: tn += 1

                detailed_results.append({
                    "sha256": test_entries[idx]["sha256"],
                    "filename": test_entries[idx].get("filename", ""),
                    "raw_class": test_entries[idx]["raw_class"],
                    "true_label": LabelManifestGenerator.LABEL_POSITIVE if true_label == 1.0 else LabelManifestGenerator.LABEL_NEGATIVE,
                    "predicted_label": LabelManifestGenerator.LABEL_POSITIVE if pred_label == 1.0 else LabelManifestGenerator.LABEL_NEGATIVE,
                    "confidence_score": round(prob if pred_label == 1.0 else (1.0 - prob), 4),
                    "raw_probability": round(prob, 4),
                    "is_correct": bool(pred_label == true_label),
                    "latency_ms": round(dt, 2)
                })

        total = len(test_entries)
        accuracy = (tp + tn) / max(1, total)
        precision = tp / max(1, tp + fp)
        recall = tp / max(1, tp + fn)
        f1 = 2 * precision * recall / max(1e-6, precision + recall)
        avg_latency_ms = sum(latencies) / max(1, len(latencies))
        model_size_bytes = self.checkpoint_path.stat().st_size

        report = {
            "model_version": "jalmarg-cv-lightroadwaternet-2026.1",
            "model_architecture": "LightRoadWaterNet",
            "checkpoint_path": str(self.checkpoint_path),
            "model_size_bytes": model_size_bytes,
            "model_size_mb": round(model_size_bytes / (1024 * 1024), 2),
            "total_test_samples": total,
            "positive_test_samples": tp + fn,
            "negative_test_samples": tn + fp,
            "metrics": {
                "accuracy": round(accuracy, 4),
                "precision": round(precision, 4),
                "recall": round(recall, 4),
                "f1_score": round(f1, 4),
                "true_positives": tp,
                "false_positives": fp,
                "true_negatives": tn,
                "false_negatives": fn,
                "avg_inference_latency_ms": round(avg_latency_ms, 2)
            },
            "release_decision": "HUMAN_REVIEW_ASSIST",
            "release_notes": "Model validated for assistive review. Predictions are strictly non-authoritative signals for municipal officers.",
            "known_failure_modes": [
                "Wet asphalt without standing water may yield false positives due to light reflection.",
                "Potholes filled with rainwater can be classified as waterlogging; distinguish at municipal review.",
                "Night-time photos with headlight glare have reduced contrast.",
                "Debris or shadows can cause localized false activations."
            ],
            "detailed_test_predictions": detailed_results
        }

        eval_path = self.checkpoint_path.parent / "test_evaluation_report.json"
        with open(eval_path, "w") as f:
            json.dump(report, f, indent=2)

        return report
