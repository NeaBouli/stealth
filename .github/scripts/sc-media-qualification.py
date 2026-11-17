#!/usr/bin/env python3
"""Temporary offline JVM qualification with synthetic provider config; NEVER MERGE."""

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
import xml.etree.ElementTree as ET

SOURCE = "b840ada3faea56782f81b234945ef6ad4c7e5e56"
TREE = "c3c7094d1c07441ffaf3dd1bbfee07c60686ed6c"
SOURCE_TABLE_SHA256 = "42b2c32180253648dd3787ba42a8a4d8ef49c4c972a8654d53a81d3a6bae4f28"
FIXTURE_SHA256 = "a466488db1f666da3f1b941ad4c389a6b7940d5c93662adf25f77fb781fd63c7"
PROVIDER = "client_android/app/google-services.json"
PACKAGES = ("com.securecall.app.free", "com.securecall.app.premium", "com.securecall.app.pro")
CLASSES = ("com.securecall.app.crypto.MediaSessionV3Test",
           "com.securecall.app.crypto.SessionKeyBindingTest",
           "com.securecall.app.net.CallMediaReceiveGateTest",
           "com.securecall.app.security.IdentityProtocolTest",
           "com.securecall.app.net.AudioPlaybackLifecycleTest")
CASES = (11, 4, 5, 5, 4)
FLAVORS = ("Free", "Premium")
WRAPPER_SHA256 = "46ac66d47f30f3dacfdf306e0b714a91a34fb94a22ba0a744b280933f47bc0cf"
WRAPPER_PATH = "wrapper/dists/gradle-9.8.0-all/619e6l8zt76uzaekhqdol0b60/gradle-9.8.0-all.zip"
MAX_BUNDLE = 64 * 1024**2
MAX_OUTPUT = 512 * 1024
REPO = "NeaBouli/stealth"
REF = "refs/heads/temp/sc-stx24-jvm-hosted-20261117"
RELEASE = 447762646
TAG = "sc-stx24-jvm-input-20261116-2346"
ASSET = "sc-stx24-jvm-input.tar"
LABEL = "sc-stx24-qualification"
ENV = {"PATH": "/usr/bin:/bin", "LC_ALL": "C", "HOME": "/nonexistent",
       "GIT_CONFIG_NOSYSTEM": "1", "GIT_CONFIG_GLOBAL": "/dev/null",
       "GIT_NO_REPLACE_OBJECTS": "1", "GIT_OPTIONAL_LOCKS": "0"}


class Refusal(ValueError):
    """Fixed trusted reason, never text from source, tool or target exceptions."""


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
            and "@RunWith" not in text, "test_source_contract")
    return set(names)


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
    for entry in git_read("ls-tree", "-rz", SOURCE, "client_android").rstrip(b"\0").split(b"\0"):
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
    require(len(table) == 796 and digest(canonical(table)) == SOURCE_TABLE_SHA256, "source_table_pin")
    for name, count in zip(CLASSES, CASES):
        methods(files["client_android/app/src/test/java/" + name.replace(".", "/") + ".kt"], count)
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
        print(f"PACK source={SOURCE} code_files=796 synthetic_provider=true bundle_sha256={checksum}")
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
    require(len(table) == 796 and canonical(table) == table_bytes
            and all(source_path(p) and p != PROVIDER for p in table)
            and files.pop(PROVIDER) == provider_fixture(), "fixture_or_source_paths")
    require(table == {p: digest(data) for p, data in files.items()}, "source_file_hashes")
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


def cleanup() -> None:
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


def stage_cache(root: Path, source: Path) -> Path:
    # The public cache is restored before private inputs arrive. Never copy its
    # home/init/properties/daemon/config-cache; authenticate selected artifacts.
    cache = Path("/home/runner/.gradle")
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
    for build_index, build in enumerate(("client_android/build.gradle", "client_android/app/build.gradle")):
        coordinates = re.findall(
            r"(?m)^\s*(?:classpath|implementation|freeImplementation|premiumImplementation|testImplementation)"
            r"\s+(?:platform\()?['\"]([^'\"]+)['\"]", (source / build).read_text())
        for index, coordinate in enumerate(coordinates):
            parts = tuple(coordinate.split(":"))
            require(any(p[:len(parts)] == parts for p in present), f"direct_dependency_{build_index}_{index}_absent")
    metadata = origin / "metadata-2.107"
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
    print("phase=toolchain accepted=true jdk=17 sdk=37", flush=True)
    return mounts


