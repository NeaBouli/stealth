#!/usr/bin/env python3
"""Temporary single-run private Rust qualification; never merge this controller."""

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import resource
import selectors
import shutil
import stat
import subprocess
import sys
import tarfile
import tempfile
import time
import tomllib

SOURCE = "5222690fb51cc49b68d25199c9d87a7a8ed0c9a0"
TREE = "fdf7e6cd495cf19db48f63dfa29e135daf212e97"
REGISTRY = "index.crates.io-1949cf8c6b5b557f"
MAX_BUNDLE = 64 * 1024 * 1024
SOURCE_HASHES = {
    "core_crypto/Cargo.lock": "d32eb2f9d43c579de6da02e96eac3d358dfc6d98172f95ee033636e4ce56c67f",
    "core_crypto/Cargo.toml": "b37d8bca590f49f1c2805af659aaaf56807aa86021c0a66a40b9d7b41573a40c",
    "core_crypto/src/aead/mod.rs": "b07c2d0593d06fbc8ff855524f57d07d5a39bba679c48b77b4f492b703cf8050",
    "core_crypto/src/ffi/mod.rs": "49cfb166023b201b481cadb8a4d5850b6b7e167ea085850a580c7652b9cc9a0e",
    "core_crypto/src/identity/mod.rs": "1febf37e94cd96561ef6a871210ea09d76e95eecfe5cfe49910d8e63d72ee0a7",
    "core_crypto/src/lib.rs": "c5c4d6a13d50377ef6a6427bc4e5f44a3d63e610bdf53dafaa085a6350aff167",
    "core_crypto/src/session/mod.rs": "e98fa0207beea6ec0be2f13eba327737ad396d545dea96ea94b514f93864471a",
    "core_crypto/src/utils/mod.rs": "817b404ece9c73dfde75ceaa30eb64a5a55c430fc41ccece8a47438d2dbf8898",
}
# Exact absent archives, not a broad platform-name exclusion.
OMITTED = {
    ("windows-sys", "0.45.0"), ("windows-targets", "0.42.2"),
    *((name, "0.42.2") for name in (
        "windows_aarch64_gnullvm", "windows_aarch64_msvc", "windows_i686_gnu",
        "windows_i686_msvc", "windows_x86_64_gnu", "windows_x86_64_gnullvm",
        "windows_x86_64_msvc")),
}
ENV = {"PATH": "/usr/bin:/bin", "LC_ALL": "C", "HOME": "/nonexistent",
       "GIT_CONFIG_NOSYSTEM": "1", "GIT_CONFIG_GLOBAL": "/dev/null",
       "GIT_NO_REPLACE_OBJECTS": "1", "GIT_OPTIONAL_LOCKS": "0"}


def require(ok: bool) -> None:
    if not ok:
        raise ValueError("PACK metadata refused")


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical(value: object) -> bytes:
    return (json.dumps(value, sort_keys=True, separators=(",", ":")) + "\n").encode()


def read_public(path: Path) -> bytes:
    require(path.is_absolute() and not any(p.is_symlink() for p in (path, *path.parents)))
    info = path.lstat()
    require(stat.S_ISREG(info.st_mode) and info.st_size <= 16 * 1024 * 1024)
    with path.open("rb") as stream:
        data = stream.read(16 * 1024 * 1024 + 1)
    require(len(data) == info.st_size)
    return data


def index_path(name: str) -> str:
    name = name.lower()
    if len(name) < 3:
        return f"{len(name)}/{name}"
    return f"3/{name[0]}/{name}" if len(name) == 3 else f"{name[:2]}/{name[2:4]}/{name}"


def sparse_records(data: bytes) -> dict:
    require(data.startswith(b"\x03\x02\x00\x00\x00"))
    fields = data[5:].split(b"\0")
    require(len(fields) % 2 == 0 and fields[-1] == b"")
    records = {}
    for i in range(1, len(fields) - 1, 2):
        record = json.loads(fields[i + 1])
        version = fields[i].decode()
        require(record["vers"] == version and version not in records)
        records[version] = record
    return records


