#!/usr/bin/env python3
"""Extract the BuildKit provenance attestation from an OCI image layout.

`docker buildx build --provenance=mode=max --output type=oci` writes the image
index, the platform manifest and a separate attestation manifest into an OCI
layout: an `index.json` plus a `blobs/` tree, either as a directory or as a tar.
This script pulls the SLSA statement out of that layout so the pipeline can
archive it as evidence and assert it exists, instead of trusting that the flag
did something.

A directory is the form the pipeline uses: `trivy image --input` reads a
directory layout but not a tar, and scanning the attested image is the point of
building it once.

Usage: extract-provenance.py <oci-layout-dir|image.tar> <out.json>
"""

import json
import sys
import tarfile
from pathlib import Path

SLSA_V1 = "https://slsa.dev/provenance/v1"
ATTESTATION_TYPE = "attestation-manifest"
REFERENCE_TYPE_KEY = "vnd.docker.reference.type"
REFERENCE_DIGEST_KEY = "vnd.docker.reference.digest"


class Layout:
    """Reads index and blobs from an OCI layout directory or tar."""

    def __init__(self, path: Path):
        self.path = path
        self._tar = None if path.is_dir() else tarfile.open(path)

    def read_document(self, name: str) -> dict:
        if self._tar is None:
            with open(self.path / name, encoding="utf-8") as handle:
                return json.load(handle)
        member = self._tar.extractfile(name)
        if member is None:
            raise SystemExit(f"missing {name} in the OCI layout")
        return json.load(member)

    def read_blob(self, digest: str) -> dict:
        return self.read_document("blobs/" + digest.replace(":", "/", 1))

    def close(self) -> None:
        if self._tar is not None:
            self._tar.close()


def collect_manifests(layout: Layout) -> list[dict]:
    """Every manifest entry the layout exposes, top level and nested.

    Buildx writes a nested index for a single-platform build, so the platform
    manifest and its attestation sit one level below `index.json`. Walking the
    whole graph is what makes a digest reference checkable regardless of the
    layout depth.
    """
    collected = []
    pending = list(layout.read_document("index.json")["manifests"])
    seen = set()
    while pending:
        entry = pending.pop(0)
        digest = entry["digest"]
        if digest in seen:
            continue
        seen.add(digest)
        collected.append(entry)
        document = layout.read_blob(digest)
        nested = document.get("manifests")
        if nested:
            pending.extend(nested)
    return collected


def main() -> int:
    if len(sys.argv) != 3:
        raise SystemExit(
            "usage: extract-provenance.py <oci-layout-dir|image.tar> <out.json>"
        )

    layout = Layout(Path(sys.argv[1]))
    destination = sys.argv[2]
    try:
        manifests = collect_manifests(layout)
        digests = {entry["digest"] for entry in manifests}
        for entry in manifests:
            annotations = entry.get("annotations", {})
            if annotations.get(REFERENCE_TYPE_KEY) != ATTESTATION_TYPE:
                continue
            attestation = layout.read_blob(entry["digest"])
            statement = layout.read_blob(attestation["layers"][0]["digest"])
            if statement.get("predicateType") != SLSA_V1:
                raise SystemExit(
                    f"unexpected predicateType: {statement.get('predicateType')}"
                )
            # An attestation proves something about a digest. Requiring the
            # digest it names to be one of this layout's manifests is what ties
            # the statement to the image the pipeline scanned; without it the
            # file would be evidence of a build, not of this build.
            reference = annotations.get(REFERENCE_DIGEST_KEY)
            if not reference:
                raise SystemExit(
                    "attestation manifest carries no reference digest; the "
                    "statement cannot be tied to an image"
                )
            if reference not in digests:
                raise SystemExit(
                    f"attestation names {reference}, which is not a manifest of "
                    "this layout"
                )
            subject = statement.get("subject") or []
            if subject:
                named = subject[0].get("digest", {}).get("sha256")
                if named and f"sha256:{named}" != reference:
                    raise SystemExit(
                        "provenance subject and attestation reference digest disagree"
                    )
            with open(destination, "w", encoding="utf-8") as out:
                json.dump(statement, out, indent=2, sort_keys=True)
                out.write("\n")
            print(f"provenance written to {destination} ({SLSA_V1}) for {reference}")
            return 0
    finally:
        layout.close()

    raise SystemExit("no build provenance attestation found; was --provenance set?")


if __name__ == "__main__":
    sys.exit(main())