def receipts() -> None:
    events = dict(line.split() for line in Path("/sys/fs/cgroup/memory.events").read_text().splitlines())
    require(all(int(events.get(key, "0")) == 0 for key in ("oom", "oom_kill", "oom_group_kill")), "jvm_cgroup_oom")
    base = Path("/scratch/project/client_android/app/build/test-results")
    lines = []
    for f, flavor in enumerate(FLAVORS):
        directory = base / f"test{flavor}DebugUnitTest"
        require({p.name for p in directory.glob("TEST-*.xml")} == {f"TEST-{name}.xml" for name in CLASSES}, "junit_exact_classes")
        for c, (name, count) in enumerate(zip(CLASSES, CASES)):
            path = directory / f"TEST-{name}.xml"
            regular(path, 256 * 1024, "junit_file_shape")
            data = path.read_bytes()
            require(b"<!DOCTYPE" not in data and b"<!ENTITY" not in data, "junit_xml_shape")
            suite = ET.fromstring(data)
            expected = methods((Path("/source/client_android/app/src/test/java") /
                                (name.replace(".", "/") + ".kt")).read_bytes(), count)
            cases = suite.findall("testcase")
            require(suite.tag == "testsuite" and suite.attrib["name"] == name
                    and int(suite.attrib["tests"]) == count
                    and all(int(suite.attrib[k]) == 0 for k in ("failures", "errors", "skipped"))
                    and len(cases) == count and {case.attrib["name"] for case in cases} == expected
                    and all(case.attrib["classname"] == name for case in cases)
                    and all(not list(suite.iter(k)) for k in ("failure", "error", "skipped")), "junit_cases")
            lines.append(f"SC_JUNIT flavor={f} class={c} tests={count} failures=0 errors=0 skipped=0")
    print("\n".join(lines))
    print(f"SC_JUNIT_OK classes={len(CLASSES) * len(FLAVORS)} tests={sum(CASES) * len(FLAVORS)}")


