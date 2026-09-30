#!/bin/bash
# ══════════════════════════════════════════════════════════════════
# MyFitness — Final Demo Data Reconciliation
#
# Brings the database to the intended demo state without duplicating
# or deleting anything. Every item is checked against real API state
# BEFORE acting — EXISTS/CREATED/FAILED is always the genuine result
# of that check, never assumed.
#
# Deliberately NOT using `set -e`: this script covers many independent
# items (staff, members, memberships, classes, enrolments), and one
# failing shouldn't prevent checking the rest. Every failure is still
# printed loudly with its real HTTP status and body — never silently
# swallowed — and counted in the final summary.
#
# Safe to run repeatedly. Nothing here deletes or modifies existing
# staff persistence architecture (StaffRepository, SqliteStaffRepository,
# StaffSeeder, TrainerService) — this script only calls the same real
# REST endpoints any client would.
# ══════════════════════════════════════════════════════════════════

BASE_URL="http://localhost:8080"
PASS="DemoPass123"

CREATED_COUNT=0
EXISTS_COUNT=0
FAILED_COUNT=0
FAILED_ITEMS=()

log_created() { echo "  [CREATED] $1"; CREATED_COUNT=$((CREATED_COUNT+1)); }
log_exists()  { echo "  [EXISTS]  $1"; EXISTS_COUNT=$((EXISTS_COUNT+1)); }
log_failed()  { echo "  [FAILED]  $1 -> HTTP $2: $3"; FAILED_COUNT=$((FAILED_COUNT+1)); FAILED_ITEMS+=("$1"); }
log_skip()    { echo "  [SKIP]    $1"; }

ADMIN_TOKEN=$(curl -s -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" \
  -d "{\"username\":\"demo_admin\",\"password\":\"$PASS\"}" | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])" 2>/dev/null)
if [ -z "$ADMIN_TOKEN" ]; then
  echo "FATAL: could not log in as demo_admin. Is the backend running? Aborting."
  exit 1
fi
AUTH="Authorization: Bearer $ADMIN_TOKEN"
echo "Admin token acquired."

# ══════════════════════════════════════════════════════
#  Section 1 — Staff (Marcus Webb, Emma Clarke, Liam Patel)
# ══════════════════════════════════════════════════════
echo ""
echo "════════════════════════════════════════"
echo " Section 1 — Staff"
echo "════════════════════════════════════════"

INSTRUCTORS=$(curl -s "$BASE_URL/api/instructors" -H "$AUTH")
FULLTIME=$(curl -s "$BASE_URL/api/staff/full-time" -H "$AUTH")
PARTTIME=$(curl -s "$BASE_URL/api/staff/part-time" -H "$AUTH")

has_id() { echo "$1" | python3 -c "import sys,json; print('yes' if any(x['staffId']=='$2' for x in json.load(sys.stdin)) else 'no')" 2>/dev/null; }

if [ "$(has_id "$INSTRUCTORS" ST01)" = "yes" ]; then
  log_exists "ST01 (Sarah Chen)"
else
  STATUS=$(curl -s -o /tmp/r.json -w "%{http_code}" -X POST "$BASE_URL/api/instructors" -H "Content-Type: application/json" -H "$AUTH" \
    -d '{"personId":"PS01","staffId":"ST01","name":"Sarah Chen","email":"sarah.chen@myfitness.demo","phone":"07700900001","salary":38000,"workSchedule":"Mon-Fri 06:00-14:00","specialisation":"Strength & Conditioning"}')
  [ "$STATUS" = "201" ] && log_created "ST01 (Sarah Chen)" || log_failed "ST01 (Sarah Chen)" "$STATUS" "$(cat /tmp/r.json)"
fi

if [ "$(has_id "$INSTRUCTORS" ST02)" = "yes" ]; then
  log_exists "ST02 (Marcus Webb)"
