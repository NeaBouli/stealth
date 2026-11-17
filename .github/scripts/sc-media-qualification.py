#!/usr/bin/env python3
"""Temporary source-pinned JNI/ART qualification; NOT_READY, NEVER MERGE."""

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
import socket
import stat
import subprocess
import sys
import tarfile
import tempfile
import time
import xml.etree.ElementTree as ET
import zipfile

SOURCE = "d1eefa872cf9995e840e5ed671f14df983f75c87"
TREE = "2bf04aa294f24fe246ee8ae142b19699fd5025a9"
SOURCE_TABLE_SHA256 = "66e57da8cc2b24e4d6a56354b52ec870299332a3562149947358baa2df7f13f9"
SOURCE_COUNT = 799
VERIFICATION_SHA256 = "51e401f56ef9e68cf1fa279b731fc7b94d1404fbd4d1d7a31a5ec46b3965c4f5"
NATIVE = {
    "arm64-v8a": "2782617de7793cc0040bae2ffff3588350e78734dffe91aaa83edf1f461e4f9f",
    "armeabi-v7a": "01b0ec8a1fda6363baf30eda275cd142f4857743ced8b012798c47afd74d4280",
    "x86_64": "4a932002caab2e585f56950f5ea35d587d4f13b588c62549f5ad513eaf207c59",
}
NDK_REVISION = "27.0.12077973"
STRIP_TOOL = "toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
NATIVE_ENCODINGS = {
    "raw": (NATIVE["x86_64"], 618624),
    "ndk27-strip-unneeded": (
        "abc5e77e3f17c7137ef94700266d7969643dc6f8f4a48f6097ca53befcd8d0ff", 477088),
}
STRIP_ARGV = ["--strip-unneeded", "-o", "stripped.so", "raw.so"]
STRIP_LIMITS = {"wall_seconds": 30, "cpu_seconds": 30,
                "file_bytes": 512 * 1024, "output_bytes": 512 * 1024}
GUARD = "tools/verify_android_crypto_exports.sh"
GUARD_SHA256 = "a20d3740d7ef133e8ea9c4937440eed603468b6331e234908e53f85f6401d964"
TEST_CLASS = "com.securecall.app.CoreCryptoMediaV3InstrumentedTest"
TEST_SOURCE = "client_android/app/src/androidTest/java/com/securecall/app/CoreCryptoMediaV3InstrumentedTest.kt"
EXPECTED_METHODS = frozenset({
    "bidirectionalRoundtripUsesDirectionKeysAndCommitsReplayState",
    "reflectedOrWrongRoleFramesAreRejected", "wrongContextKeyOrTagIsRejected",
    "replayWindowAndForgedHighSequenceDoNotPoisonState", "negativeJvmBitmapPreservesBit63",
    "plaintextSizeBoundaries4000And4001", "sequenceZeroNegativeAndMaxBoundaries",
    "legacyAndMalformedEnvelopesAreRejected",
})
# Parent binds only the reviewed diff; host Linux strip proof remains mandatory.
ADMISSION = "PARENT_REVIEWED"
REF = "refs/heads/temp/sc-jni-art-resume-20261117"
RELEASE = 448597305
TAG = "sc-jni-art-resume-20261117"
ASSET = "sc-jni-art-input.tar"
EMULATOR_REVISION = "37.2.13"
IMAGE_REVISION = "27"
FIXTURE_SHA256 = "a466488db1f666da3f1b941ad4c389a6b7940d5c93662adf25f77fb781fd63c7"
PROVIDER = "client_android/app/google-services.json"
PACKAGES = ("com.securecall.app.free", "com.securecall.app.premium", "com.securecall.app.pro")
WRAPPER_SHA256 = "46ac66d47f30f3dacfdf306e0b714a91a34fb94a22ba0a744b280933f47bc0cf"
WRAPPER_PATH = "wrapper/dists/gradle-9.8.0-all/619e6l8zt76uzaekhqdol0b60/gradle-9.8.0-all.zip"
MAX_BUNDLE = 64 * 1024**2
MAX_OUTPUT = 512 * 1024
PUBLIC_DIAGNOSTIC_ASSET = "public-preparation-diagnostic.json"
MAX_PUBLIC_TAIL = 8192
MAX_PUBLIC_DIAGNOSTIC = 16 * 1024
SYSTEM_JAVA_CACERTS = Path("/etc/ssl/certs/adoptium/cacerts")
MAX_PUBLIC_CACERTS = 4 * 1024**2
REPO = "NeaBouli/stealth"
LABEL = "sc-stx24-qualification"
ENV = {"PATH": "/usr/bin:/bin", "LC_ALL": "C", "HOME": "/nonexistent",
       "GIT_CONFIG_NOSYSTEM": "1", "GIT_CONFIG_GLOBAL": "/dev/null",
       "GIT_NO_REPLACE_OBJECTS": "1", "GIT_OPTIONAL_LOCKS": "0"}


class Refusal(ValueError):
    """Fixed trusted reason, never text from source, tool or target exceptions."""


class PublicPreparationFailure(Refusal):
    def __init__(self, payload: bytes) -> None:
        super().__init__("public_container_failure")
        self.payload = payload


def require(ok: bool, blocker: str = "metadata") -> None:
    if not ok:
        raise Refusal(blocker)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical(value: object) -> bytes:
    return (json.dumps(value, sort_keys=True, separators=(",", ":")) + "\n").encode()


def provider_fixture() -> bytes:
    data = canonical({
        "project_info": {"project_number": "000000000000",
                         "project_id": "qualification-only-invalid",
                         "storage_bucket": "qualification-only-invalid.invalid"},
        "client": [{"client_info": {
            "mobilesdk_app_id": "1:000000000000:android:" + str(index).zfill(16),
            "android_client_info": {"package_name": package}},
            "oauth_client": [], "api_key": [{"current_key": "QUALIFICATION_ONLY_INVALID_API_KEY"}],
            "services": {"appinvite_service": {"other_platform_oauth_client": []}}}
            for index, package in enumerate(PACKAGES, 1)], "configuration_version": "1"})
    require(digest(data) == FIXTURE_SHA256, "fixture_pin")
    return data


def source_path(path: str) -> bool:
    if path == GUARD:
        return True
    p = Path(path)
    forbidden = {"local.properties", "credentials", "secrets", ".git", ".fleet",
                 ".gradle", "build", "customer", "customers", "operator"}
    return (path.startswith("client_android/") and str(p) == path and ".." not in p.parts
            and not any(part.lower() in forbidden or part.startswith(".") for part in p.parts)
            and p.suffix.lower() not in {".jks", ".keystore", ".key", ".p12"}
            and (p.suffix.lower() != ".pem" or path == "client_android/app/src/main/res/raw/isrg_root_x1.pem")
            and (p.suffix.lower() != ".jar" or path == "client_android/gradle/wrapper/gradle-wrapper.jar"))


def methods(data: bytes, count: int) -> set[str]:
    text = data.decode("utf8")
    names = re.findall(r"(?m)^    @Test\s+fun ([A-Za-z0-9_]+)\(", text)
    require(len(names) == len(set(names)) == count and "@Ignore" not in text
            and "@RunWith(AndroidJUnit4::class)" in text
            and set(names) == EXPECTED_METHODS, "test_source_contract")
    return set(names)


def source_contract(files: dict[str, bytes]) -> None:
    require(digest(files[GUARD]) == GUARD_SHA256, "export_guard_pin")
    require(digest(files["client_android/gradle/verification-metadata.xml"]) == VERIFICATION_SHA256,
            "verification_pin")
    for abi, checksum in NATIVE.items():
        require(digest(files[f"client_android/app/src/main/jniLibs/{abi}/libsecurecall_core_crypto.so"])
                == checksum, "native_pin")
    methods(files[TEST_SOURCE], 8)


def pack(args: argparse.Namespace) -> None:
    repo = Path(args.repo).resolve(strict=True)
    git = shutil.which("git")
    require(git is not None)

    def git_read(*arguments: str) -> bytes:
        return subprocess.run([git, "-C", str(repo), *arguments], env=ENV,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                              check=True, timeout=15).stdout

    require(git_read("cat-file", "commit", SOURCE).splitlines()[0] == f"tree {TREE}".encode())
    files: dict[str, bytes] = {}
    for entry in git_read("ls-tree", "-rz", SOURCE, "client_android", GUARD).rstrip(b"\0").split(b"\0"):
        metadata, raw_path = entry.split(b"\t")
        mode, kind, blob = metadata.decode().split()
        path = raw_path.decode()
        require(mode in {"100644", "100755"} and kind == "blob" and source_path(path) and path not in files)
        if path == PROVIDER:
            # Only schema/public packages are consulted; original values never enter files.
            config = json.loads(git_read("cat-file", "blob", blob))
            require(set(config) == {"project_info", "client", "configuration_version"}
                    and tuple(c["client_info"]["android_client_info"]["package_name"]
                              for c in config["client"]) == PACKAGES, "provider_schema")
            del config
            continue
        size = int(git_read("cat-file", "-s", blob))
        require(0 <= size <= 16 * 1024**2)
        data = git_read("cat-file", "blob", blob)
        require(len(data) == size)
        files[path] = data
        require(len(files) <= 1000 and sum(map(len, files.values())) <= MAX_BUNDLE)
    table = {p: digest(data) for p, data in files.items()}
    require(len(table) == SOURCE_COUNT and digest(canonical(table)) == SOURCE_TABLE_SHA256, "source_table_pin")
    source_contract(files)
    files[PROVIDER] = provider_fixture()
    files["source-table.json"] = canonical(table)
    files["manifest.json"] = canonical({
        "format": 3, "source": SOURCE, "tree": TREE,
        "source_table_sha256": SOURCE_TABLE_SHA256, "fixture_sha256": FIXTURE_SHA256,
        "provenance": "exact-code-with-synthetic-provider-config",
        "files": {p: digest(data) for p, data in files.items()}})
    require(sum(map(len, files.values())) + len(files) * 1024 + 10240 <= MAX_BUNDLE)
    output = Path(args.output).absolute()
    require(output.parent.resolve(strict=True) == output.parent
            and output.parent.stat().st_uid == os.getuid()
            and stat.S_IMODE(output.parent.stat().st_mode) == 0o700 and not output.is_relative_to(repo))
    mask, created = os.umask(0o077), False
    try:
        with output.open("xb") as stream:
            created = True
            with tarfile.open(fileobj=stream, mode="w", format=tarfile.USTAR_FORMAT) as archive:
                for path, data in sorted(files.items()):
                    member = tarfile.TarInfo(path)
                    member.size, member.mode = len(data), 0o644
                    member.uid = member.gid = member.mtime = 0
                    archive.addfile(member, io.BytesIO(data))
        require(output.stat().st_size <= MAX_BUNDLE)
        with output.open("rb") as stream:
            checksum = hashlib.file_digest(stream, "sha256").hexdigest()
        print(f"PACK source={SOURCE} code_files={SOURCE_COUNT} synthetic_provider=true bundle_sha256={checksum}")
    except Exception:
        if created:
            output.unlink(missing_ok=True)
        raise
    finally:
        os.umask(mask)


