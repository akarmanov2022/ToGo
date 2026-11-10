"""Exercise deployment failure boundaries without connecting to a real server."""

import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SHA = "a" * 40
OLD_SHA = "b" * 40
FAKE_COMMAND = r'''#!/usr/bin/env python3
import io
import json
import os
from pathlib import Path
import sys
import tarfile

name = Path(sys.argv[0]).name
args = sys.argv[1:]
if name == "sshpass":
    assert args[:2] == ["-e", "ssh"]
    assert os.environ["SSHPASS"] == "fake-password with ' quotes $ and spaces"
    os.execvp(args[1], args[1:])
entry = {"command": name, "args": args, "auth": os.getenv("DOCKER_CONFIG")}
if name == "ssh":
    entry["known_hosts"] = next(a.split("=", 1)[1] for a in args if a.startswith("UserKnownHostsFile="))
    payload = sys.stdin.buffer.read() if "mkdir -p" not in args[-1] else b""
    if "tar -xzf" in args[-1]:
        with tarfile.open(fileobj=io.BytesIO(payload), mode="r:gz") as archive:
            entry["files"] = archive.getnames()
    elif "bash " in args[-1]:
        assert payload == b"fake-registry-token\n"
with open(os.environ["CALLS"], "a") as log:
    log.write(json.dumps(entry) + "\n")
failure = os.getenv("FAIL_AT")
if name == "docker":
    if args[0] == "inspect":
        template = args[2]
        if "com.docker.compose.project" in template:
            print("<no value>" if failure == "project" else "existing_project")
        elif "Mounts" in template:
            print("existing_postgres_data")
        elif "State.Running" in template:
            print("true")
        elif "image.revision" in template:
            print("wrong-version" if failure == "revision" else os.environ["TOGO_RELEASE_SHA"])
    elif args[0] == "login":
        assert sys.stdin.read() == "fake-registry-token\n"
    elif args[0] == "compose":
        if failure and failure in args:
            sys.exit(1)
elif name == "curl" and failure == "https":
    sys.exit(1)
elif name == "ssh" and failure == "ssh":
    sys.exit(255)
'''


class DeploymentTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.bin = self.directory / "bin"
        self.bin.mkdir()
        for command in ("docker", "curl", "flock", "ssh", "sshpass"):
            path = self.bin / command
            path.write_text(FAKE_COMMAND)
            path.chmod(0o755)
        self.server = self.directory / "server"
        (self.server / "infra").mkdir(parents=True)
        self.env_file = self.server / "infra/.env"
        self.env_file.write_text("DB_USER=togo\nDB_PASSWORD=fake-password\n")
        (self.server / ".deploy").mkdir()
        self.current = self.server / ".deploy/current"
        self.current.write_text(OLD_SHA + "\n")
        self.calls_file = self.directory / "calls.jsonl"
        self.env = {
            **os.environ,
            "PATH": str(self.bin) + os.pathsep + os.environ["PATH"],
            "CALLS": str(self.calls_file),
        }

    def calls(self):
        if not self.calls_file.exists():
            return []
        return [json.loads(line) for line in self.calls_file.read_text().splitlines()]

    def deploy(self, fail_at=""):
        result = subprocess.run(
            ["bash", str(ROOT / "scripts/deploy.sh"), SHA, str(self.server), "kolzuk"],
            input="fake-registry-token\n", text=True, capture_output=True,
            env={**self.env, "FAIL_AT": fail_at}, timeout=10,
        )
        self.assertNotIn("fake-registry-token", result.stdout + result.stderr)
        for call in self.calls():
            if call["command"] == "docker" and call["args"][0] == "login":
                self.assertFalse(Path(call["auth"]).exists(), "Registry credentials must be cleaned up")
        return result

    def test_deploy_preserves_database_and_records_only_verified_release(self):
        result = self.deploy()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.current.read_text().strip(), SHA)
        self.assertEqual((self.server / ".deploy/previous").read_text().strip(), OLD_SHA)
        commands = [c["args"] for c in self.calls() if c["command"] == "docker"]
        up = next(args for args in commands if "up" in args)
        self.assertEqual(up[-3:], ["backend", "frontend", "nginx"])
        self.assertIn("--no-deps", up)
        self.assertIn("--no-build", up)
        self.assertIn("--wait", up)
        self.assertEqual(up[up.index("--project-name") + 1], "existing_project")
        self.assertEqual(up[up.index("--env-file") + 1], str(self.env_file))
        self.assertFalse(any("down" in args or "volume" in args for args in commands))
        self.assertLess(next(i for i, a in enumerate(commands) if "pull" in a), commands.index(up))

    def test_failed_pull_or_nginx_validation_does_not_replace_running_containers(self):
        for failure in ("pull", "run"):
            with self.subTest(failure=failure):
                self.calls_file.unlink(missing_ok=True)
                self.assertNotEqual(self.deploy(failure).returncode, 0)
                self.assertFalse(any("up" in c["args"] for c in self.calls()))
                self.assertEqual(self.current.read_text().strip(), OLD_SHA)

    def test_failed_healthcheck_revision_or_https_does_not_mark_release_successful(self):
        for failure in ("up", "revision", "https"):
            with self.subTest(failure=failure):
                self.calls_file.unlink(missing_ok=True)
                self.assertNotEqual(self.deploy(failure).returncode, 0)
                self.assertEqual(self.current.read_text().strip(), OLD_SHA)

    def test_missing_env_stops_before_docker(self):
        self.env_file.unlink()
        self.assertNotEqual(self.deploy().returncode, 0)
        self.assertEqual(self.calls(), [])

    def test_unknown_existing_database_stops_before_pulling_images(self):
        self.assertNotEqual(self.deploy("project").returncode, 0)
        self.assertFalse(any("pull" in c["args"] for c in self.calls()))

    def remote_deploy(self, **overrides):
        return subprocess.run(
            ["bash", str(ROOT / "scripts/deploy-remote.sh")],
            text=True, capture_output=True, timeout=10,
            env={**self.env, "TOGO_RELEASE_SHA": SHA, "USER_IP": "example.com",
                 "USERNAME": "deploy", "ROOT_PASSWORD": "fake-password with ' quotes $ and spaces",
                 "DEPLOY_KNOWN_HOSTS": "fake-known-host", "DEPLOY_PATH": "/srv/togo",
                 "DOCKERHUB_USERNAME": "kolzuk", "DOCKERHUB_TOKEN": "fake-registry-token", **overrides},
        )

    def test_password_ssh_preserves_stdin_and_cleans_up_known_hosts(self):
        result = self.remote_deploy()
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = self.calls()
        self.assertEqual(len(calls), 3)
        for call in calls:
            self.assertIn("StrictHostKeyChecking=yes", call["args"])
            self.assertIn("PreferredAuthentications=password", call["args"])
            self.assertIn("PubkeyAuthentication=no", call["args"])
            self.assertIn("deploy@example.com", call["args"])
            self.assertNotIn("-i", call["args"])
        self.assertFalse(Path(calls[0]["known_hosts"]).exists())
        files = calls[1]["files"]
        self.assertIn("scripts/deploy.sh", files)
        self.assertIn("infra/docker-compose.prod.yml", files)
        self.assertFalse(any(".env" in f or f.endswith(".pem") for f in files))
        self.assertNotIn("fake-registry-token", self.calls_file.read_text() + result.stdout + result.stderr)
        self.assertNotIn("fake-password", self.calls_file.read_text() + result.stdout + result.stderr)

    def test_missing_password_stops_before_ssh(self):
        result = self.remote_deploy(ROOT_PASSWORD="")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("ROOT_PASSWORD", result.stderr)
        self.assertEqual(self.calls(), [])

    def test_ssh_failure_stops_before_upload_and_cleans_up_known_hosts(self):
        result = self.remote_deploy(FAIL_AT="ssh")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(len(self.calls()), 1)
        self.assertFalse(Path(self.calls()[0]["known_hosts"]).exists())


if __name__ == "__main__":
    unittest.main()
