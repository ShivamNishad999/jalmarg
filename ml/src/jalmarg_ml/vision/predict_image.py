import sys
import json
from pathlib import Path

# Add ml/src to sys.path
src_dir = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(src_dir))
root_dir = src_dir.parent.parent

from jalmarg_ml.vision.inference_service import VisionInferenceService

def main():
    if len(sys.argv) < 2:
        print(json.dumps({"error": "Usage: predict_image.py <image_path>"}))
        sys.exit(1)

    image_path = Path(sys.argv[1])
    if not image_path.exists():
        print(json.dumps({"error": f"Image not found: {image_path}"}))
        sys.exit(1)

    checkpoint = root_dir / "ml" / "artifacts" / "vision" / "best_checkpoint.pt"
    service = VisionInferenceService(str(checkpoint) if checkpoint.exists() else None)

    with open(image_path, "rb") as f:
        image_bytes = f.read()

    result = service.predict(image_bytes)
    print(json.dumps(result))

if __name__ == '__main__':
    main()
