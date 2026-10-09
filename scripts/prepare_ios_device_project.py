"""Generate the device archive project without changing simulator verification."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = root / "iosApp/project.yml"
simulator = "../shared/build/bin/iosSimulatorArm64/debugFramework/WorkoutCore.framework"
device = "../shared/build/bin/iosArm64/releaseFramework/WorkoutCore.framework"
text = source.read_text(encoding="utf-8")
if text.count(simulator) != 2:
    raise SystemExit("Unexpected framework configuration; review before generating device project.")
text = text.replace(simulator, device)
if 'WORKOUT_OFFLINE_BETA: "NO"' not in text:
    raise SystemExit("Missing offline-beta build setting; review device archive configuration.")
text = text.replace('WORKOUT_OFFLINE_BETA: "NO"', 'WORKOUT_OFFLINE_BETA: "YES"')
output = root / "iosApp/project-device.generated.yml"
output.write_text(text, encoding="utf-8")
print("Generated iPhone device project with the release arm64 framework. Signing remains disabled.")
