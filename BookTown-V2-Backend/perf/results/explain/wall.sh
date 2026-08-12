#!/bin/bash
# wall.sh "<label>" "<sql>" <runs>  -> 실제 wall-clock (클라이언트 왕복 포함하지 않도록 단일 세션 내 반복)
LABEL="$1"; Q="$2"; RUNS="${3:-30}"
SQLFILE=$(mktemp)
{ echo "USE booktown_perf;"; echo "SET profiling=0;"; for i in $(seq 1 3); do echo "$Q;"; done; } > $SQLFILE
/tmp/mq.sh < $SQLFILE > /dev/null 2>&1   # warm
{ echo "USE booktown_perf;"; for i in $(seq 1 $RUNS); do echo "$Q;"; done; } > $SQLFILE
S=$(date +%s%N)
/tmp/mq.sh < $SQLFILE > /dev/null 2>&1
E=$(date +%s%N)
rm -f $SQLFILE
awk -v s=$S -v e=$E -v n=$RUNS -v l="$LABEL" 'BEGIN{printf "%-52s %8.2f ms/query  (%d회 총 %.0f ms)\n", l, (e-s)/1e6/n, n, (e-s)/1e6}'
