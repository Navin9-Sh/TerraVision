"""One-time, OFFLINE export: best_model.pth -> TorchScript (.pt).

Run this exactly once (or whenever the model is retrained) on your local machine.
It has NO role in the live application — the Spring Boot backend loads only the
.pt file this script produces, via Deep Java Library's PyTorch engine. Python
never runs as part of the running system.

The architecture being exported is a stock torchvision ResNet-50 with only the
final `fc` layer replaced (see the original TerraVision/app.py), so torch.jit.trace
produces a clean, static graph -- there is no data-dependent control flow for
tracing to get wrong.

Usage (from the model-export/ directory):
    python -m venv venv
    venv\\Scripts\\activate            (Windows)  /  source venv/bin/activate (macOS/Linux)
    pip install -r requirements.txt
    python export_model.py

Defaults assume the standard repo layout (TerraVision/best_model.pth,
TerraVision/classes.json, output into ../backend/model/). Override with flags if
your layout differs.
"""

import argparse
import json
import shutil
from pathlib import Path

import torch
import torch.nn as nn
from torchvision import models


def load_model(weights_path: Path, num_classes: int) -> nn.Module:
    model = models.resnet50(weights=None)
    model.fc = nn.Linear(model.fc.in_features, num_classes)
    state_dict = torch.load(weights_path, map_location="cpu")
    model.load_state_dict(state_dict)
    model.eval()
    return model


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--weights", default="../TerraVision/best_model.pth",
                         help="Path to the trained PyTorch state_dict (best_model.pth)")
    parser.add_argument("--classes", default="../TerraVision/classes.json",
                         help="Path to classes.json (ordered list of class names)")
    parser.add_argument("--out-dir", default="../backend/model",
                         help="Directory to write the traced model + classes.json copy into")
    parser.add_argument("--model-name", default="terravision-resnet50",
                         help="Base filename (without .pt) DJL will look for in --out-dir")
    args = parser.parse_args()

    weights_path = Path(args.weights).resolve()
    classes_path = Path(args.classes).resolve()
    out_dir = Path(args.out_dir).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    if not weights_path.exists():
        raise FileNotFoundError(f"Model weights not found: {weights_path}")
    if not classes_path.exists():
        raise FileNotFoundError(f"classes.json not found: {classes_path}")

    class_names = json.loads(classes_path.read_text())
    print(f"Loaded {len(class_names)} classes: {class_names}")

    print(f"Loading weights from {weights_path} ...")
    model = load_model(weights_path, len(class_names))

    print("Tracing model to TorchScript ...")
    dummy_input = torch.zeros(1, 3, 224, 224, dtype=torch.float32)
    traced = torch.jit.trace(model, dummy_input)
    traced = torch.jit.freeze(traced)

    with torch.no_grad():
        eager_out = model(dummy_input)
        traced_out = traced(dummy_input)
        max_diff = (eager_out - traced_out).abs().max().item()
    print(f"Max abs difference between eager and traced output: {max_diff:.2e}")
    assert max_diff < 1e-5, "Traced model diverges from eager model -- do not ship this artifact"

    out_pt = out_dir / f"{args.model_name}.pt"
    traced.save(str(out_pt))
    shutil.copy(classes_path, out_dir / "classes.json")

    print(f"\nSaved TorchScript model to: {out_pt}")
    print(f"Copied classes.json to:     {out_dir / 'classes.json'}")
    print("\nDone. The Spring Boot app's terravision.inference.model-dir property")
    print(f"should point at: {out_dir}")


if __name__ == "__main__":
    main()
