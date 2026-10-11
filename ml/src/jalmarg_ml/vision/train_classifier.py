import os
import json
import time
import logging
from pathlib import Path
from typing import Dict, Any, List

import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
from torchvision import transforms
from PIL import Image

from .model_architecture import LightRoadWaterNet
from .label_manifest import LabelManifestGenerator

logger = logging.getLogger(__name__)

class RoadWaterDataset(Dataset):
    """PyTorch Dataset for road waterlogging classification."""

    def __init__(self, entries: List[Dict[str, Any]], transform=None):
        self.entries = entries
        self.transform = transform

    def __len__(self):
        return len(self.entries)

    def __getitem__(self, idx):
        entry = self.entries[idx]
        img_path = entry["path"]
        with Image.open(img_path) as img:
            rgb_img = img.convert("RGB")
            if self.transform:
                tensor_img = self.transform(rgb_img)
            else:
                tensor_img = transforms.ToTensor()(rgb_img)
                
        label = 1.0 if entry["target_label"] == LabelManifestGenerator.LABEL_POSITIVE else 0.0
        return tensor_img, torch.tensor(label, dtype=torch.float32)

class ModelTrainer:
    """Trains LightRoadWaterNet baseline on CPU with validation early-stopping and class weighting."""

    def __init__(self, artifacts_dir: str = "ml/artifacts/vision", epochs: int = 15, batch_size: int = 16, lr: float = 0.001, seed: int = 42):
        self.artifacts_dir = Path(artifacts_dir)
        self.artifacts_dir.mkdir(parents=True, exist_ok=True)
        self.epochs = epochs
        self.batch_size = batch_size
        self.lr = lr
        self.seed = seed
        torch.manual_seed(seed)

    def train(self, split_data: Dict[str, Any]) -> Dict[str, Any]:
        train_entries = split_data["train"]
        val_entries = split_data["val"]

        # Training-only augmentations
        train_transforms = transforms.Compose([
            transforms.Resize((256, 256)),
            transforms.RandomHorizontalFlip(p=0.5),
            transforms.RandomRotation(degrees=10),
            transforms.ColorJitter(brightness=0.1, contrast=0.1),
            transforms.ToTensor(),
            transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
        ])

        # Validation transforms (deterministic evaluation)
        val_transforms = transforms.Compose([
            transforms.Resize((256, 256)),
            transforms.ToTensor(),
            transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
        ])

        train_ds = RoadWaterDataset(train_entries, transform=train_transforms)
        val_ds = RoadWaterDataset(val_entries, transform=val_transforms)

        train_loader = DataLoader(train_ds, batch_size=self.batch_size, shuffle=True)
        val_loader = DataLoader(val_ds, batch_size=self.batch_size, shuffle=False)

        # Class imbalance weighting
        pos_count = sum(1 for e in train_entries if e["target_label"] == LabelManifestGenerator.LABEL_POSITIVE)
        neg_count = sum(1 for e in train_entries if e["target_label"] == LabelManifestGenerator.LABEL_NEGATIVE)
        pos_weight = torch.tensor([max(1.0, neg_count / max(1, pos_count))], dtype=torch.float32)

        model = LightRoadWaterNet()
        criterion = nn.BCEWithLogitsLoss(pos_weight=pos_weight)
        optimizer = torch.optim.AdamW(model.parameters(), lr=self.lr, weight_decay=1e-4)

        best_val_f1 = 0.0
        best_epoch = 0
        patience = 5
        no_improve_epochs = 0
        history = []

        start_time = time.time()
        print(f"Starting training on CPU: {len(train_entries)} train samples, {len(val_entries)} val samples, pos_weight={pos_weight.item():.2f}")

        for epoch in range(1, self.epochs + 1):
            model.train()
            train_loss = 0.0
            for X, y in train_loader:
                optimizer.zero_grad()
                logits = model(X)
                loss = criterion(logits, y)
                loss.backward()
                optimizer.step()
                train_loss += loss.item() * len(y)
            train_loss /= len(train_entries)

            # Validation evaluation
            model.eval()
            val_loss = 0.0
            tp, fp, fn, tn = 0, 0, 0, 0
            with torch.no_grad():
                for X, y in val_loader:
                    logits = model(X)
                    loss = criterion(logits, y)
                    val_loss += loss.item() * len(y)
                    preds = (torch.sigmoid(logits) >= 0.5).float()
                    for p_val, y_val in zip(preds, y):
                        if p_val == 1 and y_val == 1: tp += 1
                        elif p_val == 1 and y_val == 0: fp += 1
                        elif p_val == 0 and y_val == 1: fn += 1
                        else: tn += 1

            val_loss /= len(val_entries)
            precision = tp / max(1, tp + fp)
            recall = tp / max(1, tp + fn)
            f1 = 2 * precision * recall / max(1e-6, precision + recall)
            accuracy = (tp + tn) / max(1, tp + tn + fp + fn)

            history.append({
                "epoch": epoch,
                "train_loss": round(train_loss, 4),
                "val_loss": round(val_loss, 4),
                "val_accuracy": round(accuracy, 4),
                "val_precision": round(precision, 4),
                "val_recall": round(recall, 4),
                "val_f1": round(f1, 4)
            })

            print(f"Epoch {epoch:2d}/{self.epochs:2d} | Train Loss: {train_loss:.4f} | Val Loss: {val_loss:.4f} | Val Acc: {accuracy:.4f} | Val F1: {f1:.4f}")

            # Save best checkpoint
            if f1 > best_val_f1:
                best_val_f1 = f1
                best_epoch = epoch
                no_improve_epochs = 0
                torch.save(model.state_dict(), self.artifacts_dir / "best_checkpoint.pt")
            else:
                no_improve_epochs += 1
                if no_improve_epochs >= patience:
                    print(f"Early stopping triggered at epoch {epoch}. Best epoch was {best_epoch} (Val F1: {best_val_f1:.4f})")
                    break

        duration_sec = round(time.time() - start_time, 2)

        training_summary = {
            "model_architecture": "LightRoadWaterNet",
            "epochs_trained": len(history),
            "best_epoch": best_epoch,
            "best_val_f1": round(best_val_f1, 4),
            "training_duration_seconds": duration_sec,
            "device": "cpu",
            "batch_size": self.batch_size,
            "learning_rate": self.lr,
            "checkpoint_path": str(self.artifacts_dir / "best_checkpoint.pt"),
            "history": history
        }

        with open(self.artifacts_dir / "training_summary.json", "w") as f:
            json.dump(training_summary, f, indent=2)

        return training_summary
