#!/usr/bin/env python3
"""Extract the SLSA predicate from a BuildKit build metadata document.

BuildKit keeps its provenance inside the image index, where it is addressed to
the platform manifest rather than to the index the release signs, and
registries expose it in ways cosign cannot query — `cosign verify-attestation`
reports "no matching attestations" for an image that demonstrably carries one.
The build metadata holds the same provenance in a form the pipeline can read,
and publishing it as a cosign attestation attaches it to the digest that was
signed, which is the digest a deployment pulls and verifies.

Usage: extract-predicate.py <build-metadata.json> <out.json>
"""

import json
import sys

PROVENANCE_KEY = "buildx.build.provenance"


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__.strip(), file=sys.stderr)
        return 2

    with open(sys.argv[1], encoding="utf-8") as handle:
        metadata = json.load(handle)

    provenance = metadata.get(PROVENANCE_KEY)
    if not provenance:
        print(f"no {PROVENANCE_KEY} in the build metadata", file=sys.stderr)
        return 1

    # The metadata carries the whole in-toto statement; cosign wants the
    # predicate and builds the statement itself, binding it to the digest.
    predicate = provenance.get("predicate", provenance)

    with open(sys.argv[2], "w", encoding="utf-8") as handle:
        json.dump(predicate, handle)

    return 0


if __name__ == "__main__":
    sys.exit(main())
