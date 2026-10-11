import os
import sys
import json
from pathlib import Path

# Add project root and ml/src to sys.path
root_dir = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(root_dir / "ml" / "src"))

from jalmarg_ml.vision.dataset_audit import DatasetAuditor
from jalmarg_ml.vision.label_manifest import LabelManifestGenerator
from jalmarg_ml.vision.dataset_split import DatasetSplitter
from jalmarg_ml.vision.readiness_gate import VisionReadinessEvaluator
from jalmarg_ml.vision.train_classifier import ModelTrainer
from jalmarg_ml.vision.evaluate_classifier import ModelEvaluator
from jalmarg_ml.vision.inference_service import VisionInferenceService

def main():
    print("=" * 70)
    print("JALMARG PHASE 25B: IRDID COMPUTER VISION PIPELINE")
    print("=" * 70)

    dataset_dir = root_dir / "ml" / "datasets" / "irdid"
    artifacts_dir = root_dir / "ml" / "artifacts" / "vision"
    artifacts_dir.mkdir(parents=True, exist_ok=True)

    # 1. Audit Dataset
    print("\n[Step 1/5] Auditing IRDID Dataset...")
    auditor = DatasetAuditor(str(dataset_dir))
    audit_report = auditor.run_audit()
    with open(artifacts_dir / "dataset_audit_report.json", "w") as f:
        json.dump(audit_report, f, indent=2)
    print(f" -> Discovered: {audit_report['total_images_discovered']} images")
    print(f" -> Unique SHA-256: {audit_report['unique_sha256_count']}")
    print(f" -> Exact Duplicates: {audit_report['exact_duplicates_count']}")
    print(f" -> Cross-Class Duplicates: {audit_report['cross_class_duplicates_count']}")

    # 2. Generate Label Manifest
    print("\n[Step 2/5] Generating Leakage-Safe Label Manifest...")
    manifest_gen = LabelManifestGenerator(str(dataset_dir))
    manifest_data = manifest_gen.generate_manifest()
    with open(artifacts_dir / "label_manifest.json", "w") as f:
        json.dump(manifest_data, f, indent=2)
    summary = manifest_data["summary"]
    print(f" -> Usable Positives: {summary['usable_positive_samples']}")
    print(f" -> Usable Negatives: {summary['usable_negative_samples']}")
    print(f" -> Excluded Duplicates: {summary['excluded_duplicate_samples']}")
    print(f" -> Excluded Ambiguous: {summary['excluded_ambiguous_samples']}")

    # 3. Deterministic Leakage-Safe Dataset Split
    print("\n[Step 3/5] Performing Deterministic Dataset Split (Seed=42)...")
    splitter = DatasetSplitter(random_seed=42)
    splits = splitter.split(manifest_data, audit_report)
    with open(artifacts_dir / "dataset_splits.json", "w") as f:
        json.dump(splits, f, indent=2)
    split_summary = splits["summary"]
    print(f" -> Train: {split_summary['train_count']} ({split_summary['train_positives']} pos / {split_summary['train_negatives']} neg)")
    print(f" -> Val:   {split_summary['val_count']} ({split_summary['val_positives']} pos / {split_summary['val_negatives']} neg)")
    print(f" -> Test:  {split_summary['test_count']} ({split_summary['test_positives']} pos / {split_summary['test_negatives']} neg)")
    print(f" -> Leakage Check: {split_summary['leakage_verification']}")

    # 4. Training Readiness Gate Evaluation
    print("\n[Step 4/5] Evaluating Training Readiness Gate...")
    gate_evaluator = VisionReadinessEvaluator()
    readiness = gate_evaluator.evaluate(audit_report, summary, split_summary)
    with open(artifacts_dir / "readiness_assessment.json", "w") as f:
        json.dump(readiness, f, indent=2)
    print(f" -> Decision Gate Verdict: {readiness['readiness_gate']}")

    # 5. Model Training & Evaluation
    if readiness["readiness_gate"] == VisionReadinessEvaluator.GATE_READY:
        print("\n[Step 5/5] Training LightRoadWaterNet Baseline Classifier...")
        trainer = ModelTrainer(artifacts_dir=str(artifacts_dir), epochs=15, batch_size=16, lr=0.001)
        train_res = trainer.train(splits)
        print(f" -> Best Epoch: {train_res['best_epoch']} | Best Val F1: {train_res['best_val_f1']}")

        print("\n[Evaluation] Evaluating strictly on untouched Test Set...")
        evaluator = ModelEvaluator(checkpoint_path=str(artifacts_dir / "best_checkpoint.pt"))
        eval_report = evaluator.evaluate(splits["test"])
        metrics = eval_report["metrics"]
        print(f" -> Test Accuracy:  {metrics['accuracy']:.4f}")
        print(f" -> Test Precision: {metrics['precision']:.4f}")
        print(f" -> Test Recall:    {metrics['recall']:.4f}")
        print(f" -> Test F1 Score:  {metrics['f1_score']:.4f}")
        print(f" -> Avg Latency:    {metrics['avg_inference_latency_ms']:.2f} ms")
        print(f" -> Release Decision: {eval_report['release_decision']}")

        print("\n[Inference Verification] Testing secure VisionInferenceService...")
        service = VisionInferenceService(checkpoint_path=str(artifacts_dir / "best_checkpoint.pt"))
        sample_path = splits["test"][0]["path"]
        with open(sample_path, "rb") as f:
            sample_bytes = f.read()
        pred = service.predict(sample_bytes)
        print(f" -> Test sample prediction: {pred['prediction']} (conf: {pred['confidence']}, status: {pred['status']})")
    else:
        print(f"\nTraining skipped due to gate verdict: {readiness['readiness_gate']}")

    print("\n" + "=" * 70)
    print("PHASE 25B COMPUTER VISION PIPELINE COMPLETE")
    print("=" * 70)

if __name__ == '__main__':
    main()
