"""Checks extras/BSL_Trans_Realm.txt against a BSL Shaders zip: every option must exist and every value must be one the
pack offers. BSL (through Iris) silently ignores an option with a value it doesn't offer, which is how the old preset's
CLOUDS=false, AURORA=true and CG_TM=0.20 did nothing.

Usage, from the repository root:  python3 tools/check_bsl_preset.py path/to/BSL_v10.1.8.zip [preset.txt]
"""
import re
import sys
import zipfile


def options(settings):
    """BSL's options from lib/settings.glsl: name -> (default, allowed values or None for a switch)."""
    found = {}
    for line in settings.splitlines():
        m = re.match(r'\s*(//)?\s*#define\s+([A-Z0-9_]+)\s*([^\s/]+)?\s*(?://\s*\[([^\]]*)\])?', line)
        if not m:
            continue
        commented, name, value, allowed = m.groups()
        if value is None:
            found[name] = ("false" if commented else "true", None)
        else:
            found[name] = (value, allowed.split() if allowed else [value])
    return found


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    with zipfile.ZipFile(sys.argv[1]) as pack:
        name = next(n for n in pack.namelist() if n.endswith("shaders/lib/settings.glsl"))
        known = options(pack.read(name).decode("utf-8"))
    preset = sys.argv[2] if len(sys.argv) > 2 else "extras/BSL_Trans_Realm.txt"
    problems = 0
    for number, line in enumerate(open(preset, encoding="utf-8"), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, _, value = line.partition("=")
        if key not in known:
            print(f"{preset}:{number}: {key} isn't an option in this BSL")
            problems += 1
            continue
        default, allowed = known[key]
        if allowed is None and value not in ("true", "false"):
            print(f"{preset}:{number}: {key} is a switch: true or false, not {value}")
            problems += 1
        elif allowed is not None and value not in allowed:
            print(f"{preset}:{number}: {key}={value} isn't offered; pick one of: {' '.join(allowed)}")
            problems += 1
    print(f"{len(known)} BSL options; {problems} problem(s) in {preset}")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