else
  STATUS=$(curl -s -o /tmp/r.json -w "%{http_code}" -X POST "$BASE_URL/api/instructors" -H "Content-Type: application/json" -H "$AUTH" \
    -d '{"personId":"PS02","staffId":"ST02","name":"Marcus Webb","email":"marcus.webb@myfitness.demo","phone":"07700900002","salary":36000,"workSchedule":"Tue-Sat 07:00-15:00","specialisation":"Cardio & HIIT"}')
  [ "$STATUS" = "201" ] && log_created "ST02 (Marcus Webb)" || log_failed "ST02 (Marcus Webb)" "$STATUS" "$(cat /tmp/r.json)"
fi

if [ "$(has_id "$FULLTIME" ST03)" = "yes" ]; then
  log_exists "ST03 (Emma Clarke)"
else
  STATUS=$(curl -s -o /tmp/r.json -w "%{http_code}" -X POST "$BASE_URL/api/staff/full-time" -H "Content-Type: application/json" -H "$AUTH" \
    -d '{"personId":"PS03","staffId":"ST03","name":"Emma Clarke","email":"emma.clarke@myfitness.demo","phone":"07700900003","role":"Front Desk Manager","salary":28000,"workSchedule":"Mon-Fri 09:00-17:00"}')
  [ "$STATUS" = "201" ] && log_created "ST03 (Emma Clarke)" || log_failed "ST03 (Emma Clarke)" "$STATUS" "$(cat /tmp/r.json)"
fi

if [ "$(has_id "$PARTTIME" ST04)" = "yes" ]; then
  log_exists "ST04 (Liam Patel)"
else
  STATUS=$(curl -s -o /tmp/r.json -w "%{http_code}" -X POST "$BASE_URL/api/staff/part-time" -H "Content-Type: application/json" -H "$AUTH" \
    -d '{"personId":"PS04","staffId":"ST04","name":"Liam Patel","email":"liam.patel@myfitness.demo","phone":"07700900004","role":"Front Desk Assistant","hourlyRate":11.50,"hoursPerWeek":18,"shiftPattern":"Weekends"}')
  [ "$STATUS" = "201" ] && log_created "ST04 (Liam Patel)" || log_failed "ST04 (Liam Patel)" "$STATUS" "$(cat /tmp/r.json)"
fi

# ══════════════════════════════════════════════════════
#  Section 2 — Members
# ══════════════════════════════════════════════════════
echo ""
echo "════════════════════════════════════════"
echo " Section 2 — Members"
echo "════════════════════════════════════════"

MEMBER_IDS=(DM01 DM02 DM03 DM04 DM05 DM06 DM07)
MEMBER_NAMES=("Olivia Bennett" "Noah Rodriguez" "Ava Thompson" "Ethan Kim" "Mia Johansson" "Lucas Ferreira" "Isabella Novak")
MEMBER_EMAILS=("olivia.bennett@myfitness.demo" "noah.rodriguez@myfitness.demo" "ava.thompson@myfitness.demo" "ethan.kim@myfitness.demo" "mia.johansson@myfitness.demo" "lucas.ferreira@myfitness.demo" "isabella.novak@myfitness.demo")
MEMBER_PHONES=("07700910001" "07700910002" "07700910003" "07700910004" "07700910005" "07700910006" "07700910007")

for i in "${!MEMBER_IDS[@]}"; do
  id="${MEMBER_IDS[$i]}"
  name="${MEMBER_NAMES[$i]}"
  email="${MEMBER_EMAILS[$i]}"
  phone="${MEMBER_PHONES[$i]}"
  CHECK_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/members/$id" -H "$AUTH")
  if [ "$CHECK_STATUS" = "200" ]; then
    log_exists "$id ($name)"
  else
    STATUS=$(curl -s -o /tmp/r.json -w "%{http_code}" -X POST "$BASE_URL/api/members" -H "Content-Type: application/json" -H "$AUTH" \
      -d "{\"personId\":\"P$id\",\"memberId\":\"$id\",\"name\":\"$name\",\"email\":\"$email\",\"phone\":\"$phone\"}")
    [ "$STATUS" = "201" ] && log_created "$id ($name)" || log_failed "$id ($name)" "$STATUS" "$(cat /tmp/r.json)"
  fi
done

