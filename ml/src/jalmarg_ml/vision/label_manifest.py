import hashlib
from pathlib import Path
from typing import Dict, Any, List

class LabelManifestGenerator:
    """Generates a human-auditable, leakage-safe label manifest mapping raw IRDID classes to binary target."""

    LABEL_POSITIVE = "VISIBLE_WATERLOGGING"
    LABEL_NEGATIVE = "NO_VISIBLE_WATERLOGGING"
    STATUS_EXCLUDED_DUPLICATE = "EXCLUDED_DUPLICATE"
    STATUS_EXCLUDED_AMBIGUOUS = "EXCLUDED_AMBIGUOUS"

    def __init__(self, dataset_root: str):
        self.dataset_root = Path(dataset_root)

    def generate_manifest(self) -> Dict[str, Any]:
        """Maps all files in the IRDID dataset into a transparent manifest."""
        image_extensions = {'.jpg', '.jpeg', '.png', '.webp', '.bmp'}
        all_images = [f for f in self.dataset_root.rglob('*') if f.is_file() and f.suffix.lower() in image_extensions]

        # Group by SHA-256
        sha_to_records = {}
        for img_path in sorted(all_images):
            h = hashlib.sha256()
            with open(img_path, 'rb') as f:
                while chunk := f.read(65536):
                    h.update(chunk)
            sha = h.hexdigest()
            raw_class = img_path.parent.name
            if sha not in sha_to_records:
                sha_to_records[sha] = []
            sha_to_records[sha].append({
                "path": str(img_path),
                "relative_path": str(img_path.relative_to(self.dataset_root)),
                "raw_class": raw_class,
                "filename": img_path.name
            })

        manifest_entries = []
        positive_count = 0
        negative_count = 0
        duplicate_count = 0
        ambiguous_count = 0

        for sha, records in sha_to_records.items():
            classes = set(r["raw_class"] for r in records)
            is_cross_class = len(classes) > 1

            for idx, r in enumerate(records):
                if is_cross_class:
                    target_label = self.STATUS_EXCLUDED_AMBIGUOUS
                    exclusion_reason = f"Cross-class duplicate found across categories: {list(classes)}"
                    ambiguous_count += 1
                elif idx > 0:
                    target_label = self.STATUS_EXCLUDED_DUPLICATE
                    exclusion_reason = f"Exact SHA-256 duplicate of {records[0]['relative_path']}"
                    duplicate_count += 1
                else:
                    exclusion_reason = None
                    if r["raw_class"] == "waterlogged_roads":
                        target_label = self.LABEL_POSITIVE
                        positive_count += 1
                    else:
                        target_label = self.LABEL_NEGATIVE
                        negative_count += 1

                manifest_entries.append({
                    "sha256": sha,
                    "path": r["path"],
                    "relative_path": r["relative_path"],
                    "raw_class": r["raw_class"],
                    "target_label": target_label,
                    "is_primary_sample": (idx == 0 and not is_cross_class),
                    "exclusion_reason": exclusion_reason
                })

        summary = {
            "total_files": len(all_images),
            "unique_sha256_count": len(sha_to_records),
            "usable_positive_samples": positive_count,
            "usable_negative_samples": negative_count,
            "excluded_duplicate_samples": duplicate_count,
            "excluded_ambiguous_samples": ambiguous_count,
            "class_imbalance_ratio": f"1:{round(negative_count / max(1, positive_count), 2)}",
            "entries_count": len(manifest_entries)
        }

        return {
            "summary": summary,
            "entries": manifest_entries
        }
