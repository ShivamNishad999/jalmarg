import os
import zipfile
import hashlib
from pathlib import Path
from PIL import Image

def analyze_zip():
    zip_path = Path(r"D:\jalmarg\jalmarg\ml\datasets\irdid\images.zip")
    if zip_path.exists():
        with zipfile.ZipFile(zip_path, 'r') as z:
            names = z.namelist()
            print(f"ZIP Total entries: {len(names)}")
            non_images_in_zip = [n for n in names if not any(n.lower().endswith(ext) for ext in ['.jpg', '.jpeg', '.png', '.webp', '.bmp'])]
            print(f"Non-images in ZIP: {non_images_in_zip[:20]}")

def analyze_duplicates_and_hashes():
    root = Path(r"D:\jalmarg\jalmarg\ml\datasets\irdid\images\images")
    classes = [d.name for d in root.iterdir() if d.is_dir()]
    
    hash_to_paths = {}
    class_to_unique_hashes = {c: set() for c in classes}
    class_to_total = {c: 0 for c in classes}
    
    for c in classes:
        folder = root / c
        for img_p in folder.glob('*.*'):
            if img_p.suffix.lower() in ['.jpg', '.jpeg', '.png']:
                class_to_total[c] += 1
                with open(img_p, 'rb') as f:
                    sha = hashlib.sha256(f.read()).hexdigest()
                class_to_unique_hashes[c].add(sha)
                if sha not in hash_to_paths:
                    hash_to_paths[sha] = []
                hash_to_paths[sha].append((c, str(img_p.name)))

    print("\n--- PER-CLASS UNIQUE HASH ANALYSIS ---")
    for c in classes:
        print(f"Class '{c}': {class_to_total[c]} total images, {len(class_to_unique_hashes[c])} unique SHA-256 hashes")
        
    print(f"\nTotal unique SHA-256 images across all classes: {len(hash_to_paths)}")
    
    # Cross-class duplicate check
    cross_class = []
    for sha, occurrences in hash_to_paths.items():
        classes_present = set(o[0] for o in occurrences)
        if len(classes_present) > 1:
            cross_class.append({"sha": sha, "classes": list(classes_present), "files": occurrences})
            
    print(f"Cross-class duplicate count: {len(cross_class)}")
    if cross_class:
        for cc in cross_class[:5]:
            print(f"Cross-class duplicate across {cc['classes']}: {cc['files']}")

if __name__ == '__main__':
    analyze_zip()
    analyze_duplicates_and_hashes()
