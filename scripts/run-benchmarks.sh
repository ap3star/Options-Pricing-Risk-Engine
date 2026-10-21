#!/usr/bin/env bash
# Builds test classes and runs the JMH benchmarks. Extra arguments are passed to JMH,
# e.g. scripts/run-benchmarks.sh -p parallelism=1,4 monteCarlo
set -euo pipefail
cd "$(dirname "$0")/.."

mvn -q test-compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -Dmdep.includeScope=test
java -cp "target/test-classes:target/classes:$(cat target/classpath.txt)" org.openjdk.jmh.Main \
    "${@:-com.optionsengine.bench.PricingBenchmark}"