def extract_input(bundle: bytes, root: Path) -> None:
    require(len(bundle) <= MAX_BUNDLE)
    files: dict[str, bytes] = {}
    expanded = 0
    with tarfile.open(fileobj=io.BytesIO(bundle), mode="r:") as archive:
        for member in archive:
            p = Path(member.name)
            require(member.isfile() and len(files) < 1000 and not p.is_absolute()
                    and ".." not in p.parts and str(p) == member.name and member.name not in files
                    and member.mode == 0o644 and member.uid == member.gid == member.mtime == 0
                    and not member.pax_headers and member.linkname == ""
                    and 0 <= member.size <= 16 * 1024**2)
            stream = archive.extractfile(member)
            require(stream is not None)
            data = stream.read(member.size + 1)
            require(len(data) == member.size)
            files[member.name] = data
            expanded += member.size
            require(expanded <= MAX_BUNDLE)
    normalized = io.BytesIO()
    with tarfile.open(fileobj=normalized, mode="w", format=tarfile.USTAR_FORMAT) as archive:
        for path, data in sorted(files.items()):
            member = tarfile.TarInfo(path)
            member.size, member.mode = len(data), 0o644
            member.uid = member.gid = member.mtime = 0
            archive.addfile(member, io.BytesIO(data))
    require(normalized.getvalue() == bundle, "normalized_ustar")
    manifest = json.loads(files.pop("manifest.json"))
    require(manifest == {"format": 3, "source": SOURCE, "tree": TREE,
                        "source_table_sha256": SOURCE_TABLE_SHA256, "fixture_sha256": FIXTURE_SHA256,
                        "provenance": "exact-code-with-synthetic-provider-config",
                        "files": {p: digest(data) for p, data in files.items()}})
    table_bytes = files.pop("source-table.json")
    require(digest(table_bytes) == SOURCE_TABLE_SHA256, "source_table_pin")
    table = json.loads(table_bytes)
    require(len(table) == SOURCE_COUNT and canonical(table) == table_bytes
            and all(source_path(p) and p != PROVIDER for p in table)
            and files.pop(PROVIDER) == provider_fixture(), "fixture_or_source_paths")
    require(table == {p: digest(data) for p, data in files.items()}, "source_file_hashes")
    source_contract(files)
    files[PROVIDER] = provider_fixture()
    for path, data in files.items():
        target = root / path
        target.parent.mkdir(parents=True, exist_ok=True, mode=0o755)
        with target.open("xb") as stream:
            stream.write(data)
        target.chmod(0o644)


def host_command(arguments: list[str], *, token: bool = False,
                 stdin: bytes | None = None, timeout: int = 60) -> bytes:
    environment = dict(ENV)
    if token:
        environment["GH_TOKEN"] = os.environ["GH_TOKEN"]
    return subprocess.run(arguments, input=stdin, env=environment, stdout=subprocess.PIPE,
                          stderr=subprocess.DEVNULL, check=True, timeout=timeout).stdout


def gh_json(path: str) -> dict:
    return json.loads(host_command(["gh", "api", path], token=True))


def owned_name() -> str:
    run = os.environ.get("GITHUB_RUN_ID", "")
    require(re.fullmatch(r"[0-9]{1,20}", run) is not None)
    return "sc-stx24-" + run


def user_manager(arguments: list[str], *, check: bool = True) -> subprocess.CompletedProcess:
    environment = {**ENV, "XDG_RUNTIME_DIR": f"/run/user/{os.getuid()}"}
    return subprocess.run(["systemctl", "--user", *arguments], env=environment,
                          stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                          check=check, timeout=30)


def cleanup_headless() -> None:
    name = owned_name()
    record = Path(os.environ["RUNNER_TEMP"]) / (name + ".scope-owned")
    if not record.exists():
        return
    regular(record, 128, "scope_owner_record")
    require(record.stat().st_uid == os.getuid() and record.read_text() == name + "\n",
            "scope_owner_record")
    unit = name + ".scope"
    group_record = Path(os.environ["RUNNER_TEMP"]) / (name + ".scope-cgroup")
    group = ""
    if group_record.exists():
        regular(group_record, 512, "scope_group_record")
        require(group_record.stat().st_uid == os.getuid(), "scope_group_record")
        group = group_record.read_text().strip()
        require(group.startswith("/user.slice/") and ".." not in group
                and group.endswith("/" + unit), "scope_cgroup_owner")
    description = user_manager(["show", unit, "--property=Description", "--value"], check=False)
    if description.returncode == 0 and description.stdout.strip():
        require(description.stdout.decode().strip() == "SC JNI ART " + name, "scope_owner")
        actual = user_manager(["show", unit, "--property=ControlGroup", "--value"]).stdout.decode().strip()
        require(actual.startswith("/user.slice/") and ".." not in actual
                and actual.endswith("/" + unit) and (not group or group == actual), "scope_cgroup_owner")
        group = actual
        user_manager(["stop", unit])
    if group:
        events = Path("/sys/fs/cgroup" + group) / "cgroup.events"
        deadline = time.monotonic() + 5
        while events.exists() and "populated 1" in events.read_text():
            require(time.monotonic() < deadline, "scope_cgroup_populated")
            time.sleep(0.1)
    group_record.unlink(missing_ok=True)
    record.unlink()


def cleanup() -> None:
    cleanup_headless()
    name, container_id = owned_name(), None
    require(host_command(["docker", "info", "--format", "{{.CgroupVersion}}"]).strip() == b"2", "cleanup_daemon")
    for kind, reference in (("container", name), ("image", name + ":empty")):
        result = subprocess.run(["docker", kind, "inspect", reference], env=ENV,
                                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=20)
        if result.returncode == 0:
            item = json.loads(result.stdout)[0]
            require(item["Config"].get("Labels", {}).get(LABEL) == name, "cleanup_owner")
            if kind == "container":
                container_id = item["Id"]
            host_command(["docker", kind, "rm", "--force", reference])
    record = Path(os.environ["RUNNER_TEMP"]) / (name + ".cgroup-id")
    if record.exists():
        saved_id = record.read_text().strip()
        require(container_id is None or container_id == saved_id, "cleanup_identity")
        container_id = saved_id
    if container_id is not None:
        require(re.fullmatch(r"[0-9a-f]{64}", container_id) is not None)
        # runc removes the owned descendants; also check after a previous partial cleanup.
        groups = (Path("/sys/fs/cgroup/system.slice") / f"docker-{container_id}.scope",
                  Path("/sys/fs/cgroup/docker") / container_id)
        deadline = time.monotonic() + 5
        while True:
            populated = False
            for group in groups:
                try:
                    events = (group / "cgroup.events").read_text()
                except FileNotFoundError:
                    continue
                populated |= "populated 1" in events
            if not populated:
                break
            require(time.monotonic() < deadline, "cleanup_cgroup_populated")
            time.sleep(0.1)
    record.unlink(missing_ok=True)


def regular(path: Path, limit: int, blocker: str) -> None:
    require(path.is_absolute() and not any(p.is_symlink() for p in (path, *path.parents))
            and path.is_file() and stat.S_ISREG(path.stat().st_mode)
            and 0 < path.stat().st_size <= limit, blocker)


def stage_cache(root: Path, source: Path, *, cache: Path | None = None,
                seed: bool = False, direct: bool = True) -> Path:
    # The public cache is restored before private inputs arrive. Never copy its
    # home/init/properties/daemon/config-cache; authenticate selected artifacts.
    cache = cache if cache is not None else Path("/home/runner/.gradle")
    root.mkdir(parents=True, exist_ok=True, mode=0o755)
    wrapper = cache / WRAPPER_PATH
    if not wrapper.is_file():
        wrapper = Path(os.environ["RUNNER_TEMP"]) / "sc-gradle-9.8.0-all.zip"
    regular(wrapper, 300 * 1024**2, "cached_gradle_9_8_0_zip_absent")
    with wrapper.open("rb") as stream:
        require(hashlib.file_digest(stream, "sha256").hexdigest() == WRAPPER_SHA256, "wrapper_checksum")
    shutil.copyfile(wrapper, root / "wrapper.zip")
    (root / "wrapper.zip").chmod(0o644)
    ns = {"v": "https://schema.gradle.org/dependency-verification"}
    xml = ET.parse(source / "client_android/gradle/verification-metadata.xml")
    allowed: dict[tuple[str, str, str, str], set[str]] = {}
    for c in xml.findall("v:components/v:component", ns):
        for a in c.findall("v:artifact", ns):
            allowed[(c.attrib["group"], c.attrib["name"], c.attrib["version"], a.attrib["name"])] = {
                s.attrib["value"] for s in a.findall("v:sha256", ns)}
    origin, staged = cache / "caches/modules-2", root / "dependencies/modules-2"
    staged.mkdir(parents=True, exist_ok=True, mode=0o755)
    total, count = 0, 0
    present: set[tuple[str, str, str]] = set()
    for path in sorted((origin / "files-2.1").glob("*/*/*/*/*")):
        relative = path.relative_to(origin)
        _, group, module, version, checksum, filename = relative.parts
        permitted = allowed.get((group, module, version, filename), set())
        if not permitted:
            continue
        regular(path, 128 * 1024**2, "dependency_file_shape")
        content_hash = hashlib.sha256()
        path_hash = hashlib.sha1(usedforsecurity=False)
        with path.open("rb") as stream:
            while block := stream.read(1024**2):
                content_hash.update(block)
                path_hash.update(block)
        require(content_hash.hexdigest() in permitted, "public_dependency_checksum")
        # Gradle cache paths can omit leading zeroes; SHA256 still authorizes bytes.
        sha1 = path_hash.hexdigest()
        require(checksum in (sha1, sha1.lstrip("0") or "0"), "public_dependency_path_identity")
        destination = staged / relative
        destination.parent.mkdir(parents=True, exist_ok=True, mode=0o755)
        shutil.copyfile(path, destination)
        destination.chmod(0o644)
        total, count = total + path.stat().st_size, count + 1
        require(total <= 1024**3 and count <= 10000, "dependency_cache_bound")
        present.add((group, module, version))
    # Literal direct coordinates only; transitive closure/AGP graph need the real run.
    builds = ("client_android/build.gradle", "client_android/app/build.gradle") if direct else ()
    for build_index, build in enumerate(builds):
        coordinates = re.findall(
            r"(?m)^\s*(?:classpath|implementation|freeImplementation|premiumImplementation|testImplementation|androidTestImplementation)"
            r"\s+(?:platform\()?['\"]([^'\"]+)['\"]", (source / build).read_text())
        for index, coordinate in enumerate(coordinates):
            parts = tuple(coordinate.split(":"))
            require(any(p[:len(parts)] == parts for p in present), f"direct_dependency_{build_index}_{index}_absent")
    metadata = origin / "metadata-2.107"
    if not seed:
        regular(metadata / "module-metadata.bin", 64 * 1024**2, "gradle_metadata_2_107_absent")
    for path in sorted(metadata.rglob("*")):
        if path.is_dir():
            require(not path.is_symlink(), "metadata_symlink")
            continue
        relative = path.relative_to(origin)
        tail = relative.parts[1:]
        selected = (len(tail) == 1 and tail[0] in {"module-metadata.bin", "module-artifact.bin", "resource-at-url.bin"})
        selected |= (len(tail) == 6 and tail[0] == "descriptors" and tail[-1] == "descriptor.bin"
                     and tuple(tail[1:4]) in present)
        if not selected:
            continue
        regular(path, 64 * 1024**2, "metadata_file_shape")
        destination = staged / relative
        destination.parent.mkdir(parents=True, exist_ok=True, mode=0o755)
        shutil.copyfile(path, destination)
        destination.chmod(0o644)
        total, count = total + path.stat().st_size, count + 1
        require(total <= 1024**3 and count <= 20000, "dependency_cache_bound")
    print(f"phase=public_cache accepted=true files={count}", flush=True)
    return root / "dependencies"