# ══════════════════════════════════════════════════════
#  Section 3 — Fitness goals (DM04, DM06 intentionally blank)
# ══════════════════════════════════════════════════════
echo ""
echo "════════════════════════════════════════"
echo " Section 3 — Fitness goals"
echo "════════════════════════════════════════"

set_goal_if_missing() {
  local id="$1" goal="$2"
  local current
  current=$(curl -s "$BASE_URL/api/members/$id" -H "$AUTH" | python3 -c "import sys,json; g=json.load(sys.stdin).get('fitnessGoal'); print(g if g else '')" 2>/dev/null)
  if [ -n "$current" ]; then
    log_exists "$id goal ('$current')"
  else
    STATUS=$(curl -s -o /tmp/r.json -w "%{http_code}" -X PATCH "$BASE_URL/api/members/$id/goal" -H "Content-Type: application/json" -H "$AUTH" \
      -d "{\"fitnessGoal\":\"$goal\"}")
    [ "$STATUS" = "200" ] && log_created "$id goal" || log_failed "$id goal" "$STATUS" "$(cat /tmp/r.json)"
  fi
}

set_goal_if_missing DM01 "Build endurance for a 10k race"
set_goal_if_missing DM02 "Lose 5kg before summer"
set_goal_if_missing DM03 "Improve overall strength"
set_goal_if_missing DM05 "Train for a triathlon"
set_goal_if_missing DM07 "Get back into shape after a knee injury"
log_skip "DM04 goal — intentionally left blank (real empty-goal state)"
log_skip "DM06 goal — intentionally left blank (real empty-goal state)"

# ══════════════════════════════════════════════════════
#  Section 4 — Memberships (DM06 intentionally has none)
# ══════════════════════════════════════════════════════
echo ""
echo "════════════════════════════════════════"
echo " Section 4 — Memberships"
echo "════════════════════════════════════════"

create_membership_if_missing() {
  local id="$1" payload="$2" label="$3"
  local check_status
  check_status=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/members/$id/membership" -H "$AUTH")
  if [ "$check_status" = "200" ]; then
    log_exists "$label"
  else
    STATUS=$(curl -s -o /tmp/r.json -w "%{http_code}" -X POST "$BASE_URL/api/members/$id/membership" -H "Content-Type: application/json" -H "$AUTH" \
      -d "$payload")
    [ "$STATUS" = "201" ] && log_created "$label" || log_failed "$label" "$STATUS" "$(cat /tmp/r.json)"
  fi
}

create_membership_if_missing DM01 '{"membershipId":"MEM-DM01","type":"STANDARD","durationMonths":12}' "DM01 membership (Standard, active)"
create_membership_if_missing DM02 '{"membershipId":"MEM-DM02","type":"STANDARD","durationMonths":6}' "DM02 membership (Standard)"
create_membership_if_missing DM03 '{"membershipId":"MEM-DM03","type":"STUDENT_SAVER","studentIdNumber":"S204518"}' "DM03 membership (Student Saver)"
create_membership_if_missing DM04 '{"membershipId":"MEM-DM04","type":"PAY_AS_YOU_GO"}' "DM04 membership (Pay As You Go)"
create_membership_if_missing DM05 '{"membershipId":"MEM-DM05","type":"STANDARD","durationMonths":12}' "DM05 membership (Standard)"
create_membership_if_missing DM07 '{"membershipId":"MEM-DM07","type":"STANDARD","durationMonths":3}' "DM07 membership (Standard)"
log_skip "DM06 membership — intentionally left unassigned (real empty-membership state)"

# DM02 also needs to specifically be FROZEN, checked separately from existence
DM02_FROZEN=$(curl -s "$BASE_URL/api/members/DM02/membership" -H "$AUTH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('frozen', False))" 2>/dev/null)
if [ "$DM02_FROZEN" = "True" ]; then
  log_exists "DM02 membership frozen state"
else
  STATUS=$(curl -s -o /tmp/r.json -w "%{http_code}" -X POST "$BASE_URL/api/members/DM02/membership/freeze" -H "$AUTH")
  [ "$STATUS" = "200" ] && log_created "DM02 membership frozen" || log_failed "DM02 freeze" "$STATUS" "$(cat /tmp/r.json)"
fi

