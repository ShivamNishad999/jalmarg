import pytest
import io
import torch
from pathlib import Path
from PIL import Image

from jalmarg_ml.vision.dataset_audit import DatasetAuditor
from jalmarg_ml.vision.label_manifest import LabelManifestGenerator
from jalmarg_ml.vision.dataset_split import DatasetSplitter
from jalmarg_ml.vision.readiness_gate import VisionReadinessEvaluator
from jalmarg_ml.vision.model_architecture import LightRoadWaterNet
from jalmarg_ml.vision.inference_service import VisionInferenceService

def test_lightroadwaternet_architecture():
    model = LightRoadWaterNet()
    model.eval()
    
    # Input batch of 2 256x256 RGB images
    x = torch.randn(2, 3, 256, 256)
    with torch.no_grad():
        out = model(x)
        
    assert out.shape == (2,), "Model output must be 1D logits per batch item"
    
    # Check total parameters and model size
    total_params = sum(p.numel() for p in model.parameters())
    assert total_params < 500_000, f"Model must be lightweight (< 500k params), got {total_params}"

def test_inference_service_validation():
    service = VisionInferenceService(checkpoint_path=None)
    
    # 1. Empty image bytes
    res_empty = service.predict(b"")
    assert res_empty["status"] == "FAILED"
    assert res_empty["failureReason"] == "EMPTY_IMAGE_PAYLOAD"
    assert res_empty["reviewRequired"] is True
    
    # 2. Oversized image bytes (> 10MB)
    large_bytes = b"0" * (11 * 1024 * 1024)
    res_large = service.predict(large_bytes)
    assert res_large["status"] == "FAILED"
    assert res_large["failureReason"] == "PAYLOAD_TOO_LARGE"
    assert res_large["reviewRequired"] is True
    
    # 3. Invalid image bytes
    res_invalid = service.predict(b"not-an-image-payload")
    assert res_invalid["status"] == "FAILED"
    assert res_invalid["failureReason"] == "INVALID_IMAGE_ENCODING"
    
    # 4. Decompression bomb check (> 4096px)
    img_large = Image.new("RGB", (4097, 100), color="blue")
    buf = io.BytesIO()
    img_large.save(buf, format="JPEG")
    res_bomb = service.predict(buf.getvalue())
    assert res_bomb["status"] == "FAILED"
    assert res_bomb["failureReason"] == "DIMENSION_EXCEEDS_SAFE_LIMIT"

def test_readiness_gate_evaluation():
    evaluator = VisionReadinessEvaluator()
    
    audit_report = {
        "corrupted_images_count": 0,
        "exact_duplicates_count": 378,
        "total_images_discovered": 1049,
        "license_status": "LICENSE_REVIEW_REQUIRED"
    }
    
    manifest_summary = {
        "usable_positive_samples": 95,
        "usable_negative_samples": 575,
        "excluded_duplicate_samples": 373,
        "excluded_ambiguous_samples": 6
    }
    
    split_summary = {
        "train_count": 468,
        "val_count": 100,
        "test_count": 102,
        "leakage_verification": "VERIFIED_ZERO_LEAKAGE"
    }
    
    readiness = evaluator.evaluate(audit_report, manifest_summary, split_summary)
    assert readiness["readiness_gate"] == VisionReadinessEvaluator.GATE_READY
    assert readiness["total_usable_unique_samples"] == 670
    assert readiness["license_status"] == "LICENSE_REVIEW_REQUIRED"
    assert readiness["release_policy_recommendation"] == "HUMAN_REVIEW_ASSIST"

def test_leakage_safe_splitting():
    splitter = DatasetSplitter(random_seed=42)
    
    # Synthetic mini-manifest
    mock_manifest = {
        "entries": [
            {"sha256": f"pos_{i}", "target_label": LabelManifestGenerator.LABEL_POSITIVE, "raw_class": "waterlogged_roads", "path": "p", "is_primary_sample": True} for i in range(20)
        ] + [
            {"sha256": f"neg_{i}", "target_label": LabelManifestGenerator.LABEL_NEGATIVE, "raw_class": "potholes", "path": "p", "is_primary_sample": True} for i in range(80)
        ]
    }
    
    mock_audit = {"images": []}
    splits = splitter.split(mock_manifest, mock_audit)
    
    assert splits["summary"]["leakage_verification"] == "VERIFIED_ZERO_LEAKAGE"
    train_hashes = set(e["sha256"] for e in splits["train"])
    val_hashes = set(e["sha256"] for e in splits["val"])
    test_hashes = set(e["sha256"] for e in splits["test"])
    
    # Assert zero overlap between any splits
    assert len(train_hashes.intersection(val_hashes)) == 0
    assert len(train_hashes.intersection(test_hashes)) == 0
    assert len(val_hashes.intersection(test_hashes)) == 0
