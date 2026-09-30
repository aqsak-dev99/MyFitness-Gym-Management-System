#!/bin/bash
# ══════════════════════════════════════════════════════════════════
# Idempotent repair pass — checks real state before acting, unlike
# the original script's blind `> /dev/null` calls. Safe to run
# regardless of what silently succeeded or failed earlier tonight.
# Every action prints its REAL status, not an assumed one.
# ══════════════════════════════════════════════════════════════════

BASE_URL="http://localhost:8080"
PASS="DemoPass123"

ADMIN_TOKEN=$(curl -s -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" \
  -d "{\"username\":\"demo_admin\",\"password\":\"$PASS\"}" | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
AUTH="Authorization: Bearer $ADMIN_TOKEN"
echo "Admin token acquired."

echo ""
echo "=== Checking which staff genuinely exist ==="
INSTRUCTORS=$(curl -s "$BASE_URL/api/instructors" -H "$AUTH")
FULLTIME=$(curl -s "$BASE_URL/api/staff/full-time" -H "$AUTH")
PARTTIME=$(curl -s "$BASE_URL/api/staff/part-time" -H "$AUTH")

echo "$INSTRUCTORS" | python3 -c "import sys,json; print('Instructors:', [x['staffId'] for x in json.load(sys.stdin)])"
echo "$FULLTIME" | python3 -c "import sys,json; print('Full-time:', [x['staffId'] for x in json.load(sys.stdin)])"
echo "$PARTTIME" | python3 -c "import sys,json; print('Part-time:', [x['staffId'] for x in json.load(sys.stdin)])"

has_id() { echo "$1" | python3 -c "import sys,json; print('yes' if any(x['staffId']=='$2' for x in json.load(sys.stdin)) else 'no')"; }

echo ""
echo "=== Creating anything genuinely missing (real status checked, not assumed) ==="

if [ "$(has_id "$INSTRUCTORS" ST02)" = "no" ]; then
  STATUS=$(curl -s -o /tmp/r.json -w "%{http_code}" -X POST "$BASE_URL/api/instructors" -H "Content-Type: application/json" -H "$AUTH" \
    -d '{"personId":"PS02","staffId":"ST02","name":"Marcus Webb","email":"marcus.webb@myfitness.demo","phone":"07700900002","salary":36000,"workSchedule":"Tue-Sat 07:00-15:00","specialisation":"Cardio & HIIT"}')
  echo "  ST02 (Marcus Webb): create attempt -> HTTP $STATUS $([ "$STATUS" = "201" ] && echo OK || cat /tmp/r.json)"
else
  echo "  ST02 (Marcus Webb): already exists, skipped."
fi

if [ "$(has_id "$FULLTIME" ST03)" = "no" ]; then
  STATUS=$(curl -s -o /tmp/r.json -w "%{http_code}" -X POST "$BASE_URL/api/staff/full-time" -H "Content-Type: application/json" -H "$AUTH" \
    -d '{"personId":"PS03","staffId":"ST03","name":"Emma Clarke","email":"emma.clarke@myfitness.demo","phone":"07700900003","role":"Front Desk Manager","salary":28000,"workSchedule":"Mon-Fri 09:00-17:00"}')
  echo "  ST03 (Emma Clarke): create attempt -> HTTP $STATUS $([ "$STATUS" = "201" ] && echo OK || cat /tmp/r.json)"
else
  echo "  ST03 (Emma Clarke): already exists, skipped."
fi

if [ "$(has_id "$PARTTIME" ST04)" = "no" ]; then
  STATUS=$(curl -s -o /tmp/r.json -w "%{http_code}" -X POST "$BASE_URL/api/staff/part-time" -H "Content-Type: application/json" -H "$AUTH" \
    -d '{"personId":"PS04","staffId":"ST04","name":"Liam Patel","email":"liam.patel@myfitness.demo","phone":"07700900004","role":"Front Desk Assistant","hourlyRate":11.50,"hoursPerWeek":18,"shiftPattern":"Weekends"}')
  echo "  ST04 (Liam Patel): create attempt -> HTTP $STATUS $([ "$STATUS" = "201" ] && echo OK || cat /tmp/r.json)"
else
  echo "  ST04 (Liam Patel): already exists, skipped."
fi

echo ""
echo "=== Checking + fixing instructor assignments on each class ==="
for pair in "BC001:ST01" "BC002:ST02" "BC004:ST01"; do
  classId="${pair%%:*}"
  instId="${pair##*:}"
  CURRENT=$(curl -s "$BASE_URL/api/bootcamp-classes/$classId" -H "$AUTH" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['instructor']['staffId'] if d.get('instructor') else 'none')")
  if [ "$CURRENT" = "$instId" ]; then
    echo "  $classId: already correctly assigned to $instId."
  else
    STATUS=$(curl -s -o /tmp/r.json -w "%{http_code}" -X POST "$BASE_URL/api/bootcamp-classes/$classId/instructor" -H "Content-Type: application/json" -H "$AUTH" \
      -d "{\"instructorId\":\"$instId\"}")
    echo "  $classId: was '$CURRENT', assigning $instId -> HTTP $STATUS $([ "$STATUS" = "200" -o "$STATUS" = "201" ] && echo OK || cat /tmp/r.json)"
  fi
done

echo ""
echo "=== Real conflict test, finally with status actually verified end to end ==="
CURRENT_BC006=$(curl -s "$BASE_URL/api/bootcamp-classes/BC006" -H "$AUTH" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['instructor']['staffId'] if d.get('instructor') else 'none')")
echo "  BC006 currently assigned to: $CURRENT_BC006"
if [ "$CURRENT_BC006" = "none" ]; then
  echo "  Attempting Sarah Chen (ST01, already on BC001's identical schedule) -> BC006, expecting 409:"
  STATUS=$(curl -s -o /tmp/r.json -w "%{http_code}" -X POST "$BASE_URL/api/bootcamp-classes/BC006/instructor" -H "Content-Type: application/json" -H "$AUTH" -d '{"instructorId":"ST01"}')
  if [ "$STATUS" = "409" ]; then
    echo "  CONFIRMED: 409. Conflict rule genuinely verified this time."
  else
    echo "  Got $STATUS instead: $(cat /tmp/r.json)"
  fi
fi

echo ""
echo "Done. Re-run verify-demo-data.sh now for the full picture."