def pack(args: argparse.Namespace) -> None:
    repo = Path(args.repo).resolve(strict=True)
    registry = Path(args.registry).absolute()
    require(registry == Path.home() / ".cargo/registry")
    git = shutil.which("git")
    require(git is not None)

    def git_read(*arguments: str) -> bytes:
        return subprocess.run([git, "-C", str(repo), *arguments], env=ENV,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                              check=True, timeout=15).stdout

    require(git_read("cat-file", "commit", SOURCE).splitlines()[0] == f"tree {TREE}".encode())
    tree = git_read("ls-tree", "-rz", SOURCE, "core_crypto/Cargo.toml",
                    "core_crypto/Cargo.lock", "core_crypto/src")
    files = {}
    for entry in tree.rstrip(b"\0").split(b"\0"):
        metadata, path_bytes = entry.split(b"\t")
        mode, kind, blob = metadata.decode().split()
        path = path_bytes.decode()
        require(mode == "100644" and kind == "blob" and path in SOURCE_HASHES and path not in files)
        size = int(git_read("cat-file", "-s", blob))
        require(0 <= size <= 5 * 1024 * 1024)
        data = git_read("cat-file", "blob", blob)
        require(len(data) == size and digest(data) == SOURCE_HASHES[path])
        files[path] = data
    require(set(files) == set(SOURCE_HASHES) and sum(map(len, files.values())) <= 5 * 1024 * 1024)
    packages = tomllib.loads(files["core_crypto/Cargo.lock"].decode())["package"]
    packages = [p for p in packages if "source" in p]
    require(len(packages) == 75 and all(
        p["source"] == "registry+https://github.com/rust-lang/crates.io-index"
        and re.fullmatch(r"[a-zA-Z0-9_-]+", p["name"])
        and re.fullmatch(r"[a-zA-Z0-9.+-]+", p["version"])
        and re.fullmatch(r"[0-9a-f]{64}", p["checksum"]) for p in packages))
    indices = {}
    archives = 0
    for package in packages:
        name, version = package["name"], package["version"]
        if (name, version) not in OMITTED:
            path = f"cache/{REGISTRY}/{name}-{version}.crate"
            data = read_public(registry / path)
            require(digest(data) == package["checksum"])
            files["registry/" + path] = data
            archives += 1
        indices.setdefault(name, []).append(package)
    require(archives == 66 and len(indices) == 73)
    for name, expected in sorted(indices.items()):
        path = f"index/{REGISTRY}/.cache/{index_path(name)}"
        records = sparse_records(read_public(registry / path))
        data = bytearray(b'\x03\x02\x00\x00\x00etag: "sc-stx24-offline"\x00')
        for package in sorted(expected, key=lambda p: p["version"]):
            record = records[package["version"]]
            require(record["cksum"] == package["checksum"] and record["name"] == name)
            data.extend(package["version"].encode() + b"\0"
                        + canonical(record).rstrip(b"\n") + b"\0")
        files["registry/" + path] = bytes(data)
    files[f"registry/index/{REGISTRY}/config.json"] = (
        b'{"api":"https://crates.io","dl":"https://static.crates.io/crates"}\n')
    manifest = {"format": 1, "source": SOURCE, "tree": TREE,
                "files": {p: digest(data) for p, data in sorted(files.items())},
                "lock_checksums": {f"{p['name']}@{p['version']}": p["checksum"] for p in packages}}
    files["manifest.json"] = canonical(manifest)
    require(len(files) <= 1000 and sum(map(len, files.values())) + len(files) * 1024 + 10240 <= MAX_BUNDLE)
    output = Path(args.output).absolute()
    # Caller supplies an MCP-prepared private directory outside the checkout.
    require(output.parent.resolve(strict=True) == output.parent
            and output.parent.stat().st_uid == os.getuid()
            and stat.S_IMODE(output.parent.stat().st_mode) == 0o700
            and not output.is_relative_to(repo)
            and not output.is_relative_to(registry))
    mask = os.umask(0o077)
    try:
        with output.open("xb") as stream:
            with tarfile.open(fileobj=stream, mode="w", format=tarfile.USTAR_FORMAT) as tar:
                for path, data in sorted(files.items()):
                    member = tarfile.TarInfo(path)
                    member.size, member.mode = len(data), 0o644
                    member.uid = member.gid = member.mtime = 0
                    tar.addfile(member, io.BytesIO(data))
        require(output.stat().st_size <= MAX_BUNDLE)
        print(f"PACK source={SOURCE} tree={TREE} archives=66 index_versions=75")
        with output.open("rb") as stream:
            print("PACK bundle_sha256=" + hashlib.file_digest(stream, "sha256").hexdigest())
    except Exception:
        # Remove only this newly created private output, never an existing file.
        if "stream" in locals() and output.exists():
            output.unlink()
        raise
    finally:
        os.umask(mask)


