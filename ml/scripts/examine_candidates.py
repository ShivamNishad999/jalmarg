import os
import hashlib
from pathlib import Path
from PIL import Image
import numpy as np

def examine_negative_candidates():
    root = Path(r"D:\jalmarg\jalmarg\ml\datasets\irdid\images\images")
    classes = [d.name for d in root.iterdir() if d.is_dir()]
    
    print("Class folders present:")
    for c in sorted(classes):
        print(f" - {c}: {len(list((root / c).glob('*.*')))} images")
        
    # Check for color variance and saturation in waterlogged_roads vs cracks/potholes/construction
    for c in sorted(classes):
        folder = root / c
        images = list(folder.glob('*.*'))[:30]
        saturations = []
        blueness = []
        for p in images:
            with Image.open(p) as img:
                rgb = np.array(img.convert('RGB'), dtype=np.float32)
                # Blueness: B - (R + G)/2
                b_diff = rgb[:, :, 2] - (rgb[:, :, 0] + rgb[:, :, 1]) / 2.0
                blueness.append(b_diff.mean())
        print(f"Class: {c:20s} | Mean Blue Dominance: {np.mean(blueness):.2f}")

if __name__ == '__main__':
    examine_negative_candidates()
