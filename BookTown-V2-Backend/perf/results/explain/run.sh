#!/bin/bash
# $1 = label, $2 = sql
Q="$2"
echo "############ $1 ############"
echo "--- SQL ---"; echo "$Q"
echo "--- warm-up x2 ---"
/tmp/mq.sh booktown_perf -e "$Q" > /dev/null 2>&1
/tmp/mq.sh booktown_perf -e "$Q" > /dev/null 2>&1
echo "--- EXPLAIN ---"
/tmp/mq.sh booktown_perf -e "EXPLAIN $Q" 2>&1
echo "--- EXPLAIN ANALYZE (3회) ---"
for i in 1 2 3; do /tmp/mq.sh booktown_perf -e "EXPLAIN ANALYZE $Q" 2>&1; echo "   ....."; done
echo ""