REPO = "NeaBouli/stealth"
REF = "refs/heads/temp/sc-stx24-rust-hosted-20261117"
LABEL = "sc-stx24-qualification"
MEDIA_CASES = (
    "two_direction_roundtrip", "rejects_reflection_and_unknown_role",
    "rejects_duplicate_old_and_accepts_in_window_reorder",
    "forged_high_sequence_cannot_poison_window",
    "rejects_wrong_version_type_context_key_and_tag",
    "rejects_truncation_oversize_and_sequence_overflow",
    "replay_bitmap_bit63_roundtrip", "encoder_max_packet_boundary",
)


def host_command(arguments: list[str], *, token: bool = False,
                 stdin: bytes | None = None, timeout: int = 60) -> bytes:
    environment = dict(ENV)
    if token:
        environment["GH_TOKEN"] = os.environ["GH_TOKEN"]
    return subprocess.run(arguments, input=stdin, env=environment,
                          stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                          check=True, timeout=timeout).stdout


def gh_json(path: str) -> dict:
    return json.loads(host_command(["gh", "api", path], token=True))


def owned_name() -> str:
    run = os.environ.get("GITHUB_RUN_ID", "")
    require(re.fullmatch(r"[0-9]{1,20}", run) is not None)
    return "sc-stx24-" + run


def cleanup() -> None:
    name = owned_name()
    for kind, reference in (("container", name), ("image", name + ":empty")):
        result = subprocess.run(["docker", kind, "inspect", reference], env=ENV,
                                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                                timeout=20)
        if result.returncode != 0:
            continue
        item = json.loads(result.stdout)[0]
        require(item["Config"].get("Labels", {}).get(LABEL) == name)
        host_command(["docker", kind, "rm", "--force", reference])


def extract_input(bundle: bytes, root: Path) -> None:
    require(len(bundle) <= MAX_BUNDLE)
    files: dict[str, bytes] = {}
    with tarfile.open(fileobj=io.BytesIO(bundle), mode="r:") as archive:
        for member in archive:
            path = Path(member.name)
            require(member.isfile() and len(files) < 1000
                    and not path.is_absolute() and ".." not in path.parts
                    and str(path) == member.name and member.name not in files
                    and 0 <= member.size <= 16 * 1024 * 1024)
            stream = archive.extractfile(member)
            require(stream is not None)
            data = stream.read(member.size + 1)
            require(len(data) == member.size)
            files[member.name] = data
    manifest = json.loads(files.pop("manifest.json"))
    require(manifest["format"] == 1 and manifest["source"] == SOURCE
            and manifest["tree"] == TREE
            and manifest["files"] == {p: digest(v) for p, v in files.items()})
    require({p: digest(v) for p, v in files.items() if p.startswith("core_crypto/")}
            == SOURCE_HASHES)
    packages = [p for p in tomllib.loads(files["core_crypto/Cargo.lock"].decode())["package"]
                if "source" in p]
    checksums = {f"{p['name']}@{p['version']}": p["checksum"] for p in packages}
    require(len(packages) == 75 and manifest["lock_checksums"] == checksums)
    allowed = set(SOURCE_HASHES) | {f"registry/index/{REGISTRY}/config.json"}
    for package in packages:
        allowed.add(f"registry/index/{REGISTRY}/.cache/{index_path(package['name'])}")
        if (package["name"], package["version"]) not in OMITTED:
            path = f"registry/cache/{REGISTRY}/{package['name']}-{package['version']}.crate"
            allowed.add(path)
            require(digest(files[path]) == package["checksum"])
    require(set(files) == allowed)
    for path, data in files.items():
        target = root / path
        target.parent.mkdir(parents=True, exist_ok=True, mode=0o755)
        with target.open("xb") as stream:
            stream.write(data)
        target.chmod(0o644)


