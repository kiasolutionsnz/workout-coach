"""Install approved signing inputs only on the ephemeral macOS upload runner."""
import base64
from datetime import datetime
import hashlib
import os
from pathlib import Path
import plistlib
import re
import secrets
import subprocess
import sys

if sys.platform != "darwin":
    raise SystemExit("Signing setup requires the approved macOS runner.")
required = ("APPLE_TEAM_ID", "IOS_CERT_P12_BASE64", "IOS_CERT_PASSWORD", "IOS_PROFILE_BASE64", "ASC_KEY_ID", "ASC_ISSUER_ID", "ASC_PRIVATE_KEY")
if any(not os.environ.get(name) for name in required):
    raise SystemExit("Missing protected TestFlight signing/upload configuration.")
team = os.environ["APPLE_TEAM_ID"]
key_id = os.environ["ASC_KEY_ID"]
if not re.fullmatch(r"[A-Z0-9]{10}", team) or not re.fullmatch(r"[A-Z0-9]{10}", key_id):
    raise SystemExit("Invalid Apple signing or upload identifiers.")
os.umask(0o077)
folder = Path(os.environ["RUNNER_TEMP"]) / "workout-testflight"
folder.mkdir(exist_ok=False)
certificate = folder / "certificate.p12"
profile_file = folder / "profile.mobileprovision"
certificate.write_bytes(base64.b64decode(os.environ["IOS_CERT_P12_BASE64"], validate=True))
profile_file.write_bytes(base64.b64decode(os.environ["IOS_PROFILE_BASE64"], validate=True))
profile = plistlib.loads(subprocess.check_output(["security", "cms", "-D", "-i", str(profile_file)], stderr=subprocess.DEVNULL))
entitlements = profile["Entitlements"]
uuid = profile["UUID"]
if not re.fullmatch(r"[A-Fa-f0-9-]{36}", uuid):
    raise SystemExit("Invalid distribution profile UUID.")
if profile["TeamIdentifier"] != [team] or entitlements.get("application-identifier") != team + ".tech.kiasolutions.workoutcoach":
    raise SystemExit("Distribution profile does not belong to the selected Workout Coach app/team.")
if entitlements.get("get-task-allow") or profile.get("ProvisionedDevices") or profile.get("ProvisionsAllDevices"):
    raise SystemExit("Profile must be an App Store distribution profile.")
if profile["ExpirationDate"] <= datetime.utcnow():
    raise SystemExit("Distribution profile has expired.")
pem = subprocess.check_output(["openssl", "pkcs12", "-in", str(certificate), "-clcerts", "-nokeys", "-passin", "env:IOS_CERT_PASSWORD"], stderr=subprocess.DEVNULL)
der = subprocess.check_output(["openssl", "x509", "-outform", "DER"], input=pem, stderr=subprocess.DEVNULL)
if hashlib.sha256(der).digest() not in [hashlib.sha256(cert).digest() for cert in profile["DeveloperCertificates"]]:
    raise SystemExit("Signing certificate is not authorized by the distribution profile.")
keychain = folder / "signing.keychain-db"
password = secrets.token_urlsafe(32)
print("::add-mask::" + password)
print("::add-mask::" + uuid)
def quiet(*command):
    subprocess.run(command, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
quiet("security", "create-keychain", "-p", password, str(keychain))
quiet("security", "set-keychain-settings", "-lut", "21600", str(keychain))
quiet("security", "unlock-keychain", "-p", password, str(keychain))
quiet("security", "import", str(certificate), "-k", str(keychain), "-P", os.environ["IOS_CERT_PASSWORD"], "-T", "/usr/bin/codesign", "-T", "/usr/bin/security")
quiet("security", "set-key-partition-list", "-S", "apple-tool:,apple:,codesign:", "-s", "-k", password, str(keychain))
quiet("security", "list-keychains", "-d", "user", "-s", str(keychain))
profiles = Path.home() / "Library/MobileDevice/Provisioning Profiles"
profiles.mkdir(parents=True, exist_ok=True)
(profiles / (uuid + ".mobileprovision")).write_bytes(profile_file.read_bytes())
keys = folder / "private_keys"
keys.mkdir()
(keys / ("AuthKey_" + key_id + ".p8")).write_text(os.environ["ASC_PRIVATE_KEY"], encoding="utf-8")
options = {"method":"app-store-connect", "teamID":team, "signingStyle":"manual", "signingCertificate":"Apple Distribution", "provisioningProfiles":{"tech.kiasolutions.workoutcoach":uuid}, "manageAppVersionAndBuildNumber":False}
(folder / "ExportOptions.plist").write_bytes(plistlib.dumps(options))
with Path(os.environ["GITHUB_ENV"]).open("a", encoding="utf-8") as output:
    output.write("WORKOUT_PROFILE_UUID=" + uuid + "\nWORKOUT_SIGN_DEVICE=YES\n")
print("Approved app-specific profile and matching distribution certificate installed on ephemeral runner.")
