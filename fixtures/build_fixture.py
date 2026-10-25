#!/usr/bin/env python3
"""把 fixtures/demo-repo-src/demo-task-app 打包为 fixtures/demo-repo.zip。"""
import zipfile
from pathlib import Path

BASE = Path(__file__).resolve().parents[1]
SRC = BASE / "fixtures" / "demo-repo-src" / "demo-task-app"
OUT = BASE / "fixtures" / "demo-repo.zip"


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as zf:
        for path in sorted(SRC.rglob("*")):
            if path.is_file():
                zf.write(path, "demo-task-app/" + path.relative_to(SRC).as_posix())
    print(f"built {OUT} ({OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
