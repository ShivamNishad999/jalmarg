import random
from typing import Dict, Any, List, Set
from .dataset_audit import DatasetAuditor
from .label_manifest import LabelManifestGenerator

class DatasetSplitter:
    """Performs deterministic, leakage-safe train/val/test splitting by grouping near-duplicate scenes."""

    def __init__(self, random_seed: int = 42, train_ratio: float = 0.70, val_ratio: float = 0.15, test_ratio: float = 0.15):
        self.random_seed = random_seed
        self.train_ratio = train_ratio
        self.val_ratio = val_ratio
        self.test_ratio = test_ratio

    def split(self, manifest_data: Dict[str, Any], audit_report: Dict[str, Any]) -> Dict[str, Any]:
        """Splits usable primary samples into train, val, and test partitions."""
        # 1. Filter to primary usable samples
        usable = [
            e for e in manifest_data["entries"]
            if e["is_primary_sample"] and e["target_label"] in (
                LabelManifestGenerator.LABEL_POSITIVE,
                LabelManifestGenerator.LABEL_NEGATIVE
            )
        ]

        # 2. Map sha256 to perceptual hash from unique images audit
        # Build near-duplicate clusters (connected components)
        adj: Dict[str, Set[str]] = {e["sha256"]: set() for e in usable}
        
        # If near-duplicate pairs were detected, add edges
        for pair in audit_report.get("near_duplicate_pairs", []):
            s1 = pair["image1"]
            s2 = pair["image2"]
            if s1 in adj and s2 in adj:
                adj[s1].add(s2)
                adj[s2].add(s1)

        # Find connected components (scene clusters)
        visited: Set[str] = set()
        clusters: List[List[Dict[str, Any]]] = []
        sha_to_entry = {e["sha256"]: e for e in usable}

        for sha in adj:
            if sha not in visited:
                comp = []
                queue = [sha]
                visited.add(sha)
                while queue:
                    curr = queue.pop(0)
                    if curr in sha_to_entry:
                        comp.append(sha_to_entry[curr])
                    for neighbor in adj.get(curr, set()):
                        if neighbor not in visited:
                            visited.add(neighbor)
                            queue.append(neighbor)
                clusters.append(comp)

        # 3. Stratified cluster assignment for positives and negatives
        # Separate clusters into positive-containing and negative-only
        pos_clusters = [c for c in clusters if any(x["target_label"] == LabelManifestGenerator.LABEL_POSITIVE for x in c)]
        neg_clusters = [c for c in clusters if all(x["target_label"] == LabelManifestGenerator.LABEL_NEGATIVE for x in c)]

        rng = random.Random(self.random_seed)
        rng.shuffle(pos_clusters)
        rng.shuffle(neg_clusters)

        def partition_clusters(cluster_list: List[List[Dict[str, Any]]]):
            train_items = []
            val_items = []
            test_items = []
            
            total_samples = sum(len(c) for c in cluster_list)
            train_target = int(total_samples * self.train_ratio)
            val_target = int(total_samples * self.val_ratio)

            current_train = 0
            current_val = 0

            for c in cluster_list:
                if current_train < train_target:
                    train_items.extend(c)
                    current_train += len(c)
                elif current_val < val_target:
                    val_items.extend(c)
                    current_val += len(c)
                else:
                    test_items.extend(c)

            return train_items, val_items, test_items

        pos_train, pos_val, pos_test = partition_clusters(pos_clusters)
        neg_train, neg_val, neg_test = partition_clusters(neg_clusters)

        train_set = pos_train + neg_train
        val_set = pos_val + neg_val
        test_set = pos_test + neg_test

        # Shuffle each split deterministically
        rng.shuffle(train_set)
        rng.shuffle(val_set)
        rng.shuffle(test_set)

        # 4. Verify zero leakage across splits
        train_shas = set(x["sha256"] for x in train_set)
        val_shas = set(x["sha256"] for x in val_set)
        test_shas = set(x["sha256"] for x in test_set)

        assert not (train_shas & val_shas), "Data leakage detected between train and val!"
        assert not (train_shas & test_shas), "Data leakage detected between train and test!"
        assert not (val_shas & test_shas), "Data leakage detected between val and test!"

        split_summary = {
            "random_seed": self.random_seed,
            "train_count": len(train_set),
            "train_positives": sum(1 for x in train_set if x["target_label"] == LabelManifestGenerator.LABEL_POSITIVE),
            "train_negatives": sum(1 for x in train_set if x["target_label"] == LabelManifestGenerator.LABEL_NEGATIVE),
            "val_count": len(val_set),
            "val_positives": sum(1 for x in val_set if x["target_label"] == LabelManifestGenerator.LABEL_POSITIVE),
            "val_negatives": sum(1 for x in val_set if x["target_label"] == LabelManifestGenerator.LABEL_NEGATIVE),
            "test_count": len(test_set),
            "test_positives": sum(1 for x in test_set if x["target_label"] == LabelManifestGenerator.LABEL_POSITIVE),
            "test_negatives": sum(1 for x in test_set if x["target_label"] == LabelManifestGenerator.LABEL_NEGATIVE),
            "leakage_verification": "VERIFIED_ZERO_LEAKAGE"
        }

        return {
            "summary": split_summary,
            "train": train_set,
            "val": val_set,
            "test": test_set
        }