def tool_mounts() -> list[tuple[str, str]]:
    candidates = [os.environ.get("JAVA_HOME_17_X64", "")]
    candidates += [str(p) for p in sorted(Path("/usr/lib/jvm").glob("*17*"))]
    jdk = None
    for candidate in candidates:
        if not candidate or not Path(candidate).exists():
            continue
        path = Path(candidate).resolve(strict=True)
        if (path / "release").is_file() and re.search(r'(?m)^JAVA_VERSION="17[.\"]', (path / "release").read_text()):
            require((path / "bin/java").is_file() and (path / "bin/javac").is_file(), "jdk17_binaries")
            jdk = path
            break
    require(jdk is not None, "preinstalled_jdk17_absent")
    require((jdk / "conf/security/java.security").resolve(strict=True).is_relative_to(jdk), "jdk17_external_config")
    sdk = Path("/usr/local/lib/android/sdk")
    # The current official SDK names stable API37 "android-37.0", not "android-37".
    platform = sdk / "platforms/android-37.0"
    regular(platform / "android.jar", 100 * 1024**2, "preinstalled_sdk37_absent")
    props = (platform / "source.properties").read_text()
    package = ET.parse(platform / "package.xml").getroot().find("localPackage")
    require(re.search(r"(?m)^AndroidVersion.ApiLevel\s*=\s*37[.]0\s*$", props) is not None
            and package is not None and package.attrib["path"] == "platforms;android-37.0"
            and package.find("type-details/api-level").text == "37.0"
            and not (package.find("type-details/codename").text or "").strip(), "sdk37_identity")
    selected = ["platforms/android-37.0", "ndk/27.0.12077973", "cmake/3.22.1"]
    require((sdk / selected[1] / "source.properties").is_file(), "preinstalled_ndk27_absent")
    require((sdk / selected[2] / "bin/cmake").is_file(), "preinstalled_cmake_absent")
    build_tools = sorted(p for p in (sdk / "build-tools").glob("*") if re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", p.name))
    require(bool(build_tools), "preinstalled_build_tools_absent")
    for path in build_tools:
        require((path / "aapt2").is_file() and (path / "source.properties").is_file(), "build_tools_identity")
        selected.append("build-tools/" + path.name)
    if (sdk / "licenses").is_dir():
        selected.append("licenses")
    mounts = [(str(jdk), "/jdk")]
    for relative in selected:
        path = sdk / relative
        require(path == path.resolve(strict=True), "sdk_component_symlink")
        mounts.append((str(path), "/sdk/" + relative))
    for path in ("/usr/bin", "/usr/lib", "/usr/lib64", "/bin", "/lib", "/lib64", "/usr/share/zoneinfo", "/etc/ld.so.cache"):
        if Path(path).exists():
            mounts.append((str(Path(path).resolve(strict=True)), path))
    require(Path("/usr/bin/python3").exists(), "trusted_python_absent")
    awk = Path("/usr/bin/awk").resolve(strict=True)
    require(awk.is_relative_to("/usr") and os.access(awk, os.X_OK), "public_awk_alias")
    regular(awk, 16 * 1024**2, "public_awk_shape")
    mounts.append((str(awk), "/etc/alternatives/awk"))
    print("phase=toolchain accepted=true jdk=17 sdk=37", flush=True)
    return mounts


PUBLIC_ROOTS = (
    ("androidx.test", "runner", "1.7.0"),
    ("androidx.test", "rules", "1.7.0"),
    ("androidx.test.ext", "junit", "1.3.0"),
    ("androidx.test.espresso", "espresso-core", "3.7.0"),
)
PUBLIC_TOOLS = (
    ("com.google.prefab", "cli", "2.1.0", "cli-2.1.0-all.jar"),
    ("com.android.tools.build", "aapt2", "9.4.1-15978811", "aapt2-9.4.1-15978811-linux.jar"),
)
# Versions the pinned app runtime graph selects at SOURCE (fragment 1.9.1 -> tracing 2.0.0 ->
# annotation 1.10.0; built-in Kotlin 2.4.20 stdlib). The four test roots alone select
# annotation-jvm 1.7.0 / kotlin-stdlib 1.9.21, which the unchanged XML does not verify.
PUBLIC_CONSTRAINTS = (
    ("androidx.annotation", "annotation", "1.10.0", ("annotation-1.10.0.module",)),
    ("androidx.annotation", "annotation-jvm", "1.10.0",
     ("annotation-jvm-1.10.0.jar", "annotation-jvm-1.10.0.module")),
    ("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.20",
     ("kotlin-stdlib-2.4.20.jar", "kotlin-stdlib-2.4.20.module")),
)
PUBLIC_BUILD = """import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.Category
repositories { google(); mavenCentral() }
configurations {
    androidTestPublic {
        canBeConsumed = false
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, 'java-runtime'))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, 'library'))
        }
    }
    packagingPublic { canBeConsumed = false; transitive = false }
}
dependencies {
    androidTestPublic 'androidx.test:runner:1.7.0'
    androidTestPublic 'androidx.test:rules:1.7.0'
    androidTestPublic 'androidx.test.ext:junit:1.3.0'
    androidTestPublic 'androidx.test.espresso:espresso-core:3.7.0'
    packagingPublic 'com.google.prefab:cli:2.1.0:all'
    packagingPublic 'com.android.tools.build:aapt2:9.4.1-15978811:linux'
    constraints {
        androidTestPublic 'androidx.annotation:annotation:1.10.0'
        androidTestPublic 'androidx.annotation:annotation-jvm:1.10.0'
        androidTestPublic 'org.jetbrains.kotlin:kotlin-stdlib:2.4.20'
    }
}
tasks.register('resolvePublic') {
    doLast {
        configurations.androidTestPublic.resolve()
        configurations.packagingPublic.resolve()
    }
}
"""


def instrumentation(data: bytes) -> None:
    require(0 < len(data) <= MAX_OUTPUT and b"\0" not in data, "instrumentation_bound")
    lines = data.decode("utf8", errors="strict").replace("\r\n", "\n").splitlines()
    record: dict[str, str] = {}
    started: set[str] = set()
    finished: set[str] = set()
    for line in lines:
        if line.startswith("INSTRUMENTATION_STATUS: "):
            item = line.removeprefix("INSTRUMENTATION_STATUS: ")
            if "=" in item:
                key, value = item.split("=", 1)
                require(key not in record, "duplicate_status_key")
                record[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            code = line.removeprefix("INSTRUMENTATION_STATUS_CODE: ")
            require(record.get("class") == TEST_CLASS and record.get("test") in EXPECTED_METHODS
                    and record.get("numtests") == "8" and code in {"1", "0"}
                    and set(record) <= {"class", "test", "numtests", "current", "id", "stream"},
                    "instrumentation_case")
            name = record["test"]
            if code == "1":
                require(name not in started and len(started) == len(finished), "duplicate_start")
                started.add(name)
            else:
                require(name in started and name not in finished, "unstarted_or_duplicate_success")
                finished.add(name)
            record = {}
        elif line.startswith("INSTRUMENTATION_CODE: "):
            require(line == "INSTRUMENTATION_CODE: -1" and finished == EXPECTED_METHODS,
                    "instrumentation_terminal")
        else:
            require(not line.startswith(("INSTRUMENTATION_FAILED", "INSTRUMENTATION_ABORTED"))
                    and not line.startswith("INSTRUMENTATION_RESULT: shortMsg=")
                    and "FAILURES!!!" not in line, "instrumentation_failure")
    require(not record and started == finished == EXPECTED_METHODS
            and lines.count("OK (8 tests)") == 1
            and sum(line.startswith("INSTRUMENTATION_CODE:") for line in lines) == 1,
            "instrumentation_complete")


def native_encoding(checksum: str, size: int) -> str:
    require(isinstance(checksum, str) and type(size) is int, "native_identity_shape")
    for encoding, expected in NATIVE_ENCODINGS.items():
        if (checksum, size) == expected:
            return encoding
    raise Refusal("apk_native_identity")


def validate_strip_proof(proof: dict | None) -> None:
    if not isinstance(proof, dict):
        raise Refusal("strip_proof_required")
    require(set(proof) == {
        "ndk_revision", "tool", "flags", "argv", "input", "output", "limits", "exit_code"},
        "strip_proof_required")
    require(proof["ndk_revision"] == NDK_REVISION and proof["flags"] == ["--strip-unneeded"]
            and proof["argv"] == STRIP_ARGV and proof["limits"] == STRIP_LIMITS
            and type(proof["exit_code"]) is int and proof["exit_code"] == 0, "strip_proof_profile")
    tool = proof["tool"]
    require(isinstance(tool, dict) and set(tool) == {"path", "resolved_path", "sha256", "bytes"},
            "strip_proof_tool")
    require(tool["path"] == STRIP_TOOL and isinstance(tool["resolved_path"], str)
            and str(Path(tool["resolved_path"])) == tool["resolved_path"]
            and ".." not in Path(tool["resolved_path"]).parts
            and Path(tool["resolved_path"]).parent == Path(STRIP_TOOL).parent
            and isinstance(tool["sha256"], str)
            and re.fullmatch(r"[0-9a-f]{64}", tool["sha256"]) is not None
            and type(tool["bytes"]) is int and 0 < tool["bytes"] <= 256 * 1024**2,
            "strip_proof_tool")
    for field, expected_encoding in (("input", "raw"), ("output", "ndk27-strip-unneeded")):
        identity = proof[field]
        require(isinstance(identity, dict) and set(identity) == {"sha256", "bytes"}
                and native_encoding(identity["sha256"], identity["bytes"]) == expected_encoding,
                "strip_proof_identity")


def trusted_strip_proof(root: Path, source: Path, mounts: list[tuple[str, str]]) -> dict:
    require(os.getuid() != 0, "strip_host_nonroot")
    candidates = [Path(path) for path, target in mounts if target == "/sdk/ndk/" + NDK_REVISION]
    require(len(candidates) == 1, "strip_ndk_mount")
    ndk = candidates[0]
    properties = ndk / "source.properties"
    regular(properties, 64 * 1024, "strip_ndk_properties")
    properties_bytes = properties.read_bytes()
    require(re.search(rb"(?m)^Pkg\.Revision\s*=\s*" + re.escape(NDK_REVISION.encode()) + rb"\s*$",
                      properties_bytes) is not None, "strip_ndk_revision")
    tool = ndk / STRIP_TOOL
    binary = tool.resolve(strict=True)
    require(binary.is_relative_to(ndk) and binary.parent == (ndk / STRIP_TOOL).parent
            and os.access(binary, os.X_OK), "strip_tool_path")
    regular(binary, 256 * 1024**2, "strip_tool_shape")
    with binary.open("rb") as stream:
        require(stream.read(4) == b"\x7fELF", "strip_linux_tool")
        stream.seek(0)
        tool_hash = hashlib.file_digest(stream, "sha256").hexdigest()
    tool_size = binary.stat().st_size
    raw_source = source / "client_android/app/src/main/jniLibs/x86_64/libsecurecall_core_crypto.so"
    regular(raw_source, NATIVE_ENCODINGS["raw"][1], "strip_raw_bound")
    raw_bytes = raw_source.read_bytes()
    require(native_encoding(digest(raw_bytes), len(raw_bytes)) == "raw", "strip_raw_pin")
    with tempfile.TemporaryDirectory(prefix="strip-proof-", dir=root) as directory:
        work = Path(directory)
        (work / "raw.so").write_bytes(raw_bytes)
        def limits() -> None:
            resource.setrlimit(resource.RLIMIT_CPU, (30, 30))
            resource.setrlimit(resource.RLIMIT_FSIZE, (512 * 1024, 512 * 1024))
            resource.setrlimit(resource.RLIMIT_NOFILE, (64, 64))
            resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
        # Keep argv[0] as llvm-strip even when NDK aliases it to llvm-objcopy.
        with (work / "diagnostic").open("xb") as diagnostic:
            subprocess.run([str(tool), *STRIP_ARGV], executable=str(binary), cwd=work,
                           env={**ENV, "TMPDIR": str(work)}, stdout=diagnostic,
                           stderr=subprocess.STDOUT, check=True, timeout=30, preexec_fn=limits)
        regular(work / "stripped.so", NATIVE_ENCODINGS["ndk27-strip-unneeded"][1], "strip_output_bound")
        result = (work / "stripped.so").read_bytes()
        require(native_encoding(digest(result), len(result)) == "ndk27-strip-unneeded",
                "strip_output_pin")
        require((work / "raw.so").read_bytes() == raw_bytes and raw_source.read_bytes() == raw_bytes
                and properties.read_bytes() == properties_bytes, "strip_input_unchanged")
        with binary.open("rb") as stream:
            require(hashlib.file_digest(stream, "sha256").hexdigest() == tool_hash
                    and binary.stat().st_size == tool_size and tool.resolve(strict=True) == binary,
                    "strip_tool_unchanged")
        proof = {"ndk_revision": NDK_REVISION,
                 "tool": {"path": STRIP_TOOL, "resolved_path": str(binary.relative_to(ndk)),
                          "sha256": tool_hash, "bytes": tool_size},
                 "flags": ["--strip-unneeded"], "argv": list(STRIP_ARGV),
                 "input": {"sha256": digest(raw_bytes), "bytes": len(raw_bytes)},
                 "output": {"sha256": digest(result), "bytes": len(result)},
                 "limits": dict(STRIP_LIMITS), "exit_code": 0}
        validate_strip_proof(proof)
        return proof


def apk_info(path: Path, *, application: bool) -> dict[str, object]:
    regular(path, MAX_BUNDLE, "apk_bound")
    checksum: str | None = None
    size: int | None = None
    encoding: str | None = None
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)) <= 20000, "apk_duplicate_entries")
        native = "lib/x86_64/libsecurecall_core_crypto.so"
        require(not application or native in names, "apk_native_absent")
        if application or native in names:
            entry = archive.getinfo(native)
            require(0 < entry.file_size <= 16 * 1024**2, "apk_native_bound")
            checksum, size = digest(archive.read(entry)), entry.file_size
            encoding = native_encoding(checksum, size)
    return {"sha256": digest(path.read_bytes()), "bytes": path.stat().st_size,
            "x86_64_jni_sha256": checksum, "x86_64_jni_bytes": size,
            "x86_64_jni_encoding": encoding}


