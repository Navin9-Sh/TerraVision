"""Launcher that runs the TerraVision Spring Boot app on a Hugging Face Space.

A Gradio-type Space runs `python app.py` and serves whatever listens on port 7860. This
file never uses Gradio: it downloads a Java runtime, the application jar and the model,
then replaces itself with the JVM running Spring Boot on that port.

Only the Python standard library is used. Configuration (database, secrets) comes from
the Space's "Variables and secrets" settings, which arrive as environment variables.
"""
import os
import pathlib
import shutil
import sys
import tarfile
import time
import urllib.request

HOME = pathlib.Path(os.environ.get("TERRAVISION_HOME", pathlib.Path(__file__).resolve().parent / "runtime"))
REPO = os.environ.get("TERRAVISION_REPO", "Navin9-Sh/TerraVision")

JRE_URL = os.environ.get(
    "TERRAVISION_JRE_URL",
    "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jre/hotspot/normal/eclipse",
)
JAR_URL = os.environ.get(
    "TERRAVISION_JAR_URL",
    f"https://github.com/{REPO}/releases/download/deploy-latest/terravision.jar",
)
MODEL_BASE_URL = os.environ.get(
    "TERRAVISION_MODEL_BASE_URL",
    f"https://raw.githubusercontent.com/{REPO}/main/backend/model",
)
MODEL_FILES = ["terravision-resnet50.pt", "classes.json"]

PORT = os.environ.get("TERRAVISION_PORT", "7860")
HEAP = os.environ.get("TERRAVISION_HEAP", "1g")


def log(message):
    print(f"[launcher] {message}", flush=True)


def download(url, destination, attempts=4):
    """Streams url to destination (via a .part file, so a crash never leaves a half file)."""
    destination = pathlib.Path(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    partial = destination.with_name(destination.name + ".part")

    for attempt in range(1, attempts + 1):
        try:
            log(f"downloading {destination.name} (attempt {attempt}/{attempts})")
            request = urllib.request.Request(url, headers={"User-Agent": "terravision-launcher"})
            with urllib.request.urlopen(request, timeout=60) as response, open(partial, "wb") as out:
                total = int(response.headers.get("Content-Length") or 0)
                done = 0
                next_report = 25 * 1024 * 1024
                while True:
                    chunk = response.read(1024 * 1024)
                    if not chunk:
                        break
                    out.write(chunk)
                    done += len(chunk)
                    if done >= next_report:
                        log(f"  {done // (1024 * 1024)} MB" + (f" of {total // (1024 * 1024)} MB" if total else ""))
                        next_report += 25 * 1024 * 1024
            if total and done != total:
                raise IOError(f"incomplete download: {done} of {total} bytes")
            partial.replace(destination)
            log(f"  saved {destination.name} ({destination.stat().st_size // (1024 * 1024)} MB)")
            return
        except Exception as error:  # network hiccups are the expected failure here
            log(f"  failed: {error}")
            if attempt == attempts:
                raise
            time.sleep(5 * attempt)


def ensure_java():
    jre_dir = HOME / "jre"
    found = list(jre_dir.glob("*/bin/java")) if jre_dir.exists() else []
    if not found:
        archive = HOME / "jre.tar.gz"
        download(JRE_URL, archive)
        if jre_dir.exists():
            shutil.rmtree(jre_dir)
        jre_dir.mkdir(parents=True)
        with tarfile.open(archive) as tar:
            tar.extractall(jre_dir)
        archive.unlink()
        found = list(jre_dir.glob("*/bin/java"))
    if not found:
        sys.exit("[launcher] could not find bin/java in the downloaded JRE")
    java = found[0]
    java.chmod(0o755)
    return java


def ensure_model():
    for name in MODEL_FILES:
        target = HOME / "model" / name
        if not target.exists():
            download(f"{MODEL_BASE_URL}/{name}", target)


def main():
    log(f"working directory: {HOME}")
    HOME.mkdir(parents=True, exist_ok=True)

    java = ensure_java()
    ensure_model()

    # The jar is refreshed on every start so a restart picks up a new deployment.
    jar = HOME / "terravision.jar"
    download(JAR_URL, jar)

    env = os.environ.copy()
    env["PORT"] = PORT
    env.setdefault("SPRING_PROFILES_ACTIVE", "prod")
    # DJL downloads/extracts its native PyTorch library at first start; keep that in a
    # directory we know is writable.
    env.setdefault("DJL_CACHE_DIR", str(HOME / "djl"))
    env.setdefault("ENGINE_CACHE_DIR", str(HOME / "djl"))

    command = [
        str(java),
        f"-Xmx{HEAP}",
        "-XX:+UseSerialGC",
        "-Djava.io.tmpdir=" + str(HOME / "tmp"),
        "-jar",
        str(jar),
    ]
    (HOME / "tmp").mkdir(exist_ok=True)

    log("starting Spring Boot on port " + PORT)
    os.chdir(HOME)  # the app loads ./model relative to its working directory
    os.execve(command[0], command, env)


if __name__ == "__main__":
    main()
