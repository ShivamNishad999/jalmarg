import hashlib
import json
import logging
from pathlib import Path
from typing import Dict, Any, List, Tuple
from PIL import Image

logger = logging.getLogger(__name__)

class DatasetAuditor:
    """Audits raw computer vision image datasets for integrity, format, dimensions, and duplicates."""

    def __init__(self, dataset_root: str):
        self.dataset_root = Path(dataset_root)

    @staticmethod
    def compute_sha256(filepath: Path) -> str:
        h = hashlib.sha256()
        with open(filepath, 'rb') as f:
            while chunk := f.read(65536):
                h.update(chunk)
        return h.hexdigest()

    @staticmethod
    def compute_dhash(image: Image.Image, hash_size: int = 8) -> str:
        """Compute difference hash (dHash) for perceptual near-duplicate detection."""
        resized = image.convert('L').resize((hash_size + 1, hash_size), Image.Resampling.LANCZOS)
        pixels = list(resized.getdata())
        diff = []
        for row in range(hash_size):
            for col in range(hash_size):
                p_left = pixels[row * (hash_size + 1) + col]
                p_right = pixels[row * (hash_size + 1) + col + 1]
                diff.append(p_left > p_right)
        decimal_val = 0
        hex_str = []
        for idx, val in enumerate(diff):
            if val:
                decimal_val += 2 ** (idx % 8)
            if (idx % 8) == 7:
                hex_str.append(hex(decimal_val)[2:].rjust(2, '0'))
                decimal_val = 0
        return ''.join(hex_str)

    @staticmethod
    def hamming_distance(h1: str, h2: str) -> int:
        b1 = bytes.fromhex(h1)
        b2 = bytes.fromhex(h2)
        return sum(bin(x ^ y).count('1') for x, y in zip(b1, b2))

    def run_audit(self) -> Dict[str, Any]:
        """Audits the entire IRDID dataset tree."""
        if not self.dataset_root.exists():
            raise FileNotFoundError(f"Dataset directory does not exist: {self.dataset_root}")

        image_extensions = {'.jpg', '.jpeg', '.png', '.webp', '.bmp'}
        all_files = list(self.dataset_root.rglob('*'))
        images = [f for f in all_files if f.is_file() and f.suffix.lower() in image_extensions]

        class_distribution: Dict[str, int] = {}
        formats: Dict[str, int] = {}
        color_modes: Dict[str, int] = {}
        dimensions_count: Dict[str, int] = {}
        corrupted_files: List[Dict[str, str]] = []
        sha_to_paths: Dict[str, List[Path]] = {}
        unique_images_info: List[Dict[str, Any]] = []

        for p in images:
            raw_class = p.parent.name
            class_distribution[raw_class] = class_distribution.get(raw_class, 0) + 1

            # Validate decoding and dimensions
            try:
                with Image.open(p) as img:
                    fmt = img.format or "UNKNOWN"
                    mode = img.mode
                    dim = f"{img.width}x{img.height}"
                    formats[fmt] = formats.get(fmt, 0) + 1
                    color_modes[mode] = color_modes.get(mode, 0) + 1
                    dimensions_count[dim] = dimensions_count.get(dim, 0) + 1
                    img.verify()
                    # Reopen after verify to compute dHash
                    with Image.open(p) as reopened:
                        dh = self.compute_dhash(reopened)
            except Exception as e:
                corrupted_files.append({"path": str(p), "error": str(e)})
                continue

            # SHA-256 for exact deduplication
            sha = self.compute_sha256(p)
            if sha not in sha_to_paths:
                sha_to_paths[sha] = []
                unique_images_info.append({
                    "sha256": sha,
                    "primary_path": str(p),
                    "original_class": raw_class,
                    "filename": p.name,
                    "dhash": dh
                })
            sha_to_paths[sha].append(p)

        exact_duplicates = [
            {"sha256": sha, "paths": [str(x) for x in paths]}
            for sha, paths in sha_to_paths.items() if len(paths) > 1
        ]

        # Detect cross-class duplicates
        cross_class_duplicates = []
        for sha, paths in sha_to_paths.items():
            classes = set(p.parent.name for p in paths)
            if len(classes) > 1:
                cross_class_duplicates.append({
                    "sha256": sha,
                    "classes": list(classes),
                    "paths": [str(p) for p in paths]
                })

        # Detect near-duplicates using dHash (Hamming distance <= 4)
        near_duplicate_pairs = []
        n = len(unique_images_info)
        for i in range(n):
            for j in range(i + 1, n):
                dist = self.hamming_distance(unique_images_info[i]["dhash"], unique_images_info[j]["dhash"])
                if dist <= 4:
                    near_duplicate_pairs.append({
                        "image1": unique_images_info[i]["sha256"],
                        "image2": unique_images_info[j]["sha256"],
                        "class1": unique_images_info[i]["original_class"],
                        "class2": unique_images_info[j]["original_class"],
                        "hamming_distance": dist
                    })

        report = {
            "dataset_root": str(self.dataset_root),
            "total_images_discovered": len(images),
            "corrupted_images_count": len(corrupted_files),
            "corrupted_files": corrupted_files,
            "unique_sha256_count": len(sha_to_paths),
            "exact_duplicates_count": sum(len(paths) - 1 for paths in sha_to_paths.values()),
            "cross_class_duplicates_count": len(cross_class_duplicates),
            "cross_class_duplicates": cross_class_duplicates,
            "near_duplicate_pairs_count": len(near_duplicate_pairs),
            "class_distribution_raw": class_distribution,
            "formats_distribution": formats,
            "color_modes_distribution": color_modes,
            "dimensions_distribution": dimensions_count,
            "license_status": "LICENSE_REVIEW_REQUIRED",
            "license_notes": "IRDID road damage image repository lacks an embedded permissive LICENSE file; marked for research and human-review assist only.",
            "audit_verdict": "DATASET_PARSED_VALID"
        }
        return report
