#!/usr/bin/env bash
# Collects Prometheus + log evidence for the just-finished k6 run.
# Usage: collect_metrics.sh <LABEL> <WINDOW_SECONDS> [START_EPOCH]
# START_EPOCH should be the wall-clock time right before the k6 run started (not NOW-WINDOW) —
# otherwise, if WINDOW reaches back past a backend container restart, stale gauge values
# (e.g. hikaricp_connections_acquire_seconds_max, which only resets on JVM restart / time-window
# rotation) from the PREVIOUS container/run can leak into this run's "max" calculation.
set -uo pipefail
LABEL="$1"
WINDOW="${2:-210}"
NOW=$(date +%s)
START="${3:-$((NOW-WINDOW))}"
OUT="perf/results/metrics-${LABEL}.txt"

q_range_max() {
  curl -sG "http://localhost:9090/api/v1/query_range" \
    --data-urlencode "query=$1" \
    --data-urlencode "start=$START" --data-urlencode "end=$NOW" --data-urlencode "step=5" \
    | grep -oE '\[[0-9]+(\.[0-9]+)?,"[-0-9.eE+]+"\]' | sed -E 's/\[[0-9.]+,"([-0-9.eE+]+)"\]/\1/' \
    | sort -g | tail -1
}
q_range_min() {
  curl -sG "http://localhost:9090/api/v1/query_range" \
    --data-urlencode "query=$1" \
    --data-urlencode "start=$START" --data-urlencode "end=$NOW" --data-urlencode "step=5" \
    | grep -oE '\[[0-9]+(\.[0-9]+)?,"[-0-9.eE+]+"\]' | sed -E 's/\[[0-9.]+,"([-0-9.eE+]+)"\]/\1/' \
    | sort -g | head -1
}
q_range_avg() {
  curl -sG "http://localhost:9090/api/v1/query_range" \
    --data-urlencode "query=$1" \
    --data-urlencode "start=$START" --data-urlencode "end=$NOW" --data-urlencode "step=5" \
    | grep -oE '\[[0-9]+(\.[0-9]+)?,"[-0-9.eE+]+"\]' | sed -E 's/\[[0-9.]+,"([-0-9.eE+]+)"\]/\1/' \
    | awk '{s+=$1; n++} END {if(n>0) print s/n; else print "n/a"}'
}
q_instant() {
  curl -sG "http://localhost:9090/api/v1/query" --data-urlencode "query=$1" \
    | grep -oE '"value":\[[0-9.]+,"[-0-9.eE+]+"\]' | sed -E 's/.*,"([-0-9.eE+]+)"\]/\1/'
}

{
  echo "label=${LABEL}"
  echo "window_start=${START} window_end=${NOW}"
  echo "pending_max=$(q_range_max 'hikaricp_connections_pending{pool="HikariPool-1"}')"
  echo "active_max=$(q_range_max 'hikaricp_connections_active{pool="HikariPool-1"}')"
  echo "active_avg=$(q_range_avg 'hikaricp_connections_active{pool="HikariPool-1"}')"
  echo "idle_min=$(q_range_min 'hikaricp_connections_idle{pool="HikariPool-1"}')"
  echo "acquire_seconds_max=$(q_range_max 'hikaricp_connections_acquire_seconds_max{pool="HikariPool-1"}')"
  echo "# note: no _bucket histograms exposed (percentiles-histogram not enabled) -> no acquire/read p95 via Prometheus; read p95 comes from k6 client-side output instead"
  echo "cpu_avg=$(q_range_avg 'system_cpu_usage')"
  echo "cpu_max=$(q_range_max 'system_cpu_usage')"
  echo "heap_max=$(q_range_max 'sum(jvm_memory_used_bytes{area="heap"})')"
  echo "executor_active_max=$(q_range_max 'executor_active_threads{name="summaryProcessingExecutor"}')"
  echo "executor_queue_max=$(q_range_max 'executor_queued_tasks{name="summaryProcessingExecutor"}')"
} | tee "$OUT"

echo "--- hikari exhaustion log lines (last ${WINDOW}s) ---"
docker logs booktown-backend-backend-1 --since "${WINDOW}s" 2>&1 \
  | grep -icE "Connection is not available|SQLTransientConnectionException" | tee -a "$OUT"
