#!/usr/bin/env python3
"""Verify compiled Android .so artifacts under rustJniLibs against 16 KiB alignment,
machine architecture, DT_NEEDED dependencies, GNU Build-ID, and byte sizes.
"""

from __future__ import annotations

import os
import re
import subprocess
import sys
from pathlib import Path

ABI_EXPECTATIONS = {
    "arm64-v8a": ("AArch64", True),
    "x86_64": ("Advanced Micro Devices X86-64", True),
    "armeabi-v7a": ("ARM", False),
}


def find_llvm_readelf() -> Path:
    ndk_home = os.environ.get("ANDROID_NDK_HOME")
    if not ndk_home:
        local_app_data = os.environ.get("LOCALAPPDATA", "")
        candidate = Path(local_app_data) / "Android" / "Sdk" / "ndk" / "28.2.13676358"
        if candidate.exists():
            ndk_home = str(candidate)
    if not ndk_home:
        raise RuntimeError("ANDROID_NDK_HOME not set and default NDK r28 not found")

    prebuilt_root = Path(ndk_home) / "toolchains" / "llvm" / "prebuilt"
    for plat in ("windows-x86_64", "linux-x86_64", "darwin-x86_64"):
        exe_name = "llvm-readelf.exe" if plat.startswith("windows") else "llvm-readelf"
        tool = prebuilt_root / plat / "bin" / exe_name
        if tool.exists():
            return tool
    raise RuntimeError(f"llvm-readelf not found under {prebuilt_root}")


def run_readelf(tool: Path, *args: str) -> str:
    res = subprocess.run(
        [str(tool), *args],
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    return res.stdout


def verify_abi(tool: Path, lib_dir: Path, abi: str, expected_machine: str, require_16k: bool) -> None:
    so_path = lib_dir / abi / "libbookgen_core.so"
    if not so_path.is_file():
        raise AssertionError(f"Missing native library: {so_path}")

    header = run_readelf(tool, "-h", str(so_path))
    if "DYN (Shared object file)" not in header:
        raise AssertionError(f"{so_path}: expected ELF Type DYN")
    if expected_machine not in header:
        raise AssertionError(f"{so_path}: expected Machine '{expected_machine}' in:\n{header}")

    phdrs = run_readelf(tool, "-lW", str(so_path))
    load_aligns = set()
    for line in phdrs.splitlines():
        parts = line.strip().split()
        if parts and parts[0] == "LOAD":
            load_aligns.add(parts[-1].lower())
    if not load_aligns:
        raise AssertionError(f"{so_path}: no LOAD segments found")
    if require_16k:
        for align in load_aligns:
            val = int(align, 16)
            if val < 0x4000 or (val % 0x4000) != 0:
                raise AssertionError(f"{so_path}: 64-bit LOAD alignment {align} is not 16 KiB aligned")

    dyn = run_readelf(tool, "-d", str(so_path))
    needed = re.findall(r"\(NEEDED\)\s+Shared library:\s+\[([^\]]+)\]", dyn)
    allowed_sys = {"libc.so", "libm.so", "libdl.so", "liblog.so", "libandroid.so", "libz.so"}
    unexpected = [lib for lib in needed if lib not in allowed_sys]
    if unexpected:
        raise AssertionError(f"{so_path}: unexpected DT_NEEDED libraries: {unexpected}")

    notes = run_readelf(tool, "-n", str(so_path))
    m_build_id = re.search(r"Build ID:\s*([0-9a-fA-F]+)", notes)
    if not m_build_id:
        raise AssertionError(f"{so_path}: missing GNU Build ID note")

    size_kb = so_path.stat().st_size / 1024.0
    print(
        f"[OK] {abi}: machine={expected_machine}, load_align={sorted(load_aligns)}, "
        f"needed={needed}, build_id={m_build_id.group(1)[:12]}..., size={size_kb:.1f} KiB"
    )


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    lib_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else root / "app" / "build" / "generated" / "rustJniLibs"
    tool = find_llvm_readelf()
    checked = 0
    for abi, (machine, req_16k) in ABI_EXPECTATIONS.items():
        if (lib_dir / abi).exists():
            verify_abi(tool, lib_dir, abi, machine, req_16k)
            checked += 1
    if checked == 0:
        raise AssertionError(f"No ABI directories found under {lib_dir}")
    print(f"Verified {checked} Android ABI artifacts cleanly.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
