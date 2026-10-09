from pathlib import Path
from hashlib import sha256

root = Path(__file__).resolve().parents[1]
expected = "59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a"
for relative in ("androidApp/src/main/assets/pose_landmarker_lite.task", "iosApp/Assets/pose_landmarker_lite.task"):
    content = (root / relative).read_bytes()
    if len(content) != 5777746 or sha256(content).hexdigest() != expected:
        raise SystemExit(f"Model asset mismatch: {relative}")
print("Both bundled model assets match the qualified version and hash.")
