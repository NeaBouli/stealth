#!/usr/bin/env python3
"""Temporary single-script private Node qualification; never merge this controller."""

import argparse
import base64
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

SOURCE = "5222690fb51cc49b68d25199c9d87a7a8ed0c9a0"
TREE = "fdf7e6cd495cf19db48f63dfa29e135daf212e97"
MAX_BUNDLE = 64 * 1024 * 1024
BASE = "backend/signaling/"
SOURCE_HASHES = {
    BASE + "package.json": "9c589bdde1d06ed10bbdd36f0a96901f9ac5540a7e57129fd940e77836538ac4",
    BASE + "package-lock.json": "6751953df2cb47fad21d4665df45639b35f9303bf38039da314afa5e8e8334cf",
    BASE + "src/__tests__/authenticated_call.test.js": "9438be0cd9e5857e1931b6e7c14740a09d0eca2a4f89f6525498441d66c93138",
    BASE + "src/context.js": "ac6fa9a241ff91fe2ae85ada8296124df77864189f9c10026393e54acc24a60a",
    BASE + "src/state.js": "4fff97ddb7dbf1d1485ecd78844be49f64b95742b47e99457bfa5b18981d71de",
    BASE + "src/data_dir.js": "228648d2769fdd4a341bc8dfa863bcde694005b10319b7ca08a8bc5eaf5d6023",
    BASE + "src/utils/phone.js": "cbeea2117e4fc4d1de938c1068c1237b85bf552122de904b6fa7beb79eeb9a1f",
    BASE + "src/utils/sanitize.js": "8cd51d6f4fb82e4daf49280744f219ad8cec10694b8e405f426871f12fa286ca",
    BASE + "src/utils/json_store.js": "599f9482eb1fe8a027c93b7dbe1eea2928dcac4d5b2a7467780aec2d2798721e",
    BASE + "src/utils/network_key.js": "69a98c7a239099a61e1a872bf413d74d6a117daa2b7568667961c2c184d2aea3",
    BASE + "src/middleware/ip.js": "c261f86f82c33c870b6df1095c75c40102f9f0339c4de80420de2b66ed7a1144",
    BASE + "src/middleware/cors.js": "4f31153c70e539eaedcdd99bfa2f1d7f916ee07750420ac1260251cc0aa71f4d",
    BASE + "src/middleware/admin.js": "e1612d99addfd41a65a3c893517d6cebffc1dc624a24ea194a44e3f7a381edc2",
    BASE + "src/services/identity_registry.js": "6ee2a7ded7137643869bd995ee3a0fc03859658b065af2c64fb315482d56659d",
    BASE + "src/services/fcm_store.js": "81153615020a8b18cabb01c8e5d03bc8f63616f3642c7371df550c72adc3dd33",
    BASE + "src/services/activation_store.js": "d138d93ce41b67992ae533c77d7169bc766b62d7030deeed3b216a790512494a",
    BASE + "src/services/wallet_store.js": "323ac8571f07182152258081824c304b58104c1a7bcdcdf383248bf7d6c69374",
    BASE + "src/services/ifr.js": "ae75bba7c1242a854b4beca81b0038b5e46f16b5fd17bf5575556449f6feb681",
    BASE + "src/security/identity_protocol.js": "4e8692d0621c6cd673a9b7939cdbd66e4365df7d062cefb47a7b942b8f7167bb",
    BASE + "src/security/pkd_registration_limiter.js": "d45ae78cdf7ef65d639cb54f75e298a35b46e2d81450ad0866247df02e587831",
    BASE + "src/security/log_ip.js": "c18657ec1d00f53de463f7b3491d3125408fb201c5ea2725dc9895dae852763e",
    BASE + "src/routes/health.js": "f4f1b74e0df89fa3426baad9d1c12a2bcf24b7a039ab978e5807ecd88c3bc920",
    BASE + "src/routes/pkd.js": "8fef2f65b448674e9f0abab986c9bc195667f1df99174d069040e384eea72e6c",
    BASE + "src/routes/licenses.js": "a6f716bb9be1229bd1bd60c7b07f4f1a587cefccd9d94ca38608accdece74012",
    BASE + "src/ws/index.js": "65db233c5f8012b2def53f1e28ee8addd232cf460240d62d0722c664946aa7db",
    BASE + "src/ws/handlers/index.js": "195f99b902975261be5c614877d962f117d9dbe11441a71395f056cebbbe824c",
    BASE + "src/ws/handlers/register.js": "41cda353459d9a6d27ca9ef7bc61bb589d7a7e13e631f43ce0200875de0786a6",
    BASE + "src/ws/handlers/call.js": "62f58c13aad31f63a00d715d2809f6b5ba51e19e9fd4a3294f8140ea29fab36f",
    BASE + "src/ws/handlers/webrtc.js": "d7df9377022e26b38a14d4e7aad72a6b2cabd17d52f7223fba313aebdf743ede",
    BASE + "src/ws/handlers/phone.js": "d8bd9346fd420393de625eca64c318c501a1bd50a7724e4fc672410f1a74afe0",
    BASE + "src/ws/handlers/subscription.js": "cd2685212ee7a3e99342266875ed5df6c3eb39ee77da5f6a26aa2d1ea5687ea6",
    BASE + "src/ws/handlers/contact.js": "6e911bf3105fc17f6e3113bcc337dec53da71e0b0725e9eea0df7407206dea43",
    BASE + "src/ws/handlers/tester_license.js": "76a40aac7dc5ad5342ece51f41f510af9b95f0ddd4c175e2d6a2901a8ad03726",
    BASE + "src/payments/entitlement_tokens.js": "1bd10d7a0dbf52fc02d028e3d2cbd7f6a8be2816f668c79c83805224a520a2bf",
    BASE + "src/payments/vlabs_fulfillment.js": "ee5bdb7f3fd360b5171bd0382378035fbc0dc043aeb4334d8ac8b882a4d659d8",
    BASE + "src/payments/stripe_handler.js": "c1c40f0212b6ec80f7c49e0b5a3e8ae9d175a41ce9ee6fbd049f5d59d340fade",
    BASE + "src/payments/sold_codes.js": "64ebad8f2d69707a4218b2b87fa0f93b02549b73f46dc2830fa096b08dedeee9",
    BASE + "src/payments/email_handler.js": "50ffd5b2bf0faad7ad70202ec95780992a3ece734ad1e29c1ad56d06863dc58f",
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


def locked_packages(lock: bytes) -> dict:
    packages = json.loads(lock)["packages"]
    selected: dict[str, dict] = {}
    pending = ["node_modules/ws", "node_modules/ethers"]
    while pending:
        path = pending.pop()
        if path in selected:
            continue
        package = packages[path]
        require(re.fullmatch(r"[0-9A-Za-z.+-]+", package["version"]) is not None
                and package["resolved"].startswith("https://registry.npmjs.org/")
                and not package.get("link"))
        integrity = package["integrity"]
        require(integrity.startswith("sha512-"))
        require(len(base64.b64decode(integrity[7:], validate=True)) == 64)
        selected[path] = package
        for name in package.get("dependencies", {}):
            require(re.fullmatch(r"(?:@[a-z0-9._-]+/)?[a-z0-9._-]+", name) is not None)
            parent = Path(path)
            while True:
                candidate = str(parent / "node_modules" / name)
                if candidate in packages:
                    pending.append(candidate)
                    break
                require(parent != Path("."))
                parent = parent.parent
                if parent.name == "node_modules":
                    parent = parent.parent
    require(len(selected) <= 20)
    return selected


def runtime_file(path: Path) -> bool:
    excluded = {"bin", ".bin", "test", "tests", "__tests__", "scripts", "docs",
                "examples", "misc", "node_modules", "config", "configs",
                "secrets", "customer", "customers", "finance"}
    return (not any(p.startswith(".") or p.lower() in excluded for p in path.parts)
            and ".config." not in path.name.lower()
            and (path.suffix in {".js", ".cjs", ".mjs"}
                 or path.name == "package.json"
                 or re.fullmatch(r"LICENSE(?:[.-][A-Za-z0-9]+)?", path.name, re.I) is not None))


def dependency_files(archive: bytes, package: dict, name: str) -> dict[str, bytes]:
    files: dict[str, bytes] = {}
    seen: set[str] = set()
    expanded = 0
    with tarfile.open(fileobj=io.BytesIO(archive), mode="r:gz") as tar:
        for member in tar:
            path = Path(member.name)
            require(not path.is_absolute() and ".." not in path.parts
                    and len(path.parts) >= 1
                    and path.parts[0] == ("node" if name == "@types/node" else "package")
                    and (member.isfile() or member.isdir())
                    and member.name not in seen and len(seen) < 5000)
            seen.add(member.name)
            expanded += member.size
            require(0 <= member.size <= 16 * 1024**2 and expanded <= MAX_BUNDLE)
            if len(path.parts) == 1:
                require(member.isdir() and member.size == 0)
                continue
            relative = Path(*path.parts[1:])
            if not member.isfile() or not runtime_file(relative):
                continue
            stream = tar.extractfile(member)
            require(stream is not None)
            data = stream.read(member.size + 1)
            require(len(data) == member.size and str(relative) not in files)
            files[str(relative)] = data
    metadata = json.loads(files["package.json"])
    require(metadata["name"] == name and metadata["version"] == package["version"])
    return files


def pack(args: argparse.Namespace) -> None:
    repo = Path(args.repo).resolve(strict=True)
    registry = Path(args.registry).absolute()
    require(registry == Path("/Users/gio/.npm/_cacache"))
    git = shutil.which("git")
    require(git is not None)

    def git_read(*arguments: str) -> bytes:
        return subprocess.run([git, "-C", str(repo), *arguments], env=ENV,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                              check=True, timeout=15).stdout

    require(git_read("cat-file", "commit", SOURCE).splitlines()[0] == f"tree {TREE}".encode())
    tree = git_read("ls-tree", "-rz", SOURCE, *sorted(SOURCE_HASHES))
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
    packages = locked_packages(files[BASE + "package-lock.json"])
    for path, package in sorted(packages.items()):
        checksum = base64.b64decode(package["integrity"][7:], validate=True).hex()
        cached = registry / "content-v2/sha512" / checksum[:2] / checksum[2:4] / checksum[4:]
        if not cached.is_file():
            print(f"PACK missing_cache={path}@{package['version']}", file=sys.stderr)
            raise ValueError("Exact locked cache absent")
        data = read_public(cached)
        require(hashlib.sha512(data).hexdigest() == checksum)
        name = path.rsplit("node_modules/", 1)[1]
        for relative, content in dependency_files(data, package, name).items():
            files[BASE + path + "/" + relative] = content
        require(len(files) <= 2000 and sum(map(len, files.values())) <= MAX_BUNDLE)
    manifest = {"format": 2, "source": SOURCE, "tree": TREE,
                "files": {p: digest(data) for p, data in sorted(files.items())},
                "lock_integrities": {p: v["integrity"] for p, v in sorted(packages.items())}}
    files["manifest.json"] = canonical(manifest)
    require(len(files) <= 2000 and sum(map(len, files.values())) + len(files) * 1024 + 10240 <= MAX_BUNDLE)
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
        print(f"PACK source={SOURCE} tree={TREE} packages={len(packages)} scripts=1")
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
REF = "refs/heads/temp/sc-stx24-node-hosted-20261117"
LABEL = "sc-stx24-qualification"


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
            require(member.isfile() and len(files) < 2000
                    and not path.is_absolute() and ".." not in path.parts
                    and str(path) == member.name and member.name not in files
                    and 0 <= member.size <= 16 * 1024 * 1024)
            stream = archive.extractfile(member)
            require(stream is not None)
            data = stream.read(member.size + 1)
            require(len(data) == member.size)
            files[member.name] = data
            require(sum(map(len, files.values())) <= MAX_BUNDLE)
    manifest = json.loads(files.pop("manifest.json"))
    require(manifest["format"] == 2 and manifest["source"] == SOURCE
            and manifest["tree"] == TREE
            and manifest["files"] == {p: digest(v) for p, v in files.items()})
    require({p: digest(files[p]) for p in SOURCE_HASHES}
            == SOURCE_HASHES)
    packages = locked_packages(files[BASE + "package-lock.json"])
    require(manifest["lock_integrities"] == {p: v["integrity"] for p, v in packages.items()})
    for path in set(files) - set(SOURCE_HASHES):
        matches = [p for p in packages if path.startswith(BASE + p + "/")]
        require(bool(matches))
        package_path = max(matches, key=len)
        require(runtime_file(Path(path[len(BASE + package_path + "/"):])))
    for path, package in packages.items():
        metadata = json.loads(files[BASE + path + "/package.json"])
        require(metadata["name"] == path.rsplit("node_modules/", 1)[1]
                and metadata["version"] == package["version"])
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
    toolcache = Path("/opt/hostedtoolcache/node")
    choices = sorted((p for p in toolcache.glob("22.*/x64")
                      if re.fullmatch(r"22\.[0-9]+\.[0-9]+", p.parent.name)
                      and (p / "bin/node").is_file()
                      and p == p.resolve(strict=True)),
                     key=lambda p: tuple(map(int, p.parent.name.split("."))))
    require(bool(choices))
    toolchain = choices[-1]
    version = subprocess.run([str(toolchain / "bin/node"), "--version"], env={},
                             stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                             check=True, timeout=15).stdout.decode().strip()
    require(version == "v" + toolchain.parent.name)
    print("phase=toolchain accepted=true", flush=True)
    tag, asset, expected = (os.environ[key] for key in (
        "SC_RELEASE_TAG", "SC_ASSET", "SC_BUNDLE_SHA256"))
    require(tag == "sc-stx24-node-input-20261116-2315"
            and asset == "sc-stx24-node-input.tar"
            and re.fullmatch(r"[0-9a-f]{64}", expected) is not None)
    # The tag endpoint only resolves published releases; this owned input stays draft.
    release = gh_json(f"repos/{REPO}/releases/447731929")
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
            for path in ("usr", "bin", "lib", "lib64", "etc", "source", "toolchain", "scratch"):
                member = tarfile.TarInfo(path)
                member.type, member.mode = tarfile.DIRTYPE, 0o755
                archive.addfile(member)
        image_id = host_command(["docker", "import", "--change", f"LABEL {LABEL}={name}",
                                 "-", image], stdin=empty.getvalue()).decode().strip()
        try:
            mounts = [(str(root / "backend"), "/source/backend"),
                      (str(toolchain), "/toolchain")]
            mounts += [(str(Path(path).resolve(strict=True)), path)
                       for path in ("/usr", "/bin", "/lib", "/lib64")]
            if Path("/etc/ld.so.cache").is_file():
                mounts.append(("/etc/ld.so.cache", "/etc/ld.so.cache"))
            environment = {"PATH": "/toolchain/bin:/usr/bin:/bin", "HOME": "/scratch/home",
                           "TMPDIR": "/scratch/tmp", "DATA_DIR": "/scratch/data",
                           "STRIPE_PROCESSED_FILE": "/scratch/data/stripe-processed.json",
                           "LC_ALL": "C"}
            script = ("test \"$(cat /sys/fs/cgroup/memory.max)\" = 2147483648; "
                      "test \"$(cat /sys/fs/cgroup/memory.swap.max)\" = 0; "
                      "test \"$(cat /sys/fs/cgroup/pids.max)\" = 64; "
                      "set -- $(cat /sys/fs/cgroup/cpu.max); test \"$1\" = \"$2\"; "
                      "echo SC_CAPS_OK; mkdir -p /scratch/home /scratch/data /scratch/tmp; "
                      "exec node /source/backend/signaling/src/__tests__/authenticated_call.test.js")
            command = ["docker", "create", "--name", name, "--label", f"{LABEL}={name}",
                       "--network", "none", "--read-only", "--user", "65534:65534",
                       "--workdir", "/scratch",
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
                    and config["WorkingDir"] == "/scratch"
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
                state = json.loads(host_command(["docker", "inspect", name]))[0]["State"]
            finally:
                selector.close()
                cleanup()
                if process.poll() is None:
                    process.kill()
                    process.wait(timeout=10)
            text = data.decode("utf8", errors="replace")
            lines = text.splitlines()
            marker = "authenticated_call.test.js: PASS"
            accepted = (code == 0 and state["ExitCode"] == 0
                        and state["Running"] is False and state["OOMKilled"] is False
                        and "SC_CAPS_OK" in lines
                        and bool(lines) and lines[-1] == marker and lines.count(marker) == 1)
            diagnostic = root / "node-authenticated-call.txt"
            diagnostic.write_bytes(bytes(data))
            diagnostic.chmod(0o600)
            require(gh_json(f"repos/{REPO}/releases/{release['id']}")["draft"] is True)
            host_command(["gh", "release", "upload", tag, str(diagnostic), "--repo", REPO], token=True)
            print(f"phase=result source={SOURCE} scripts=1 accepted={str(accepted).lower()}")
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
