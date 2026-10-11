import os
import sys
import json
import hashlib
from pathlib import Path
from PIL import Image

def get_file_sha256(filepath):
    h = hashlib.sha256()
    with open(filepath, 'rb') as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def audit_dataset(root_dir):
    root = Path(root_dir)
    print(f"Auditing root: {root}")
    
    classes = {}
    corrupted = []
    formats = {}
    dimensions = []
    file_sizes = []
    hashes = {}
    duplicates = []
    
    # Check all files recursively
    all_files = list(root.rglob('*'))
    image_files = [f for f in all_files if f.is_file() and f.suffix.lower() in {'.jpg', '.jpeg', '.png', '.webp', '.bmp'}]
    non_image_files = [f for f in all_files if f.is_file() and f.suffix.lower() not in {'.jpg', '.jpeg', '.png', '.webp', '.bmp', '.zip'}]
    
    print(f"Total files: {len(all_files)}")
    print(f"Total image files: {len(image_files)}")
    print(f"Non-image files: {[str(f.relative_to(root)) for f in non_image_files]}")
    
    for img_path in image_files:
        # Determine class name from parent folder
        class_name = img_path.parent.name
        classes[class_name] = classes.get(class_name, 0) + 1
        
        # Check size
        size = img_path.stat().st_size
        file_sizes.append(size)
        
        # Check format and corruption
        try:
            with Image.open(img_path) as img:
                fmt = img.format
                w, h = img.size
                mode = img.mode
                formats[fmt] = formats.get(fmt, 0) + 1
                dimensions.append((w, h))
                img.verify()
        except Exception as e:
            corrupted.append({"path": str(img_path), "error": str(e)})
            continue
            
        # Check SHA256 duplicate
        sha = get_file_sha256(img_path)
        if sha in hashes:
            duplicates.append({"original": hashes[sha], "duplicate": str(img_path), "sha256": sha})
        else:
            hashes[sha] = str(img_path)

    stats = {
        "dataset_root": str(root),
        "total_images": len(image_files),
        "class_distribution": classes,
        "formats": formats,
        "corrupted_count": len(corrupted),
        "corrupted_files": corrupted,
        "duplicate_count": len(duplicates),
        "duplicates_sample": duplicates[:5],
        "min_file_size": min(file_sizes) if file_sizes else 0,
        "max_file_size": max(file_sizes) if file_sizes else 0,
        "non_image_files": [str(f.relative_to(root)) for f in non_image_files]
    }
    
    print(json.dumps(stats, indent=2))
    return stats

if __name__ == '__main__':
    audit_dataset(r"D:\jalmarg\jalmarg\ml\datasets\irdid")
