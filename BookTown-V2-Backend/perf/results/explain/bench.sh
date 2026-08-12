#!/bin/bash
# bench.sh "<label>" "<sql>" [runs]
LABEL="$1"; Q="$2"; RUNS="${3:-15}"
/tmp/mq.sh booktown_perf -e "$Q" >/dev/null 2>&1
/tmp/mq.sh booktown_perf -e "$Q" >/dev/null 2>&1
TIMES=""
for i in $(seq 1 $RUNS); do
  T=$(/tmp/mq.sh booktown_perf -N -e "EXPLAIN ANALYZE $Q" 2>/dev/null | head -1 | grep -oP '\(actual time=[0-9.]+\.\.\K[0-9.]+' | head -1)
  TIMES="$TIMES $T"
done
echo "$LABEL"
echo "  raw(ms): $TIMES"
echo "$TIMES" | tr ' ' '\n' | grep -v '^$' | sort -g | awk -v n="$RUNS" '
  {a[NR]=$1; s+=$1}
  END{ printf "  n=%d  min=%.3f  p50=%.3f  p95=%.3f  max=%.3f  avg=%.3f\n", NR, a[1], a[int((NR+1)/2)], a[int(NR*0.95+0.5)], a[NR], s/NR }'
