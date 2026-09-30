# Ruby SDK compatibility

Ruby 3.4 with locked official Google Cloud clients and Minitest. This initial
suite covers GCS byte transfers, object inventory and pagination with synthetic
credentials and fixture data.

Build with `docker build -t floci-sdk-ruby .`. Run on the same Docker network as
the emulator, set `FLOCI_GCP_ENDPOINT` to its HTTP origin, and mount a writable
directory at `/results` for JUnit output. The entrypoint runs `test/all.rb`.

The compatibility workflow runs this suite; the cache-warming workflow watches
Gemfile and Gemfile.lock and uses the matching runner architecture and cache
scope. PR jobs remain read-only cache consumers. The CI emulator keeps TLS enabled.

Compute, Monitoring, advanced storage transfers, multipart and restart contracts
are proposed separately after their emulator prerequisites land.