def hosted() -> None:
    require(os.environ.get("GITHUB_REPOSITORY") == REPO
            and os.environ.get("GITHUB_EVENT_NAME") == "workflow_dispatch"
            and os.environ.get("GITHUB_RUN_ATTEMPT") == "1"
            and os.environ.get("SC_RUNNER_ENVIRONMENT") == "github-hosted"
            and os.environ.get("GITHUB_REF") == REF
            and os.environ.get("SC_CONTROLLER_SHA") == os.environ.get("GITHUB_SHA")
            and re.fullmatch(r"[0-9a-f]{40}", os.environ["SC_CONTROLLER_SHA"]) is not None, "dispatch")
    require(host_command(["git", "rev-parse", "HEAD"]).decode().strip() == os.environ["SC_CONTROLLER_SHA"])
    require(shutil.disk_usage(os.environ["RUNNER_TEMP"]).free >= 6 * 1024**3, "host_disk_spare")
    memory = Path("/proc/meminfo").read_text()
    require(int(re.search(r"MemAvailable:\s+(\d+)", memory)[1]) >= 5 * 1024**2, "host_memory_spare")
    require(not host_command(["docker", "ps", "-q"]).strip(), "foreign_container")
    processes = host_command(["ps", "-e", "-o", "comm="]).decode().splitlines()
    require(not {"cargo", "rustc", "gradle", "java"}.intersection(p.strip() for p in processes), "foreign_workload")
    driver = host_command(["docker", "info", "--format", "{{.CgroupVersion}} {{.CgroupDriver}}"]).decode().strip()
    require(driver in {"2 systemd", "2 cgroupfs"}, "cgroup_v2_driver")
    cgroup_parent = "system.slice" if driver == "2 systemd" else "/docker"
    mounts = tool_mounts()
    tag, asset, expected = (os.environ[key] for key in ("SC_RELEASE_TAG", "SC_ASSET", "SC_BUNDLE_SHA256"))
    require(tag == TAG and asset == ASSET and re.fullmatch(r"[0-9a-f]{64}", expected) is not None)
    release = gh_json(f"repos/{REPO}/releases/{RELEASE}")
    require(release["id"] == RELEASE and release["draft"] is True and release["tag_name"] == TAG)
    matches = [item for item in release["assets"] if item["name"] == ASSET]
    require(len(matches) == 1 and 0 < matches[0]["size"] <= MAX_BUNDLE)
    with tempfile.TemporaryDirectory(prefix="sc-stx24-", dir=os.environ["RUNNER_TEMP"]) as directory:
        root = Path(directory)
        def download_limit() -> None:
            resource.setrlimit(resource.RLIMIT_FSIZE, (MAX_BUNDLE, MAX_BUNDLE))

        with (root / "input.tar").open("xb") as stream:
            subprocess.run(["gh", "api", f"repos/{REPO}/releases/assets/{matches[0]['id']}", "-H", "Accept: application/octet-stream"],
                           env={**ENV, "GH_TOKEN": os.environ["GH_TOKEN"]}, stdout=stream,
                           stderr=subprocess.DEVNULL, check=True, timeout=120, preexec_fn=download_limit)
        raw = (root / "input.tar").read_bytes()
        require(len(raw) == matches[0]["size"] and digest(raw) == expected)
        source = root / "source"
        source.mkdir(mode=0o755)
        extract_input(raw, source)
        raw = b""
        dependencies = stage_cache(root, source)
        # Override Docker's generated account/network files with fixed readonly
        # inputs, rather than leave writable /etc files outside scratch.
        for filename, content in {"hosts": b"127.0.0.1 localhost\n::1 localhost\n",
                                  "hostname": b"qualification\n", "resolv.conf": b""}.items():
            path = root / filename
            path.write_bytes(content)
            path.chmod(0o644)
            mounts.append((str(path), "/etc/" + filename))
        name, image = owned_name(), owned_name() + ":empty"
        empty = io.BytesIO()
        with tarfile.open(fileobj=empty, mode="w", format=tarfile.USTAR_FORMAT) as archive:
            for path in ("usr", "usr/bin", "usr/lib", "usr/lib64", "bin", "lib", "lib64", "etc", "source", "jdk", "sdk", "dependencies", "scratch"):
                member = tarfile.TarInfo(path)
                member.type, member.mode = tarfile.DIRTYPE, 0o755
                archive.addfile(member)
            member = tarfile.TarInfo("tmp")
            member.type, member.linkname, member.mode = tarfile.SYMTYPE, "/scratch/tmp", 0o777
            archive.addfile(member)
            for path, data in {"etc/passwd": b"nobody:x:65534:65534:Qualification:/scratch/home:/bin/sh\n",
                               "etc/group": b"nogroup:x:65534:\n",
                               "etc/nsswitch.conf": b"passwd: files\ngroup: files\nhosts: files\n"}.items():
                member = tarfile.TarInfo(path)
                member.size, member.mode = len(data), 0o644
                archive.addfile(member, io.BytesIO(data))
        try:
            image_id = host_command(["docker", "import", "--change", f"LABEL {LABEL}={name}", "-", image], stdin=empty.getvalue()).decode().strip()
            mounts += [(str(source), "/source"), (str(dependencies), "/dependencies"), (str(root / "wrapper.zip"), "/wrapper.zip"),
                       (str(Path(__file__).resolve(strict=True)), "/controller.py")]
            environment = {"PATH": "/jdk/bin:/usr/bin:/bin", "JAVA_HOME": "/jdk", "ANDROID_HOME": "/sdk", "ANDROID_SDK_ROOT": "/sdk",
                           "ANDROID_USER_HOME": "/scratch/android", "HOME": "/scratch/home", "GRADLE_USER_HOME": "/scratch/gradle",
                           "GRADLE_RO_DEP_CACHE": "/dependencies", "TMPDIR": "/scratch/tmp", "LC_ALL": "C.UTF-8",
                           "GRADLE_OPTS": "-Dorg.gradle.jvmargs=-Xmx1024m -Dorg.gradle.java.installations.auto-download=false -Dorg.gradle.project.android.builder.sdkDownload=false",
                           "JAVA_TOOL_OPTIONS": "-XX:ActiveProcessorCount=1 -XX:ParallelGCThreads=1 -XX:ConcGCThreads=1 -XX:CICompilerCount=2 -XX:MaxMetaspaceSize=384m -XX:ReservedCodeCacheSize=128m -Xss512k -Djava.io.tmpdir=/scratch/tmp -Duser.home=/scratch/home"}
            gradle = ["./gradlew", "--no-daemon", "--max-workers=1", "--offline", "--console=plain", "-Pinternal"]
            for flavor in FLAVORS:
                gradle.append(f":app:test{flavor}DebugUnitTest")
                for name_filter in CLASSES:
                    gradle += ["--tests", name_filter]
            script = ("test \"$(cat /sys/fs/cgroup/memory.max)\" = 4294967296; "
                      "test \"$(cat /sys/fs/cgroup/memory.swap.max)\" = 0; "
                      "test \"$(cat /sys/fs/cgroup/pids.max)\" = 256; "
                      "set -- $(cat /sys/fs/cgroup/cpu.max); test \"$1\" = \"$2\"; "
                      "test \"$(id -u)\" = 65534; test \"$(ulimit -t)\" = 840; test \"$(ulimit -n)\" = 1024; "
                      "grep -q '^Max file size[[:space:]]*536870912[[:space:]]*536870912[[:space:]]*bytes' /proc/self/limits; "
                      "grep -q '^NoNewPrivs:[[:space:]]*1$' /proc/self/status; "
                      "grep -q '^CapEff:[[:space:]]*0000000000000000$' /proc/self/status; "
                      "echo SC_CAPS_OK; mkdir -p /scratch/home /scratch/tmp /scratch/android /scratch/project; "
                      "cp -R /source/client_android /scratch/project/; mkdir -p /scratch/gradle/" + str(Path(WRAPPER_PATH).parent) + "; "
                      "cp /wrapper.zip /scratch/gradle/" + WRAPPER_PATH + "; cd /scratch/project/client_android; chmod +x gradlew; "
                      + " ".join(gradle) + "; exec /usr/bin/python3 -I /controller.py receipts")
            command = ["docker", "create", "--name", name, "--label", f"{LABEL}={name}", "--network", "none", "--read-only",
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
                    and limits["ReadonlyRootfs"] is True and limits["NetworkMode"] == "none"
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
            print("phase=admitted source=" + SOURCE + " synthetic_provider=true offline=true memory_mib=4096 pids=256 cpu=1", flush=True)
            started, data, bounded = time.monotonic(), bytearray(), True
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
            expected_lines = [f"SC_JUNIT flavor={f} class={c} tests={n} failures=0 errors=0 skipped=0"
                              for f in range(2) for c, n in enumerate(CASES)] + [f"SC_JUNIT_OK classes={len(CLASSES) * len(FLAVORS)} tests={sum(CASES) * len(FLAVORS)}"]
            accepted = (bounded and code == 0 and state["ExitCode"] == 0 and state["Running"] is False
                        and state["OOMKilled"] is False and lines.count("SC_CAPS_OK") == 1
                        and lines[-len(expected_lines):] == expected_lines
                        and sum(line.startswith("SC_JUNIT") for line in lines) == len(expected_lines))
            diagnostic = root / "jvm-filtered.txt"
            diagnostic.write_bytes(bytes(data))
            diagnostic.chmod(0o600)
            current = gh_json(f"repos/{REPO}/releases/{RELEASE}")
            require(current["id"] == RELEASE and current["draft"] is True and current["tag_name"] == TAG)
            host_command(["gh", "api", "--method", "POST",
                          f"https://uploads.github.com/repos/{REPO}/releases/{RELEASE}/assets?name=jvm-filtered.txt",
                          "-H", "Content-Type: text/plain", "--input", str(diagnostic)], token=True)
            if accepted:
                for receipt in expected_lines[:-1]:
                    print("phase=class_receipt " + receipt.removeprefix("SC_JUNIT "))
            print(f"phase=result expected_classes={len(CLASSES) * len(FLAVORS)} expected_tests={sum(CASES) * len(FLAVORS)} synthetic_provider=true accepted={str(accepted).lower()}")
            require(accepted, "jvm_result")
        finally:
            cleanup()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="mode", required=True)
    packing = commands.add_parser("pack")
    packing.add_argument("--repo", required=True)
    packing.add_argument("--output", required=True)
    commands.add_parser("hosted")
    commands.add_parser("cleanup")
    commands.add_parser("receipts")
    args = parser.parse_args()
    try:
        if args.mode == "pack":
            pack(args)
        elif args.mode == "cleanup":
            cleanup()
        elif args.mode == "receipts":
            receipts()
        else:
            hosted()
    except Exception as error:
        label = str(error) if type(error) is Refusal else "operation"
        print("phase=" + args.mode + " accepted=false blocker=" + label, file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
