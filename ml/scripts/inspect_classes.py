from pathlib import Path
from PIL import Image
import numpy as np

def inspect_class_characteristics():
    root = Path(r"D:\jalmarg\jalmarg\ml\datasets\irdid\images\images")
    classes = [d.name for d in root.iterdir() if d.is_dir()]
    
    for c in sorted(classes):
        folder = root / c
        images = list(folder.glob('*.*'))
        resolutions = []
        brightnesses = []
        color_channels = set()
        
        for p in images[:50]:
            try:
                with Image.open(p) as img:
                    w, h = img.size
                    resolutions.append((w, h))
                    color_channels.add(img.mode)
                    # Convert to grayscale and calculate mean brightness
                    gray = np.array(img.convert('L'))
                    brightnesses.append(gray.mean())
            except Exception:
                pass
                
        common_res = max(set(resolutions), key=resolutions.count) if resolutions else (0, 0)
        mean_b = np.mean(brightnesses) if brightnesses else 0
        print(f"Class: {c:20s} | Total: {len(images):3d} | Sample Size: {len(resolutions):2d} | Common Res: {common_res} | Modes: {list(color_channels)} | Mean Brightness: {mean_b:.1f}")

if __name__ == '__main__':
    inspect_class_characteristics()
