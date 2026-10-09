"""Generate the device archive project without changing simulator verification."""
from pathlib import Path
import json
import os
import re

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
if os.environ.get("WORKOUT_SIGN_DEVICE") == "YES":
    team = os.environ["APPLE_TEAM_ID"]
    profile = os.environ["WORKOUT_PROFILE_UUID"]
    if not re.fullmatch(r"[A-Z0-9]{10}", team) or not re.fullmatch(r"[A-Fa-f0-9-]{36}", profile):
        raise SystemExit("Invalid signing identifiers.")
    text = text.replace("        CODE_SIGNING_ALLOWED: NO", "        CODE_SIGNING_ALLOWED: YES\n        CODE_SIGN_STYLE: Manual\n        CODE_SIGN_IDENTITY: Apple Distribution\n        DEVELOPMENT_TEAM: " + json.dumps(team) + "\n        PROVISIONING_PROFILE_SPECIFIER: " + json.dumps(profile), 1)
output = root / "iosApp/project-device.generated.yml"
output.write_text(text, encoding="utf-8")
print("Generated offline iPhone project with the release arm64 framework; signing mode comes from explicit configuration.")