# ══════════════════════════════════════════════════════
#  Section 5 — Bootcamp classes
# ══════════════════════════════════════════════════════
echo ""
echo "════════════════════════════════════════"
echo " Section 5 — Bootcamp classes"
echo "════════════════════════════════════════"

create_class_if_missing() {
  local id="$1" payload="$2" label="$3"
  local check_status
  check_status=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/bootcamp-classes/$id" -H "$AUTH")
  if [ "$check_status" = "200" ]; then
    log_exists "$label"
  else
    STATUS=$(curl -s -o /tmp/r.json -w "%{http_code}" -X POST "$BASE_URL/api/bootcamp-classes" -H "Content-Type: application/json" -H "$AUTH" \
      -d "$payload")
    [ "$STATUS" = "201" ] && log_created "$label" || log_failed "$label" "$STATUS" "$(cat /tmp/r.json)"
  fi
}

# BC001-003 come from BootcampSeeder and are guaranteed present on every
# boot — but real payloads are still provided here (matching
# BootcampSeeder's exact values) so the create-fallback is correct too,
# not just assumed unreachable.
create_class_if_missing BC001 '{"classId":"BC001","type":"FAT_BURN","schedule":"Mon/Wed 07:00","maxCapacity":10}' "BC001 (seeded)"
create_class_if_missing BC002 '{"classId":"BC002","type":"FITNESS_AND_ENDURANCE","schedule":"Tue/Thu 08:00","maxCapacity":10}' "BC002 (seeded)"
create_class_if_missing BC003 '{"classId":"BC003","type":"FULL_BODY","schedule":"Sat 09:00","maxCapacity":10}' "BC003 (seeded)"
create_class_if_missing BC004 '{"classId":"BC004","type":"FAT_BURN","schedule":"Fri 18:00","maxCapacity":6}' "BC004 (near-full demo class)"
create_class_if_missing BC005 '{"classId":"BC005","type":"FULL_BODY","schedule":"Sun 10:00","maxCapacity":15}' "BC005 (roomy demo class)"
create_class_if_missing BC006 '{"classId":"BC006","type":"FITNESS_AND_ENDURANCE","schedule":"Mon/Wed 07:00","maxCapacity":8}' "BC006 (conflict-test class)"

# ══════════════════════════════════════════════════════
#  Section 6 — Instructor assignments
# ══════════════════════════════════════════════════════
echo ""
echo "════════════════════════════════════════"
echo " Section 6 — Instructor assignments"
echo "════════════════════════════════════════"

assign_if_missing() {
  local classId="$1" instId="$2"
  local current
  current=$(curl -s "$BASE_URL/api/bootcamp-classes/$classId" -H "$AUTH" | python3 -c "import sys,json; d=json.load(sys.stdin); i=d.get('instructor'); print(i['staffId'] if i else 'none')" 2>/dev/null)
  if [ "$current" = "$instId" ]; then
    log_exists "$classId -> $instId"
  else
    STATUS=$(curl -s -o /tmp/r.json -w "%{http_code}" -X POST "$BASE_URL/api/bootcamp-classes/$classId/instructor" -H "Content-Type: application/json" -H "$AUTH" \
      -d "{\"instructorId\":\"$instId\"}")
    if [ "$STATUS" = "200" ] || [ "$STATUS" = "201" ]; then
      log_created "$classId -> $instId (was '$current')"
    else
      log_failed "$classId -> $instId (was '$current')" "$STATUS" "$(cat /tmp/r.json)"
    fi
  fi
}

assign_if_missing BC001 ST01
assign_if_missing BC002 ST02
assign_if_missing BC004 ST01
assign_if_missing BC006 ST02

# ══════════════════════════════════════════════════════
#  Section 7 — Enrolments
# ══════════════════════════════════════════════════════
echo ""
echo "════════════════════════════════════════"
echo " Section 7 — Enrolments"
echo "════════════════════════════════════════"