def hosted() -> None:
    require(os.environ.get("GITHUB_REPOSITORY") == REPO
            and os.environ.get("GITHUB_EVENT_NAME") == "workflow_dispatch"
            and os.environ.get("GITHUB_RUN_ATTEMPT") == "1"
            and os.environ.get("SC_RUNNER_ENVIRONMENT") == "github-hosted"
            and os.environ.get("GITHUB_REF") == REF
            and os.environ.get("SC_CONTROLLER_SHA") == os.environ.get("GITHUB_SHA")
            and re.fullmatch(r"[0-9a-f]{40}", os.environ["SC_CONTROLLER_SHA"]) is not None)
    require(host_command(["git", "rev-parse", "HEAD"]).decode().strip()
            == os.environ["SC_CONTROLLER_SHA"])
    print("phase=dispatch accepted=true", flush=True)
    require(shutil.disk_usage(os.environ["RUNNER_TEMP"]).free >= 6 * 1024**3)
    memory = Path("/proc/meminfo").read_text()
    require(int(re.search(r"MemAvailable:\s+(\d+)", memory)[1]) >= 3 * 1024**2)
    require(not host_command(["docker", "ps", "-q"]).strip())
    processes = host_command(["ps", "-e", "-o", "comm="]).decode().splitlines()
    require(not {"cargo", "rustc", "gradle", "java"}.intersection(p.strip() for p in processes))
    print("phase=resources accepted=true", flush=True)
    toolchains = Path(os.environ.get("RUSTUP_HOME", str(Path.home() / ".rustup"))) / "toolchains"
    choices = sorted(p.resolve() for p in toolchains.glob("stable-*-unknown-linux-gnu")
                     if (p / "bin/rustc").is_file() and (p / "bin/cargo").is_file())
    require(len(choices) == 1)
    toolchain = choices[0]
    linker = Path(shutil.which("cc") or "/nonexistent").resolve(strict=True)
    require(str(linker).startswith("/usr/") and linker.is_file())
    print("phase=toolchain accepted=true", flush=True)
    tag, asset, expected = (os.environ[key] for key in (
        "SC_RELEASE_TAG", "SC_ASSET", "SC_BUNDLE_SHA256"))
    require(re.fullmatch(r"sc-stx24-rust-input-[a-z0-9-]{1,50}", tag) is not None
            and asset == "sc-stx24-rust-input.tar"
            and re.fullmatch(r"[0-9a-f]{64}", expected) is not None)
    release = gh_json(f"repos/{REPO}/releases/tags/{tag}")
    require(release["draft"] is True and release["tag_name"] == tag)
    matches = [item for item in release["assets"] if item["name"] == asset]
    require(len(matches) == 1 and 0 < matches[0]["size"] <= MAX_BUNDLE)
    print("phase=private_input_metadata accepted=true", flush=True)
    with tempfile.TemporaryDirectory(prefix="sc-stx24-", dir=os.environ["RUNNER_TEMP"]) as directory:
        root = Path(directory)
        def download_limit() -> None:
            resource.setrlimit(resource.RLIMIT_FSIZE, (MAX_BUNDLE, MAX_BUNDLE))

        download = root / "input.tar"
        with download.open("xb") as stream:
            subprocess.run(["gh", "api", f"repos/{REPO}/releases/assets/{matches[0]['id']}",
                            "-H", "Accept: application/octet-stream"],
                           env={**ENV, "GH_TOKEN": os.environ["GH_TOKEN"]},
                           stdout=stream, stderr=subprocess.DEVNULL, check=True,
                           timeout=120, preexec_fn=download_limit)
        raw = download.read_bytes()
        require(len(raw) == matches[0]["size"] and digest(raw) == expected)
        extract_input(raw, root)
        raw = b""
        print("phase=input_verified accepted=true", flush=True)
        name = owned_name()
        image = name + ":empty"
        empty = io.BytesIO()
        with tarfile.open(fileobj=empty, mode="w", format=tarfile.USTAR_FORMAT) as archive:
            for path in ("usr", "bin", "lib", "lib64", "etc", "source", "cache", "toolchain", "scratch"):
                member = tarfile.TarInfo(path)
                member.type, member.mode = tarfile.DIRTYPE, 0o755
                archive.addfile(member)
        image_id = host_command(["docker", "import", "--change", f"LABEL {LABEL}={name}",
                                 "-", image], stdin=empty.getvalue()).decode().strip()
        try:
            mounts = [(str(root / "core_crypto"), "/source"),
                      (str(root / "registry"), "/cache"), (str(toolchain), "/toolchain")]
            mounts += [(str(Path(path).resolve(strict=True)), path)
                       for path in ("/usr", "/bin", "/lib", "/lib64")]
            if Path("/etc/ld.so.cache").is_file():
                mounts.append(("/etc/ld.so.cache", "/etc/ld.so.cache"))
            environment = {"PATH": "/toolchain/bin:/usr/bin:/bin", "HOME": "/scratch/home",
                           "RUSTC": "/toolchain/bin/rustc", "CARGO_HOME": "/scratch/cargo",
                           "TMPDIR": "/scratch/tmp", "CARGO_TARGET_DIR": "/scratch/target",
                           "CARGO_NET_OFFLINE": "true", "CARGO_BUILD_JOBS": "1",
                           "CARGO_TARGET_X86_64_UNKNOWN_LINUX_GNU_LINKER": str(linker),
                           "CC": str(linker),
                           "CARGO_INCREMENTAL": "0", "LC_ALL": "C"}
            script = ("test \"$(cat /sys/fs/cgroup/memory.max)\" = 2147483648; "
                      "test \"$(cat /sys/fs/cgroup/memory.swap.max)\" = 0; "
                      "test \"$(cat /sys/fs/cgroup/pids.max)\" = 64; "
                      "set -- $(cat /sys/fs/cgroup/cpu.max); test \"$1\" = \"$2\"; "
                      "echo SC_CAPS_OK; mkdir -p /scratch/home /scratch/cargo /scratch/tmp; "
                      "cp -R /cache /scratch/cargo/registry; "
                      "exec cargo test --manifest-path /source/Cargo.toml --locked --offline "
                      "--lib aead -- --test-threads=1")
            command = ["docker", "create", "--name", name, "--label", f"{LABEL}={name}",
                       "--network", "none", "--read-only", "--user", "65534:65534",
                       "--cap-drop", "ALL", "--security-opt", "no-new-privileges=true",
                       "--memory", "2g", "--memory-swap", "2g", "--cpus", "1",
                       "--pids-limit", "64", "--cgroupns", "private", "--ipc", "none",
                       "--log-driver", "none", "--ulimit", "cpu=240:240",
                       "--ulimit", "fsize=134217728:134217728", "--ulimit", "nofile=256:256",
                       "--tmpfs", "/scratch:rw,exec,nosuid,nodev,size=1073741824,uid=65534,gid=65534,mode=0700"]
            for source, destination in mounts:
                command += ["--mount", f"type=bind,src={source},dst={destination},readonly"]
            for key, value in environment.items():
                command += ["--env", f"{key}={value}"]
            target = ["/usr/bin/env", "-i", *[f"{key}={value}" for key, value in environment.items()],
                      "/bin/sh", "-eu", "-c", script]
            host_command(command + [image, *target])
            info = json.loads(host_command(["docker", "inspect", name]))[0]
            config, limits = info["Config"], info["HostConfig"]
            require(info["Image"] == image_id and config["User"] == "65534:65534"
                    and config["Cmd"] == target and len(config["Env"]) == len(environment)
                    and set(config["Env"]) == {f"{k}={v}" for k, v in environment.items()}
                    and limits["ReadonlyRootfs"] is True and limits["NetworkMode"] == "none"
                    and limits["Memory"] == 2 * 1024**3 and limits["MemorySwap"] == 2 * 1024**3
                    and limits["NanoCpus"] == 10**9 and limits["PidsLimit"] == 64
                    and limits["CgroupnsMode"] == "private" and limits["IpcMode"] == "none"
                    and limits["Privileged"] is False and limits["PidMode"] == ""
                    and limits["CapDrop"] == ["ALL"] and not limits["CapAdd"]
                    and limits["SecurityOpt"] == ["no-new-privileges=true"]
                    and limits["LogConfig"]["Type"] == "none"
                    and {u["Name"]: (u["Soft"], u["Hard"]) for u in limits["Ulimits"]}
                    == {"cpu": (240, 240), "fsize": (134217728, 134217728), "nofile": (256, 256)}
                    and limits["Tmpfs"] == {"/scratch": "rw,exec,nosuid,nodev,size=1073741824,uid=65534,gid=65534,mode=0700"}
                    and {(m["Source"], m["Destination"]) for m in info["Mounts"] if m["Type"] == "bind"} == set(mounts)
                    and all(m["RW"] is False for m in info["Mounts"] if m["Type"] == "bind")
                    and not limits.get("Devices") and not limits.get("DeviceRequests"))
            print("phase=admitted source=" + SOURCE + " offline=true memory_mib=2048 pids=64 cpu=1", flush=True)
            started, data = time.monotonic(), bytearray()
            process = subprocess.Popen(["docker", "start", "--attach", name], env=ENV,
                                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            require(process.stdout is not None)
            selector = selectors.DefaultSelector()
            selector.register(process.stdout, selectors.EVENT_READ)
            try:
                while selector.get_map():
                    require(time.monotonic() - started <= 360)
                    for key, _ in selector.select(timeout=1):
                        block = os.read(key.fd, 4096)
                        if not block:
                            selector.unregister(key.fileobj)
                            continue
                        data.extend(block)
                        require(len(data) <= 256 * 1024)
                code = process.wait(timeout=10)
            finally:
                selector.close()
                cleanup()
                if process.poll() is None:
                    process.kill()
                    process.wait(timeout=10)
            text = data.decode("utf8", errors="replace")
            accepted = code == 0 and "SC_CAPS_OK\n" in text and all(
                f"test aead::tests::media_v3_{case} ... ok" in text for case in MEDIA_CASES)
            accepted = accepted and re.search(
                r"test result: ok\. 16 passed; 0 failed; 0 ignored;", text) is not None
            diagnostic = root / "rust-aead.txt"
            diagnostic.write_bytes(bytes(data))
            diagnostic.chmod(0o600)
            require(gh_json(f"repos/{REPO}/releases/{release['id']}")["draft"] is True)
            host_command(["gh", "release", "upload", tag, str(diagnostic), "--repo", REPO], token=True)
            print(f"phase=result source={SOURCE} passed={16 if accepted else 0} accepted={str(accepted).lower()}")
            require(accepted)
        finally:
            cleanup()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="mode", required=True)
    packing = commands.add_parser("pack")
    packing.add_argument("--repo", required=True)
    packing.add_argument("--registry", required=True)
    packing.add_argument("--output", required=True)
    commands.add_parser("hosted")
    commands.add_parser("cleanup")
    args = parser.parse_args()
    try:
        if args.mode == "pack":
            pack(args)
        elif args.mode == "cleanup":
            cleanup()
        else:
            hosted()
    except Exception:
        # Git/IO/metadata exceptions can include private paths or source. Do not relay.
        print("phase=" + args.mode + " accepted=false", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