def container_profile() -> None:
    base = Path("/sys/fs/cgroup")
    require((base / "memory.max").read_text().strip() == "4294967296"
            and (base / "memory.swap.max").read_text().strip() == "0"
            and (base / "pids.max").read_text().strip() == "256"
            and (base / "cpu.max").read_text().split() == ["100000", "100000"],
            "kernel_cgroup")
    require(os.getuid() == os.getgid() == 65534
            and resource.getrlimit(resource.RLIMIT_CPU) == (840, 840)
            and resource.getrlimit(resource.RLIMIT_FSIZE) == (536870912, 536870912)
            and resource.getrlimit(resource.RLIMIT_NOFILE) == (1024, 1024), "kernel_rlimits")
    status = Path("/proc/self/status").read_text()
    require(re.search(r"(?m)^NoNewPrivs:\s+1$", status) is not None
            and re.search(r"(?m)^CapEff:\s+0000000000000000$", status) is not None,
            "kernel_privileges")
    expected = set(json.loads(os.environ["SC_MOUNT_DESTINATIONS"]))
    seen = set()
    for line in Path("/proc/self/mountinfo").read_text().splitlines():
        fields = line.split()
        target, flags = fields[4], fields[5].split(",")
        require("\\" not in target, "kernel_mount_escape")
        if target in expected:
            require("ro" in flags and target not in seen, "kernel_bind_readonly")
            seen.add(target)
        elif target == "/":
            require("ro" in flags, "kernel_root_readonly")
        elif "rw" in flags:
            require(target == "/scratch" or target == "/dev"
                    or target in {"/dev/pts", "/dev/shm", "/dev/mqueue"}
                    or target == "/proc" or target.startswith("/proc/"),
                    "kernel_writable_mount")
    require(seen == expected, "kernel_mount_allowlist")
    if os.environ["SC_NETWORK"] == "none":
        require(set(os.listdir("/sys/class/net")) == {"lo"}, "kernel_network")
    print("SC_CAPS_OK", flush=True)


def container_work() -> None:
    container_profile()
    public = os.environ["SC_NETWORK"] == "bridge"
    scratch = Path("/scratch")
    for directory in ("home", "tmp", "android", "project"):
        (scratch / directory).mkdir(mode=0o700)
    shutil.copytree("/source/client_android", scratch / "project/client_android")
    if not public:
        shutil.copytree("/source/tools", scratch / "project/tools")
    wrapper = scratch / "gradle" / WRAPPER_PATH
    wrapper.parent.mkdir(parents=True)
    shutil.copyfile("/wrapper.zip", wrapper)
    if public:
        shutil.copytree("/dependencies/modules-2", scratch / "gradle/caches/modules-2")
    project = scratch / "project/client_android"
    if public:
        distribution = scratch / "distribution"
        with zipfile.ZipFile("/wrapper.zip") as archive:
            require(all(not Path(n).is_absolute() and ".." not in Path(n).parts
                        for n in archive.namelist()), "distribution_paths")
            archive.extractall(distribution)
        executable = distribution / "gradle-9.8.0/bin/gradle"
    else:
        executable = project / "gradlew"
    executable.chmod(0o700)
    command = [str(executable), "--no-daemon", "--max-workers=1", "--console=plain",
               "--dependency-verification=strict"]
    command += (["resolvePublic"] if public else
                ["--offline", ":app:assembleFreeDebug", ":app:assembleFreeDebugAndroidTest"])
    subprocess.run(command, cwd=project, check=True, timeout=850)
    events = dict(line.split() for line in (Path("/sys/fs/cgroup") / "memory.events").read_text().splitlines())
    require(all(int(events.get(k, "0")) == 0 for k in ("oom", "oom_kill", "oom_group_kill")), "cgroup_oom")
    exports = scratch / "exports"
    exports.mkdir(mode=0o700)
    if public:
        staged = stage_cache(scratch / "selected", Path("/source"),
                             cache=scratch / "gradle", direct=False)
        # Copy only the checksum-selected modules + allowlisted Gradle metadata.
        (exports / "caches").mkdir(mode=0o700)
        (staged / "modules-2").rename(exports / "caches/modules-2")
        verify_public_roots(exports)
    else:
        candidates = (
            project / "app/build/outputs/apk/free/debug/app-free-x86_64-debug.apk",
            project / "app/build/outputs/apk/androidTest/free/debug/app-free-debug-androidTest.apk",
        )
        info = {}
        for index, source in enumerate(candidates):
            info[str(index)] = apk_info(source, application=index == 0)
            shutil.copyfile(source, exports / f"{index}.apk")
        (exports / "apk-receipt.json").write_bytes(canonical(info))
    print("SC_EXPORT_READY", flush=True)
    # The host copies the bounded immutable selection before releasing this hold.
    deadline = time.monotonic() + 60
    while not (scratch / "export-done").exists():
        require(time.monotonic() < deadline, "export_hold_deadline")
        time.sleep(0.1)


def verify_public_roots(cache: Path) -> None:
    base = cache / "caches/modules-2/files-2.1"
    for group, name, version in PUBLIC_ROOTS:
        require(any((base / group / name / version).glob(f"*/{name}-{version}.aar")),
                "public_root_absent")
    for group, name, version, filename in PUBLIC_TOOLS:
        require(any((base / group / name / version).glob("*/" + filename)), "public_tool_absent")


def receive_export(name: str, destination: Path, *, public: bool, timeout: int) -> None:
    require(not destination.exists(), "fresh_export")
    limit = 1024**3 + 32 * 1024**2 if public else 2 * MAX_BUNDLE + 1024**2
    capture = destination.with_suffix(".tar")
    def bound() -> None:
        resource.setrlimit(resource.RLIMIT_FSIZE, (limit, limit))
    try:
        with capture.open("xb") as stream:
            subprocess.run(["docker", "cp", name + ":/scratch/exports/.", "-"],
                           env=ENV, stdout=stream, stderr=subprocess.DEVNULL,
                           check=True, timeout=min(timeout, 60), preexec_fn=bound)
        destination.mkdir(mode=0o700)
        total, count, entries = 0, 0, 0
        with tarfile.open(capture, mode="r:") as archive:
            for entry in archive:
                entries += 1
                require(entries <= (100000 if public else 5), "export_entry_bound")
                path = Path(entry.name)
                require(not path.is_absolute() and ".." not in path.parts
                        and not entry.issym() and not entry.islnk()
                        and (entry.isfile() or entry.isdir()), "export_shape")
                if str(path) == ".":
                    require(entry.isdir(), "export_root")
                    continue
                require(str(path) == entry.name.removeprefix("./").rstrip("/"), "export_path")
                if public:
                    require(path.parts[:2] == ("caches", "modules-2")
                            or (entry.isdir() and path.parts == ("caches",)), "export_cache_path")
                else:
                    require(entry.isfile() and str(path) in {"0.apk", "1.apk", "apk-receipt.json"},
                            "export_apk_path")
                    if str(path) == "apk-receipt.json":
                        require(entry.size <= 4096, "apk_receipt_bound")
                target = destination / path
                if entry.isdir():
                    target.mkdir(parents=True, exist_ok=True, mode=0o700)
                    continue
                count += 1
                total += entry.size
                require(0 < entry.size <= (128 * 1024**2 if public else MAX_BUNDLE)
                        and total <= (1024**3 if public else 2 * MAX_BUNDLE + 4096)
                        and count <= (20000 if public else 3), "export_bound")
                target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
                require(not target.exists(), "export_duplicate")
                stream = archive.extractfile(entry)
                require(stream is not None)
                with target.open("xb") as output:
                    shutil.copyfileobj(stream, output, length=1024**2)
                require(target.stat().st_size == entry.size, "export_size")
        if public:
            verify_public_roots(destination)
        else:
            require({p.name for p in destination.iterdir()} == {"0.apk", "1.apk", "apk-receipt.json"},
                    "exact_apk_exports")
            require(json.loads((destination / "apk-receipt.json").read_bytes()) == {
                str(i): apk_info(destination / f"{i}.apk", application=i == 0) for i in range(2)},
                "export_apk_receipt")
    finally:
        capture.unlink(missing_ok=True)


def nsswitch_config(public: bool) -> bytes:
    return b"passwd: files\ngroup: files\nhosts: " + (b"files dns\n" if public else b"files\n")


def redact_public_log(data: bytes) -> str:
    text = re.sub(r"\x1b\[[0-?]*[ -/]*[@-~]", "", data.decode("utf8", errors="replace"))
    text = "".join(c if c in "\n\t" or 32 <= ord(c) < 127 else "?" for c in text)
    patterns = (
        r"(?im)\b(?:proxy-)?authorization\s*[:=][^\n]*",
        r'''(?i)\b(?:\w*(?:token|password|passwd|secret|credential)\w*|api[_-]?key)["']?\s*[:=]\s*(?:"[^"]*"|'[^']*'|[^\s,;]+)''',
        r"(?i)\bBearer\s+\S+",
        r"\b(?:gh[pousr]_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+|(?:sk|rk)-[A-Za-z0-9_-]+|AKIA[0-9A-Z]{16})\b",
        r"\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b",
        r'''[A-Za-z][A-Za-z0-9+.-]*://[^\s<>"']+''',
        r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,63}",
        r"(?<!\w)(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?::[0-9]{1,5})?",
        r"(?<!\w)\[?(?:[0-9a-fA-F]{0,4}:){2,}[0-9a-fA-F:.]*(?:%[A-Za-z0-9_.-]+)?\]?(?::[0-9]{1,5})?",
        r"(?i)\b(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}\b(?::[0-9]{1,5})?",
        r"(?i)\blocalhost\b(?::[0-9]{1,5})?",
        r"[A-Za-z0-9_+=/-]{20,}",
    )
    for pattern in patterns:
        text = re.sub(pattern, "[redacted]", text)
    return text