enrol_if_missing() {
  local classId="$1" memberId="$2"
  local already
  already=$(curl -s "$BASE_URL/api/bootcamp-classes/$classId" -H "$AUTH" | python3 -c "import sys,json; d=json.load(sys.stdin); print('yes' if any(p.get('memberId')=='$memberId' for p in d.get('participants',[])) else 'no')" 2>/dev/null)
  if [ "$already" = "yes" ]; then
    log_exists "$classId <- $memberId"
  else
    STATUS=$(curl -s -o /tmp/r.json -w "%{http_code}" -X POST "$BASE_URL/api/bootcamp-classes/$classId/enrolments" -H "Content-Type: application/json" -H "$AUTH" \
      -d "{\"memberId\":\"$memberId\"}")
    [ "$STATUS" = "201" ] && log_created "$classId <- $memberId" || log_failed "$classId <- $memberId" "$STATUS" "$(cat /tmp/r.json)"
  fi
}

enrol_if_missing BC001 DM01
enrol_if_missing BC001 DM03
enrol_if_missing BC001 DM05
enrol_if_missing BC002 DM02
enrol_if_missing BC002 DM04
enrol_if_missing BC002 DM05
enrol_if_missing BC004 DM01
enrol_if_missing BC004 DM03
enrol_if_missing BC004 DM07
enrol_if_missing BC004 DM06
enrol_if_missing BC004 DM02

# ══════════════════════════════════════════════════════
#  Section 8 — Member login accounts
# ══════════════════════════════════════════════════════
echo ""
echo "════════════════════════════════════════"
echo " Section 8 — Member login accounts"
echo "════════════════════════════════════════"

for id in "${MEMBER_IDS[@]}"; do
  username=$(echo "$id" | tr '[:upper:]' '[:lower:]')_demo
  LOGIN_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" \
    -d "{\"username\":\"$username\",\"password\":\"$PASS\"}")
  if [ "$LOGIN_STATUS" = "200" ]; then
    log_exists "$username"
  else
    STATUS=$(curl -s -o /tmp/r.json -w "%{http_code}" -X POST "$BASE_URL/api/auth/register" -H "Content-Type: application/json" \
      -d "{\"username\":\"$username\",\"password\":\"$PASS\",\"role\":\"MEMBER\",\"linkedMemberId\":\"$id\"}")
    [ "$STATUS" = "200" ] && log_created "$username" || log_failed "$username" "$STATUS" "$(cat /tmp/r.json)"
  fi
done

# ══════════════════════════════════════════════════════
#  Final summary
# ══════════════════════════════════════════════════════
echo ""
echo "════════════════════════════════════════"
echo " SUMMARY"
echo "════════════════════════════════════════"
echo "  Created: $CREATED_COUNT"
echo "  Already existed: $EXISTS_COUNT"
echo "  Failed: $FAILED_COUNT"
if [ "$FAILED_COUNT" -gt 0 ]; then
  echo ""
  echo "  Failed items (needs investigation, not silently ignored):"
  for item in "${FAILED_ITEMS[@]}"; do
    echo "    - $item"
  done
fi

echo ""
echo "  Real verification — final counts read back from the database:"
curl -s "$BASE_URL/api/members" -H "$AUTH" | python3 -c "import sys,json; print('    Members:', len([m for m in json.load(sys.stdin) if m['memberId'].startswith('DM')]), '(expected 7)')"
curl -s "$BASE_URL/api/instructors" -H "$AUTH" | python3 -c "import sys,json; print('    Instructors:', len(json.load(sys.stdin)), '(expected 5: INS001-003, ST01, ST02)')"
curl -s "$BASE_URL/api/staff/full-time" -H "$AUTH" | python3 -c "import sys,json; print('    Full-time staff:', len(json.load(sys.stdin)), '(expected 6: FT001-005, ST03)')"
curl -s "$BASE_URL/api/staff/part-time" -H "$AUTH" | python3 -c "import sys,json; print('    Part-time staff:', len(json.load(sys.stdin)), '(expected 5: PT001-004, ST04)')"
curl -s "$BASE_URL/api/bootcamp-classes" -H "$AUTH" | python3 -c "import sys,json; print('    Bootcamp classes:', len(json.load(sys.stdin)), '(expected 6: BC001-006)')"

echo ""
echo "Reconciliation complete."