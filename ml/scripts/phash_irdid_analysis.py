import hashlib
from pathlib import Path
from PIL import Image

def dhash(image, hash_size=8):
    # Resize to (hash_size + 1, hash_size)
    image = image.convert('L').resize((hash_size + 1, hash_size), Image.Resampling.LANCZOS)
    pixels = list(image.getdata())
    # Compare adjacent pixels
    diff = []
    for row in range(hash_size):
        for col in range(hash_size):
            pixel_left = pixels[row * (hash_size + 1) + col]
            pixel_right = pixels[row * (hash_size + 1) + col + 1]
            diff.append(pixel_left > pixel_right)
    # Convert bool list to hex string
    decimal_val = 0
    hex_str = []
    for index, value in enumerate(diff):
        if value:
            decimal_val += 2**(index % 8)
        if (index % 8) == 7:
            hex_str.append(hex(decimal_val)[2:].rjust(2, '0'))
            decimal_val = 0
    return ''.join(hex_str)

def hamming_distance(h1, h2):
    b1 = bytes.fromhex(h1)
    b2 = bytes.fromhex(h2)
    return sum(bin(x ^ y).count('1') for x, y in zip(b1, b2))

def analyze_phash():
    root = Path(r"D:\jalmarg\jalmarg\ml\datasets\irdid\images\images")
    unique_items = []
    seen_sha = set()
    
    for folder in root.iterdir():
        if not folder.is_dir():
            continue
        c = folder.name
        for p in folder.glob('*.*'):
            if p.suffix.lower() in ['.jpg', '.jpeg', '.png']:
                with open(p, 'rb') as f:
                    sha = hashlib.sha256(f.read()).hexdigest()
                if sha not in seen_sha:
                    seen_sha.add(sha)
                    with Image.open(p) as img:
                        dh = dhash(img)
                    unique_items.append({"class": c, "path": p, "sha": sha, "dhash": dh})

    print(f"Computed dHash for {len(unique_items)} unique images.")
    
    # Cluster near duplicates with hamming distance <= 4
    near_dups = []
    for i in range(len(unique_items)):
        for j in range(i + 1, len(unique_items)):
            dist = hamming_distance(unique_items[i]["dhash"], unique_items[j]["dhash"])
            if dist <= 4:
                near_dups.append((unique_items[i], unique_items[j], dist))
                
    print(f"Found {len(near_dups)} near-duplicate pairs (Hamming distance <= 4).")
    
    # Check within waterlogged_roads
    wl_items = [u for u in unique_items if u["class"] == 'waterlogged_roads']
    wl_near_dups = []
    for i in range(len(wl_items)):
        for j in range(i + 1, len(wl_items)):
            dist = hamming_distance(wl_items[i]["dhash"], wl_items[j]["dhash"])
            if dist <= 4:
                wl_near_dups.append((wl_items[i], wl_items[j], dist))
    print(f"Waterlogged roads unique images: {len(wl_items)}, near-duplicate pairs within class: {len(wl_near_dups)}")

if __name__ == '__main__':
    analyze_phash()