def public_diagnostic(data: bytes, *, public: bool, code: int | None, state: dict,
                      bounded: bool, exported: bool, run_id: str, controller_sha: str) -> bytes:
    require(public is True and isinstance(data, bytes) and len(data) <= MAX_OUTPUT,
            "diagnostic_public_only")
    require(re.fullmatch(r"[0-9]{1,20}", run_id) is not None
            and re.fullmatch(r"[0-9a-f]{40}", controller_sha) is not None, "diagnostic_identity")
    container_exit = state.get("ExitCode")
    require(all(value is None or type(value) is int and -255 <= value <= 255
                for value in (code, container_exit)), "diagnostic_exit_shape")
    require(all(value is None or type(value) is bool
                for value in (state.get("OOMKilled"), state.get("Running")))
            and type(bounded) is type(exported) is bool, "diagnostic_state_shape")
    lines = data.decode("utf8", errors="replace").splitlines()
    record = {"format": 1, "kind": "public-preparation-diagnostic", "public_only": True,
              "private_input_fetched": False, "qualification": "NOT_RUN",
              "run_id": run_id, "controller_sha": controller_sha,
              "launcher_exit": code, "container_exit": container_exit,
              "oom": state.get("OOMKilled"), "running": state.get("Running"),
              "caps_markers": lines.count("SC_CAPS_OK"), "export_markers": lines.count("SC_EXPORT_READY"),
              "exported": exported, "output_bytes": len(data), "bounded": bounded,
              "limits": {"wall_seconds": 900, "output_bytes": MAX_OUTPUT},
              "tail": redact_public_log(data)[-MAX_PUBLIC_TAIL:]}
    while True:
        payload = canonical(record)
        if len(payload) <= MAX_PUBLIC_DIAGNOSTIC:
            validate_public_diagnostic(payload)
            return payload
        record["tail"] = record["tail"][max(1, len(record["tail"]) // 4):]


def validate_public_diagnostic(payload: bytes) -> None:
    require(isinstance(payload, bytes) and 0 < len(payload) <= MAX_PUBLIC_DIAGNOSTIC,
            "diagnostic_size")
    try:
        record = json.loads(payload)
    except (ValueError, UnicodeError):
        raise Refusal("diagnostic_json") from None
    require(isinstance(record, dict) and set(record) == {
        "format", "kind", "public_only", "private_input_fetched", "qualification", "run_id",
        "controller_sha", "launcher_exit", "container_exit", "oom", "running", "caps_markers",
        "export_markers", "exported", "output_bytes", "bounded", "limits", "tail"}, "diagnostic_schema")
    require(record["public_only"] is True and record["private_input_fetched"] is False
            and record["qualification"] == "NOT_RUN" and record["kind"] == "public-preparation-diagnostic"
            and type(record["format"]) is int and record["format"] == 1, "diagnostic_public_only")
    require(isinstance(record["tail"], str) and len(record["tail"].encode()) <= MAX_PUBLIC_TAIL
            and redact_public_log(record["tail"].encode()) == record["tail"], "diagnostic_redaction")
    require(all(type(record[key]) is int and 0 <= record[key] <= MAX_OUTPUT
                for key in ("output_bytes", "caps_markers", "export_markers"))
            and all(record[key] is None or type(record[key]) is int and -255 <= record[key] <= 255
                    for key in ("launcher_exit", "container_exit"))
            and all(record[key] is None or type(record[key]) is bool for key in ("oom", "running"))
            and all(type(record[key]) is bool for key in ("exported", "bounded"))
            and record["limits"] == {"wall_seconds": 900, "output_bytes": MAX_OUTPUT}
            and isinstance(record["run_id"], str) and re.fullmatch(r"[0-9]{1,20}", record["run_id"])
            and isinstance(record["controller_sha"], str)
            and re.fullmatch(r"[0-9a-f]{40}", record["controller_sha"])
            and canonical(record) == payload, "diagnostic_state_shape")


def lineage_checks() -> None:
    """Pure synthetic fixtures only; never invoke strip, Docker, Gradle or JNI."""
    import copy
    from collections.abc import Callable
    from unittest.mock import Mock, patch
    import warnings

    checks = 0
    require(NATIVE_ENCODINGS == {
        "raw": ("4a932002caab2e585f56950f5ea35d587d4f13b588c62549f5ad513eaf207c59", 618624),
        "ndk27-strip-unneeded": (
            "abc5e77e3f17c7137ef94700266d7969643dc6f8f4a48f6097ca53befcd8d0ff", 477088)},
        "approved_encoding_constants_fixture")
    require(STRIP_ARGV == ["--strip-unneeded", "-o", "stripped.so", "raw.so"]
            and NDK_REVISION == "27.0.12077973", "strip_invocation_fixture")
    def rejected(operation: Callable[[], object]) -> None:
        nonlocal checks
        try:
            operation()
        except Refusal:
            checks += 1
        else:
            raise Refusal("negative_lineage_fixture_accepted")

    for encoding, (checksum, size) in NATIVE_ENCODINGS.items():
        require(native_encoding(checksum, size) == encoding, "identity_fixture")
        checks += 1
        rejected(lambda: native_encoding(checksum, size + 1))
        rejected(lambda: native_encoding("0" * 64, size))
    raw_hash, raw_size = NATIVE_ENCODINGS["raw"]
    stripped_hash, stripped_size = NATIVE_ENCODINGS["ndk27-strip-unneeded"]
    # Dummy tool identity exercises receipt shape, never claims Linux execution.
    proof = {"ndk_revision": NDK_REVISION,
             "tool": {"path": STRIP_TOOL, "resolved_path": STRIP_TOOL,
                      "sha256": "1" * 64, "bytes": 100},
             "flags": ["--strip-unneeded"], "argv": list(STRIP_ARGV),
             "input": {"sha256": raw_hash, "bytes": raw_size},
             "output": {"sha256": stripped_hash, "bytes": stripped_size},
             "limits": dict(STRIP_LIMITS), "exit_code": 0}
    validate_strip_proof(proof)
    checks += 1
    rejected(lambda: validate_strip_proof(None))
    rejected(lambda: validate_strip_proof({}))
    for keys, value in (
        (("ndk_revision",), "26.0.0"), (("flags",), ["--strip-all"]),
        (("argv",), ["--strip-unneeded", "raw.so"]), (("exit_code",), 1),
        (("tool", "path"), "untrusted/llvm-strip"),
        (("tool", "resolved_path"), str(Path(STRIP_TOOL).parent / "..")),
        (("tool", "sha256"), "not-a-hash"), (("tool", "bytes"), 0),
        (("input", "sha256"), stripped_hash), (("input", "bytes"), raw_size + 1),
        (("output", "sha256"), raw_hash), (("output", "bytes"), stripped_size + 1),
        (("limits", "wall_seconds"), 900),
    ):
        bad = copy.deepcopy(proof)
        target = bad
        for key in keys[:-1]:
            target = target[key]
        target[keys[-1]] = value
        rejected(lambda: validate_strip_proof(bad))
    with patch.dict(globals(), {"host_command": Mock(side_effect=AssertionError("unexpected host call"))}):
        rejected(lambda: run_container(Path("."), Path("."), Path("."), [], public=False))

    with tempfile.TemporaryDirectory(prefix="sc-lineage-fixtures-", dir=Path.cwd()) as directory:
        root = Path(directory)
        payloads = {"raw": b"synthetic raw", "ndk27-strip-unneeded": b"synthetic stripped"}
        with patch.dict(NATIVE_ENCODINGS, {key: (digest(data), len(data))
                                          for key, data in payloads.items()}, clear=True):
            native = "lib/x86_64/libsecurecall_core_crypto.so"
            for encoding, data in payloads.items():
                path = root / (encoding + ".apk")
                with zipfile.ZipFile(path, "w") as archive:
                    archive.writestr(native, data)
                for application in (True, False):
                    info = apk_info(path, application=application)
                    require(info["x86_64_jni_encoding"] == encoding
                            and info["x86_64_jni_sha256"] == digest(data)
                            and info["x86_64_jni_bytes"] == len(data), "zip_encoding_fixture")
                    checks += 1
            empty = root / "empty.apk"
            with zipfile.ZipFile(empty, "w") as archive:
                archive.writestr("AndroidManifest.xml", b"synthetic")
            require(apk_info(empty, application=False)["x86_64_jni_encoding"] is None, "zip_absence_fixture")
            checks += 1
            rejected(lambda: apk_info(empty, application=True))
            wrong = root / "wrong.apk"
            with zipfile.ZipFile(wrong, "w") as archive:
                archive.writestr(native, b"arbitrary mismatch")
            for application in (True, False):
                rejected(lambda: apk_info(wrong, application=application))
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                with zipfile.ZipFile(wrong, "w") as archive:
                    archive.writestr(native, payloads["raw"])
                    archive.writestr(native, payloads["raw"])
            rejected(lambda: apk_info(wrong, application=True))
    require(nsswitch_config(True) == b"passwd: files\ngroup: files\nhosts: files dns\n"
            and nsswitch_config(False) == b"passwd: files\ngroup: files\nhosts: files\n",
            "generated_dns_profile_fixture")
    print(f"SC_SOURCE_CHECKS lineage_zip={checks} generated_profiles=2 target_execution=false")


def run_container(root: Path, source: Path, dependencies: Path,
                  mounts: list[tuple[str, str]], *, public: bool, lineage: dict | None = None,
                  diagnose: bool = False) -> Path:
    require(not diagnose or public is True, "diagnostic_public_only")
    if not public:
        validate_strip_proof(lineage)
    network = "bridge" if public else "none"
    require(host_command(["docker", "info", "--format", "{{.CgroupVersion}}"]).strip() == b"2",
            "cgroup_v2")
    driver = host_command(["docker", "info", "--format", "{{.CgroupDriver}}"]).decode().strip()
    require(driver in {"systemd", "cgroupfs"}, "cgroup_driver")
    cgroup_parent = "system.slice" if driver == "systemd" else "/docker"
    for filename, content in {"hosts": b"127.0.0.1 localhost\n::1 localhost\n",
                              "hostname": b"qualification\n",
                              "resolv.conf": b"nameserver 1.1.1.1\n" if public else b""}.items():
        path = root / filename
        path.write_bytes(content)
        path.chmod(0o644)
        mounts.append((str(path), "/etc/" + filename))

    name, image = owned_name(), owned_name() + ":empty"
    empty = io.BytesIO()
    with tarfile.open(fileobj=empty, mode="w", format=tarfile.USTAR_FORMAT) as archive:
        for path in ("usr", "usr/bin", "usr/lib", "usr/lib64", "bin", "lib", "lib64", "etc", "etc/alternatives", "source", "jdk", "sdk", "dependencies", "scratch"):
            member = tarfile.TarInfo(path)
            member.type, member.mode = tarfile.DIRTYPE, 0o755
            archive.addfile(member)
        member = tarfile.TarInfo("tmp")
        member.type, member.linkname, member.mode = tarfile.SYMTYPE, "/scratch/tmp", 0o777
        archive.addfile(member)
        for path, data in {"etc/passwd": b"nobody:x:65534:65534:Qualification:/scratch/home:/bin/sh\n",
                           "etc/group": b"nogroup:x:65534:\n",
                           "etc/nsswitch.conf": nsswitch_config(public)}.items():
            member = tarfile.TarInfo(path)
            member.size, member.mode = len(data), 0o644
            archive.addfile(member, io.BytesIO(data))
    data, code, state = bytearray(), None, {}
    bounded, exported = True, False
    try:
        image_id = host_command(["docker", "import", "--change", f"LABEL {LABEL}={name}", "-", image], stdin=empty.getvalue()).decode().strip()
        mounts += [(str(source), "/source"), (str(dependencies), "/dependencies"), (str(root / "wrapper.zip"), "/wrapper.zip"),
                   (str(Path(__file__).resolve(strict=True)), "/controller.py")]
        environment = {"PATH": "/jdk/bin:/usr/bin:/bin", "JAVA_HOME": "/jdk", "ANDROID_HOME": "/sdk", "ANDROID_SDK_ROOT": "/sdk",
                       "ANDROID_USER_HOME": "/scratch/android", "HOME": "/scratch/home", "GRADLE_USER_HOME": "/scratch/gradle",
                       "GRADLE_RO_DEP_CACHE": "/dependencies", "TMPDIR": "/scratch/tmp", "LC_ALL": "C.UTF-8",
                       "GRADLE_OPTS": "-Dorg.gradle.jvmargs=-Xmx1024m -Dorg.gradle.java.installations.auto-download=false -Dorg.gradle.project.android.builder.sdkDownload=false",
                       "JAVA_TOOL_OPTIONS": "-XX:ActiveProcessorCount=1 -XX:ParallelGCThreads=1 -XX:ConcGCThreads=1 -XX:CICompilerCount=2 -XX:MaxMetaspaceSize=384m -XX:ReservedCodeCacheSize=128m -Xss512k -Djava.io.tmpdir=/scratch/tmp -Duser.home=/scratch/home"}
        if public:
            require(sum(target == "/public-cacerts" for _, target in mounts) == 1,
                    "public_cacerts_mount")
            environment["JAVA_TOOL_OPTIONS"] += " -Djavax.net.ssl.trustStore=/public-cacerts"
        environment["SC_MOUNT_DESTINATIONS"] = json.dumps(sorted(
            destination for _, destination in mounts), separators=(",", ":"))
        environment["SC_NETWORK"] = network
        script = (
            "exec /usr/bin/python3 -I /controller.py container-work"
        )
        command = ["docker", "create", "--name", name, "--label", f"{LABEL}={name}", "--network", network, "--read-only",
                   "--user", "65534:65534", "--workdir", "/scratch", "--cap-drop", "ALL", "--security-opt", "no-new-privileges=true",
                   "--memory", "4g", "--memory-swap", "4g", "--cpus", "1", "--pids-limit", "256", "--cgroupns", "private",
                   "--cgroup-parent", cgroup_parent,
                   "--ipc", "none", "--log-driver", "none", "--ulimit", "cpu=840:840", "--ulimit", "fsize=536870912:536870912",
                   "--ulimit", "nofile=1024:1024", "--tmpfs", "/scratch:rw,exec,nosuid,nodev,size=3221225472,uid=65534,gid=65534,mode=0700"]
        for mount_source, destination in mounts:
            command += ["--mount", f"type=bind,src={mount_source},dst={destination},readonly"]
        for key, value in environment.items():
            command += ["--env", f"{key}={value}"]
        target = ["/usr/bin/env", "-i", *[f"{k}={v}" for k, v in environment.items()], "/bin/sh", "-eu", "-c", script]
        host_command(command + [image, *target])
        info = json.loads(host_command(["docker", "inspect", name]))[0]
        config, limits = info["Config"], info["HostConfig"]
        require(info["Image"] == image_id and config["User"] == "65534:65534"
                and config["Entrypoint"] in (None, []) and config["Labels"].get(LABEL) == name
                and info["State"]["Status"] == "created"
                and config["WorkingDir"] == "/scratch" and config["Cmd"] == target
                and len(config["Env"]) == len(environment) and set(config["Env"]) == {f"{k}={v}" for k, v in environment.items()}
                and limits["ReadonlyRootfs"] is True and limits["NetworkMode"] == network
                and limits["Memory"] == limits["MemorySwap"] == 4 * 1024**3
                and limits["NanoCpus"] == 10**9 and limits["PidsLimit"] == 256 and limits["CgroupnsMode"] == "private"
                and limits["CgroupParent"] == cgroup_parent and limits["IpcMode"] == "none" and limits["Privileged"] is False
                and limits["PidMode"] == "" and limits["CapDrop"] == ["ALL"] and not limits["CapAdd"]
                and limits["SecurityOpt"] == ["no-new-privileges=true"] and limits["LogConfig"]["Type"] == "none"
                and {u["Name"]: (u["Soft"], u["Hard"]) for u in limits["Ulimits"]}
                == {"cpu": (840, 840), "fsize": (536870912, 536870912), "nofile": (1024, 1024)}
                and limits["Tmpfs"] == {"/scratch": "rw,exec,nosuid,nodev,size=3221225472,uid=65534,gid=65534,mode=0700"}
                and {(m["Source"], m["Destination"]) for m in info["Mounts"] if m["Type"] == "bind"} == set(mounts)
                and all(m["RW"] is False if m["Type"] == "bind" else m["Type"] == "tmpfs" and m["Destination"] == "/scratch" for m in info["Mounts"])
                and not limits.get("Devices") and not limits.get("DeviceRequests"), "inspect_profile")
        require(re.fullmatch(r"[0-9a-f]{64}", info["Id"]) is not None)
        with (Path(os.environ["RUNNER_TEMP"]) / (name + ".cgroup-id")).open("x") as stream:
            stream.write(info["Id"] + "\n")
        print(f"phase=admitted public={str(public).lower()} offline={str(not public).lower()} memory_mib=4096 pids=256 cpu=1", flush=True)
        started, data, bounded, exported = time.monotonic(), bytearray(), True, False
        process = subprocess.Popen(["docker", "start", "--attach", name], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        require(process.stdout is not None)
        selector = selectors.DefaultSelector()
        selector.register(process.stdout, selectors.EVENT_READ)
        try:
            while selector.get_map():
                if time.monotonic() - started > 900:
                    bounded = False
                    break
                for key, _ in selector.select(timeout=1):
                    block = os.read(key.fd, 4096)
                    if not block:
                        selector.unregister(key.fileobj)
                        continue
                    if len(data) + len(block) > MAX_OUTPUT:
                        bounded = False
                        break
                    data.extend(block)
                    if not exported and b"\nSC_EXPORT_READY\n" in b"\n" + data:
                        receive_export(name, root / "exported", public=public,
                                       timeout=max(1, int(900 - (time.monotonic() - started))))
                        exported = True
                        host_command(["docker", "exec", "--user", "65534:65534", name,
                                      "/usr/bin/touch", "/scratch/export-done"],
                                     timeout=max(1, int(900 - (time.monotonic() - started))))
                if not bounded:
                    break
            if not bounded:
                host_command(["docker", "kill", "--signal", "KILL", name])
            code = process.wait(timeout=10)
            state = json.loads(host_command(["docker", "inspect", name]))[0]["State"]
        finally:
            selector.close()
            try:
                cleanup()
            finally:
                if process.poll() is None:
                    process.kill()
                    process.wait(timeout=10)
        lines = data.decode("utf8", errors="replace").splitlines()
        require(bounded and code == 0 and state["ExitCode"] == 0
                and not state["Running"] and not state["OOMKilled"]
                and lines.count("SC_CAPS_OK") == 1
                and lines.count("SC_EXPORT_READY") == 1 and exported,
                "container_result")
        return root / "exported"
    except Exception:
        if diagnose:
            raise PublicPreparationFailure(public_diagnostic(
                bytes(data), public=public, code=code, state=state, bounded=bounded, exported=exported,
                run_id=os.environ["GITHUB_RUN_ID"], controller_sha=os.environ["SC_CONTROLLER_SHA"])) from None
        raise
    finally:
        cleanup()


def dispatch_guard() -> None:
    require(ADMISSION == "PARENT_REVIEWED" and REF.startswith("refs/heads/temp/")
            and RELEASE > 0 and TAG.startswith("sc-jni-art-")
            and ASSET == "sc-jni-art-input.tar"
            and EMULATOR_REVISION != "NOT_READY" and IMAGE_REVISION != "NOT_READY",
            "bindings_NOT_READY")
    require(os.getuid() != 0 and Path(f"/run/user/{os.getuid()}/bus").is_socket(),
            "headless_user_cgroup_admission")
    require(Path("/dev/kvm").exists() and stat.S_ISCHR(Path("/dev/kvm").stat().st_mode)
            and os.access("/dev/kvm", os.R_OK | os.W_OK), "existing_user_kvm_access")
    require(os.environ.get("GITHUB_REPOSITORY") == REPO
            and os.environ.get("GITHUB_EVENT_NAME") == "workflow_dispatch"
            and os.environ.get("GITHUB_RUN_ATTEMPT") == "1"
            and os.environ.get("SC_RUNNER_ENVIRONMENT") == "github-hosted"
            and os.environ.get("GITHUB_REF") == REF
            and os.environ.get("SC_CONTROLLER_SHA") == os.environ.get("GITHUB_SHA")
            and re.fullmatch(r"[0-9a-f]{40}", os.environ.get("SC_CONTROLLER_SHA", "")) is not None,
            "dispatch")
    require(host_command(["git", "rev-parse", "HEAD"]).decode().strip() == os.environ["SC_CONTROLLER_SHA"],
            "controller_commit")
    require(shutil.disk_usage(os.environ["RUNNER_TEMP"]).free >= 6 * 1024**3, "host_disk_spare")
    available = re.search(r"MemAvailable:\s+(\d+)", Path("/proc/meminfo").read_text())
    require(available is not None and int(available[1]) >= 5 * 1024**2, "host_memory_spare")
    require(not host_command(["docker", "ps", "-q"]).strip(), "foreign_container")
    processes = host_command(["ps", "-e", "-o", "comm="]).decode().splitlines()
    require(not {"cargo", "rustc", "gradle", "java", "emulator", "qemu-system-x86"}.intersection(
        p.strip() for p in processes), "foreign_workload")


def public_cacerts_source(jdk: Path) -> Path:
    require(jdk.is_absolute() and jdk == jdk.resolve(strict=True), "public_cacerts_jdk")
    try:
        source = (jdk / "lib/security/cacerts").resolve(strict=True)
    except (OSError, RuntimeError):
        raise Refusal("public_cacerts_missing") from None
    require(source.is_relative_to(jdk) or source == SYSTEM_JAVA_CACERTS,
            "public_cacerts_provenance")
    regular(source, MAX_PUBLIC_CACERTS, "public_cacerts_shape")
    require(os.access(source, os.R_OK), "public_cacerts_readable")
    return source


def public_cacerts_bytes(path: Path) -> bytes:
    regular(path, MAX_PUBLIC_CACERTS, "public_cacerts_shape")
    with path.open("rb") as stream:
        data = stream.read(MAX_PUBLIC_CACERTS + 1)
    require(0 < len(data) <= MAX_PUBLIC_CACERTS and len(data) == path.stat().st_size,
            "public_cacerts_size")
    return data


def verify_public_cacerts(jdk: Path, staged: Path, checksum: str) -> None:
    require(digest(public_cacerts_bytes(public_cacerts_source(jdk))) == checksum
            and digest(public_cacerts_bytes(staged)) == checksum, "public_cacerts_changed")


def stage_public_cacerts(root: Path, jdk: Path) -> tuple[Path, str]:
    data = public_cacerts_bytes(public_cacerts_source(jdk))
    checksum = digest(data)
    staged = root / "public-cacerts"
    with staged.open("xb") as output:
        output.write(data)
    staged.chmod(0o444)
    verify_public_cacerts(jdk, staged, checksum)
    return staged, checksum


def verify_public_constraints(xml: ET.Element, ns: dict[str, str]) -> None:
    for group, name, version, artifacts in PUBLIC_CONSTRAINTS:
        require(f"androidTestPublic '{group}:{name}:{version}'" in PUBLIC_BUILD,
                "public_constraint_declared")
        found = xml.findall(f"v:components/v:component[@group='{group}'][@name='{name}']"
                            f"[@version='{version}']", ns)
        require(len(found) == 1, "public_constraint_unverified")
        names = {item.get("name") for item in found[0].findall("v:artifact", ns)}
        require(set(artifacts) <= names, "public_constraint_unverified")


def public_preparation(root: Path, mounts: list[tuple[str, str]], *, diagnose: bool = False) -> Path:
    source = root / "source"
    project = source / "client_android"
    (project / "gradle").mkdir(parents=True, mode=0o755)
    metadata = host_command([
        "curl", "--disable", "--fail", "--proto", "=https", "--max-time", "60",
        "--max-filesize", "524288", "--silent",
        f"https://raw.githubusercontent.com/{REPO}/{SOURCE}/client_android/gradle/verification-metadata.xml"],
        timeout=70)
    require(digest(metadata) == VERIFICATION_SHA256, "public_verification_pin")
    xml = ET.fromstring(metadata)
    ns = {"v": "https://schema.gradle.org/dependency-verification"}
    require(xml.findtext("v:configuration/v:verify-metadata", namespaces=ns) == "true",
            "strict_public_metadata")
    verify_public_constraints(xml, ns)
    for path, data in {
        "gradle/verification-metadata.xml": metadata,
        "settings.gradle": b"rootProject.name = 'sc-public-pinned-preparation'\n",
        "build.gradle": PUBLIC_BUILD.encode(),
    }.items():
        (project / path).write_bytes(data)
    seeded = stage_cache(root, source, seed=True, direct=False)
    jdks = [Path(path) for path, target in mounts if target == "/jdk"]
    require(len(jdks) == 1, "public_cacerts_jdk")
    staged_ca, ca_sha256 = stage_public_cacerts(root, jdks[0])
    public_mounts = [*mounts, (str(staged_ca), "/public-cacerts")]
    try:
        exported = run_container(root, source, seeded, public_mounts, public=True, diagnose=diagnose)
    finally:
        verify_public_cacerts(jdks[0], staged_ca, ca_sha256)
    # Recheck the exported bytes on the host before any private download.
    selected = root / "selected"
    dependencies = stage_cache(selected, source, cache=exported, direct=False)
    verified_home = root / "verified-home"
    (verified_home / "caches").mkdir(parents=True, mode=0o755)
    (dependencies / "modules-2").rename(verified_home / "caches/modules-2")
    verify_public_roots(verified_home)
    return verified_home


def run_headless(root: Path, apks: Path, mounts: list[tuple[str, str]]) -> dict[str, object]:
    require(os.getuid() != 0, "headless_nonroot")
    for port in (5039, 5554, 5555):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
            probe.bind(("127.0.0.1", port))  # Refuse a foreign ADB/emulator endpoint.
    sdk = Path("/usr/local/lib/android/sdk")
    for component, revision in (("emulator", EMULATOR_REVISION),
                                ("system-images/android-24/google_apis/x86_64", IMAGE_REVISION)):
        properties = (sdk / component / "source.properties").read_text()
        require(re.fullmatch(r"[0-9]+(?:\.[0-9]+){0,3}", revision) is not None
                and re.search(r"(?m)^Pkg\.Revision\s*=\s*" + re.escape(revision) + r"\s*$", properties)
                is not None, "headless_public_revision")
    jdk = next(source for source, destination in mounts if destination == "/jdk")
    headless = root / "headless"
    headless.mkdir(mode=0o700)
    name = owned_name()
    unit = name + ".scope"
    previous = user_manager(["show", unit, "--property=LoadState", "--value"], check=False)
    require(previous.stdout.strip() == b"not-found", "fresh_user_scope")
    record = Path(os.environ["RUNNER_TEMP"]) / (name + ".scope-owned")
    with record.open("x") as output:
        output.write(name + "\n")
    record.chmod(0o600)
    group_record = Path(os.environ["RUNNER_TEMP"]) / (name + ".scope-cgroup")
    require(not group_record.exists(), "fresh_scope_group_record")
    clean = {"PATH": f"{jdk}/bin:{sdk}/platform-tools:{sdk}/emulator:{sdk}/cmdline-tools/latest/bin:/usr/bin:/bin",
             "JAVA_HOME": jdk, "ANDROID_HOME": str(sdk), "ANDROID_SDK_ROOT": str(sdk),
             "HOME": str(headless / "home"), "ANDROID_AVD_HOME": str(headless / "avd"),
             "ANDROID_USER_HOME": str(headless / "android"), "TMPDIR": str(headless / "tmp"),
             "ANDROID_ADB_SERVER_PORT": "5039", "LC_ALL": "C.UTF-8",
             "SC_ART_ROOT": str(headless), "SC_ART_APKS": str(apks),
             "SC_ART_CONTROLLER": str(Path(__file__).resolve()),
             "SC_ART_SOURCE": SOURCE, "SC_ART_TREE": TREE,
             "SC_ART_RUN": os.environ["GITHUB_RUN_ID"]}
    command = ["systemd-run", "--user", "--scope", "--quiet", "--collect", "--unit=" + unit,
               "--description=SC JNI ART " + name,
               "--property=MemoryMax=4294967296", "--property=MemorySwapMax=0",
               "--property=CPUQuota=100%", "--property=TasksMax=256", "--",
               "setpriv", "--no-new-privs", "prlimit", "--cpu=840:840",
               "--fsize=536870912:536870912", "--nofile=1024:1024", "--",
               "/usr/bin/env", "-i", *[f"{key}={value}" for key, value in clean.items()],
               "/bin/bash", str(Path(__file__).with_name("sc-art-headless.sh").resolve())]
    process = None
    selector = selectors.DefaultSelector()
    data = bytearray()
    try:
        process = subprocess.Popen(command, env={**ENV, "XDG_RUNTIME_DIR": f"/run/user/{os.getuid()}"},
                                   stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        require(process.stdout is not None)
        selector.register(process.stdout, selectors.EVENT_READ)
        deadline = time.monotonic() + 900
        while selector.get_map():
            require(time.monotonic() < deadline, "headless_wall")
            for key, _ in selector.select(timeout=1):
                block = os.read(key.fd, 4096)
                if not block:
                    selector.unregister(key.fileobj)
                else:
                    require(len(data) + len(block) <= MAX_OUTPUT, "headless_output")
                    data.extend(block)
                    if not group_record.exists() and b"\n" in data:
                        first = bytes(data).split(b"\n", 1)[0].decode("ascii")
                        require(first.startswith("SC_ART_SCOPE cgroup="), "headless_scope_receipt")
                        group = first.removeprefix("SC_ART_SCOPE cgroup=")
                        require(group.startswith("/user.slice/") and ".." not in group
                                and group.endswith("/" + unit), "headless_scope_owner")
                        with group_record.open("x") as output:
                            output.write(group + "\n")
                        group_record.chmod(0o600)
        require(process.wait(timeout=5) == 0, "headless_exit")
        lines = data.decode("ascii").splitlines()
        prefix = f"CONTACT_VISUAL_ISOLATION:head={SOURCE} tree={TREE} api=24 pre_app=true ipv4_drop=true ipv6_drop=true ipv4_probe=true "
        require(len(lines) == 4 and group_record.exists() and lines[1].startswith(prefix)
                and re.fullmatch(r"ipv4_delta=[1-9][0-9]{0,8} ipv6_probe=(?:true|false) ipv6_delta=[0-9]{1,9} held=true",
                                 lines[1][len(prefix):]) is not None
                and lines[2] == "SC_ART_OK tests=8 skipped=0"
                and lines[3] == f"CONTACT_VISUAL_SHUTDOWN:head={SOURCE} tree={TREE} held=true disconnected=true",
                "headless_exact_receipts")
    finally:
        selector.close()
        try:
            cleanup_headless()
        finally:
            if process is not None and process.poll() is None:
                process.kill()
                process.wait(timeout=10)
    return {"pre_app": True, "held_through_shutdown": True, "disconnected": True,
            "owned_scope_empty": True, "tests": 8, "skipped": 0}


def draft() -> dict:
    current = gh_json(f"repos/{REPO}/releases/{RELEASE}")
    require(current["id"] == RELEASE and current["draft"] is True
            and current["tag_name"] == TAG and current["prerelease"] is False, "owned_draft")
    return current


def publish_public_diagnostic(root: Path, payload: bytes) -> None:
    validate_public_diagnostic(payload)
    cleanup()  # No tokenized API use unless exact owned cleanup has succeeded.
    current = draft()
    require(not any(asset["name"] == PUBLIC_DIAGNOSTIC_ASSET for asset in current["assets"]),
            "diagnostic_asset_exists")
    path = root / PUBLIC_DIAGNOSTIC_ASSET
    with path.open("xb") as output:
        output.write(payload)
    path.chmod(0o600)
    draft()
    host_command(["gh", "api", "--method", "POST",
                  f"https://uploads.github.com/repos/{REPO}/releases/{RELEASE}/assets?name={PUBLIC_DIAGNOSTIC_ASSET}",
                  "-H", "Content-Type: application/json", "--input", str(path)], token=True)
    print("phase=public_diagnostic stored=true app_tests=NOT_RUN qualification=NOT_RUN")


def diagnose_public() -> None:
    dispatch_guard()
    require(os.environ.get("SC_RELEASE_TAG") == TAG, "diagnostic_draft_binding")
    mounts = tool_mounts()
    with tempfile.TemporaryDirectory(prefix=owned_name() + "-public-diag-",
                                     dir=os.environ["RUNNER_TEMP"]) as directory:
        root = Path(directory)
        prep = root / "public"
        prep.mkdir(mode=0o755)
        try:
            public_preparation(prep, mounts, diagnose=True)
        except PublicPreparationFailure as error:
            publish_public_diagnostic(root, error.payload)
            raise Refusal("public_preparation_failed") from None
        # Success ends here too: never enter the hosted/private qualification path.
        print("phase=public_diagnostic completed=true app_tests=NOT_RUN qualification=NOT_RUN")


def public_diagnostic_checks() -> None:
    """Synthetic JSON/redaction/control-flow fixtures, without host/target execution."""
    from contextlib import ExitStack, redirect_stdout
    from collections.abc import Callable
    from unittest.mock import Mock, patch

    checks = 0
    def rejected(operation: Callable[[], object]) -> None:
        nonlocal checks
        try:
            operation()
        except Refusal:
            checks += 1
        else:
            raise Refusal("negative_public_diagnostic_fixture_accepted")

    args = {"public": True, "code": 1, "state": {"ExitCode": 1, "OOMKilled": False, "Running": False},
            "bounded": True, "exported": False, "run_id": "123", "controller_sha": "a" * 40}
    canaries = ("192.0.2.7:443", "[2001:db8::7]:443", "repo.example.org", "user@example.org",
                "ghp_SYNTHETIC_FAKE_TOKEN_12345", "github_pat_SYNTHETIC_123456789",
                "SHORT_BEARER", "SHORT_PASSWORD", "SHORT_API_KEY")
    data = ("SC_CAPS_OK\nGET https://repo.example.org/path\n" + " ".join(canaries[:6])
            + '\nAuthorization: Bearer SHORT_BEARER\npassword=SHORT_PASSWORD\napi_key="SHORT_API_KEY"\n').encode()
    payload = public_diagnostic(data, **args)
    record = json.loads(payload)
    require(record["caps_markers"] == 1 and record["export_markers"] == 0
            and record["container_exit"] == 1 and all(c not in record["tail"] for c in canaries),
            "diagnostic_redaction_fixture")
    checks += 1
    large = public_diagnostic((b'"\\\n' * 20000) + b"END_OF_PUBLIC_LOG", **args)
    require(len(large) <= MAX_PUBLIC_DIAGNOSTIC and len(json.loads(large)["tail"].encode()) <= MAX_PUBLIC_TAIL
            and json.loads(large)["tail"].endswith("END_OF_PUBLIC_LOG"), "diagnostic_size_fixture")
    checks += 1
    rejected(lambda: public_diagnostic(b"PRIVATE MUST NOT BE CAPTURED", **{**args, "public": False}))
    rejected(lambda: public_diagnostic(b"x" * (MAX_OUTPUT + 1), **args))
    rejected(lambda: public_diagnostic(data, **{**args, "code": True}))
    rejected(lambda: validate_public_diagnostic(b"not-json"))
    rejected(lambda: validate_public_diagnostic(b"x" * (MAX_PUBLIC_DIAGNOSTIC + 1)))
    for field, value in (("public_only", False), ("private_input_fetched", True),
                         ("tail", "https://unredacted.example.org/"), ("qualification", "PASS")):
        rejected(lambda: validate_public_diagnostic(canonical({**record, field: value})))
    forbidden = Mock(side_effect=AssertionError("private/host operation in pure public fixture"))
    with patch.dict(globals(), {"host_command": forbidden}):
        rejected(lambda: run_container(Path("."), Path("."), Path("."), [], public=False, diagnose=True))
    with tempfile.TemporaryDirectory(prefix="sc-public-diag-fixtures-", dir=Path.cwd()) as directory:
        root = Path(directory)
        events: list[str] = []
        def fake_upload(arguments: list[str], **kwargs: object) -> bytes:
            require(kwargs == {"token": True} and events == ["cleanup", "draft", "draft"]
                    and arguments[4] == f"https://uploads.github.com/repos/{REPO}/releases/{RELEASE}/assets?name={PUBLIC_DIAGNOSTIC_ASSET}"
                    and arguments[-1] == str(root / PUBLIC_DIAGNOSTIC_ASSET)
                    and (root / PUBLIC_DIAGNOSTIC_ASSET).read_bytes() == payload,
                    "diagnostic_upload_order")
            return b"{}"
        with patch.dict(globals(), {"cleanup": lambda: events.append("cleanup"),
                                   "draft": lambda: events.append("draft") or {"assets": []},
                                   "host_command": fake_upload}), redirect_stdout(io.StringIO()):
            publish_public_diagnostic(root, payload)
        checks += 1
        with patch.dict(globals(), {"cleanup": Mock(side_effect=Refusal("cleanup_failed")),
                                   "draft": forbidden, "host_command": forbidden}):
            rejected(lambda: publish_public_diagnostic(root, payload))
        with ExitStack() as stack:
            stack.enter_context(patch.dict(os.environ, {"RUNNER_TEMP": directory, "GITHUB_RUN_ID": "123",
                                                       "SC_RELEASE_TAG": TAG}))
            for name in ("hosted", "extract_input", "trusted_strip_proof", "run_headless", "gh_json"):
                stack.enter_context(patch.dict(globals(), {name: forbidden}))
            stack.enter_context(patch.dict(globals(), {"dispatch_guard": Mock(), "tool_mounts": lambda: []}))
            publisher = Mock()
            prep = Mock(return_value=root)
            stack.enter_context(patch.dict(globals(), {"publish_public_diagnostic": publisher,
                                                       "public_preparation": prep}))
            with redirect_stdout(io.StringIO()):
                diagnose_public()
                publisher.assert_not_called()
                require(prep.call_args.kwargs == {"diagnose": True}, "diagnostic_public_route")
                checks += 1
                prep.side_effect = PublicPreparationFailure(payload)
                rejected(diagnose_public)
                require(publisher.call_count == 1, "diagnostic_failure_publish")
                prep.side_effect = Refusal("public_metadata_failure")
                rejected(diagnose_public)
                require(publisher.call_count == 1, "diagnostic_only_container_failure")
    print(f"SC_SOURCE_CHECKS public_diagnostic={checks} host_target_network_execution=false")


def hosted() -> None:
    dispatch_guard()
    mounts = tool_mounts()  # Public tools and awk resolve before private input.
    expected = os.environ["SC_BUNDLE_SHA256"]
    require(os.environ["SC_RELEASE_TAG"] == TAG and os.environ["SC_ASSET"] == ASSET
            and re.fullmatch(r"[0-9a-f]{64}", expected) is not None, "input_binding")
    with tempfile.TemporaryDirectory(prefix=owned_name() + "-", dir=os.environ["RUNNER_TEMP"]) as directory:
        root = Path(directory)
        prep = root / "public"
        prep.mkdir(mode=0o755)
        public_cache = public_preparation(prep, mounts)
        current = draft()
        matches = [asset for asset in current["assets"] if asset["name"] == ASSET]
        require(len(matches) == 1 and len(current["assets"]) == 1
                and 0 < matches[0]["size"] <= MAX_BUNDLE, "fresh_input_only")
        private = root / "private"
        private.mkdir(mode=0o700)
        def limit() -> None:
            resource.setrlimit(resource.RLIMIT_FSIZE, (MAX_BUNDLE, MAX_BUNDLE))
        bundle = private / "input.tar"
        with bundle.open("xb") as output:
            subprocess.run(["gh", "api", f"repos/{REPO}/releases/assets/{matches[0]['id']}",
                            "-H", "Accept: application/octet-stream"],
                           env={**ENV, "GH_TOKEN": os.environ["GH_TOKEN"]}, stdout=output,
                           stderr=subprocess.DEVNULL, check=True, timeout=120, preexec_fn=limit)
        raw = bundle.read_bytes()
        require(len(raw) == matches[0]["size"] and digest(raw) == expected, "private_bundle_pin")
        source = private / "source"
        source.mkdir(mode=0o755)
        extract_input(raw, source)
        raw = b""
        lineage = trusted_strip_proof(private, source, mounts)
        dependencies = stage_cache(private, source, cache=public_cache)
        apks = run_container(private, source, dependencies, list(mounts), public=False, lineage=lineage)
        # No token or private source is passed to the headless scope.
        receipt = run_headless(root, apks, mounts)
        data = canonical({"kind": "sc-jni-art", "source": SOURCE, "tree": TREE,
                          "source_table_sha256": SOURCE_TABLE_SHA256,
                          "controller_sha": os.environ["SC_CONTROLLER_SHA"],
                          "run_id": os.environ["GITHUB_RUN_ID"], "api": 24,
                          "class": TEST_CLASS, "tests": 8, "skipped": 0,
                          "strip_lineage": lineage,
                          "apks": json.loads((apks / "apk-receipt.json").read_bytes()),
                          "isolation": receipt})
        require(len(data) <= 16384, "receipt_bound")
        path = root / "jni-art-receipt.json"
        path.write_bytes(data)
        path.chmod(0o600)
        draft()
        host_command(["gh", "api", "--method", "POST",
                      f"https://uploads.github.com/repos/{REPO}/releases/{RELEASE}/assets?name=jni-art-receipt.json",
                      "-H", "Content-Type: application/json", "--input", str(path)], token=True)
        print("phase=result api=24 tests=8 skipped=0 synthetic_provider=true accepted=true")


def public_ca_checks() -> None:
    """Synthetic filesystem/byte fixtures only; no real CA store or JVM access."""
    from collections.abc import Callable
    from unittest.mock import patch

    checks = 0
    def rejected(operation: Callable[[], object]) -> None:
        nonlocal checks
        try:
            operation()
        except Refusal:
            checks += 1
        else:
            raise Refusal("negative_public_ca_fixture_accepted")

    with tempfile.TemporaryDirectory(prefix="sc-public-ca-fixtures-", dir=Path.cwd()) as directory:
        root = Path(directory)
        jdk = root / "jdk"
        security = jdk / "lib/security"
        security.mkdir(parents=True)
        candidate = security / "cacerts"
        data = b"synthetic public certificate bytes, not a real truststore"
        candidate.write_bytes(data)
        staged, checksum = stage_public_cacerts(root, jdk)
        require(staged.read_bytes() == data and checksum == digest(data), "public_ca_copy_fixture")
        checks += 1
        candidate.unlink()
        internal = security / "public-store"
        internal.write_bytes(data)
        candidate.symlink_to(internal)
        require(public_cacerts_source(jdk) == internal, "public_ca_internal_alias_fixture")
        checks += 1
        system = root / "system-public-java-cacerts"
        system.write_bytes(data)
        candidate.unlink()
        candidate.symlink_to(system)
        with patch.dict(globals(), {"SYSTEM_JAVA_CACERTS": system}):
            require(public_cacerts_source(jdk) == system, "public_ca_system_alias_fixture")
            checks += 1
        rejected(lambda: public_cacerts_source(jdk))
        candidate.unlink()
        rejected(lambda: public_cacerts_source(jdk))
        candidate.symlink_to(internal)
        internal.write_bytes(b"changed source")
        rejected(lambda: verify_public_cacerts(jdk, staged, checksum))
        internal.write_bytes(data)
        staged.chmod(0o600)
        staged.write_bytes(b"changed snapshot")
        rejected(lambda: verify_public_cacerts(jdk, staged, checksum))
        with patch.dict(globals(), {"MAX_PUBLIC_CACERTS": 1}):
            rejected(lambda: public_cacerts_source(jdk))
        with patch.object(os, "access", return_value=False):
            rejected(lambda: public_cacerts_source(jdk))
    print(f"SC_SOURCE_CHECKS public_ca={checks} actual_host_ca_jvm_network_execution=false")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="mode", required=True)
    packing = commands.add_parser("pack")
    packing.add_argument("--repo", required=True)
    packing.add_argument("--output", required=True)
    commands.add_parser("hosted")
    commands.add_parser("diagnose-public")
    commands.add_parser("public-diagnostic-checks")
    commands.add_parser("public-ca-checks")
    commands.add_parser("admission")
    commands.add_parser("cleanup")
    commands.add_parser("container-work")
    commands.add_parser("lineage-checks")
    result = commands.add_parser("instrumentation")
    result.add_argument("--result", required=True)
    args = parser.parse_args()
    try:
        if args.mode == "pack":
            pack(args)
        elif args.mode == "admission":
            dispatch_guard()
            print("phase=admission accepted=true")
        elif args.mode == "diagnose-public":
            diagnose_public()
        elif args.mode == "public-diagnostic-checks":
            public_diagnostic_checks()
        elif args.mode == "public-ca-checks":
            public_ca_checks()
        elif args.mode == "cleanup":
            cleanup()
        elif args.mode == "container-work":
            container_work()
        elif args.mode == "lineage-checks":
            lineage_checks()
        elif args.mode == "instrumentation":
            path = Path(args.result)
            regular(path, MAX_OUTPUT, "instrumentation_result")
            instrumentation(path.read_bytes())
            print("SC_ART_OK tests=8 skipped=0")
        else:
            hosted()
    except Exception as error:
        label = str(error) if type(error) is Refusal else "operation"
        print("phase=" + args.mode + " accepted=false blocker=" + label, file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
