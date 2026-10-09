"""Check tracked app files without printing potential credential values."""
import pathlib
import re
import subprocess
import sys

root = pathlib.Path(__file__).resolve().parents[1]
paths = subprocess.check_output(["git", "ls-files", "-z"], cwd=root).decode().split("\0")
patterns = [
    re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    re.compile(rb"gh[pousr]_" + rb"[A-Za-z0-9]{30,}"),
    re.compile(rb"AKIA" + rb"[A-Z0-9]{16}"),
    re.compile(rb"sb_secret_" + rb"[A-Za-z0-9_-]{20,}"),
    re.compile(rb"(?i)(?:service_role_key|jwt_secret|database_password)\s*[:=]\s*[\"'](?![<$])[A-Za-z0-9+/=_-]{20,}[\"']"),
]
failures = []
for path in filter(None, paths):
    file = root / path
    if not file.is_file():
        continue
    if file.name.startswith(".env") and file.name != ".env.example":
        failures.append(path + ": runtime environment file tracked")
    content = file.read_bytes()
    if any(p.search(content) for p in patterns):
        failures.append(path + ": potential credential material")
if failures:
    print("\n".join(failures))
    sys.exit(1)
print("Tracked-file credential guard passed; heuristic scan is not a complete security audit.")
