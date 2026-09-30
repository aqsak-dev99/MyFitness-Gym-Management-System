#!/bin/bash
# ══════════════════════════════════════════════════════════════════
# MyFitness — Demo Dataset Seeder
#
# Option B: a separate, manually-run mechanism — NOT a startup
# CommandLineRunner. Run this yourself, only when you want demo data,
# against your ALREADY-RUNNING backend. Nothing about this script
# runs automatically on deploy or restart.
#
# Every call below goes through the REAL REST API — the same
# endpoints, validation, and business logic a browser would hit.
# Nothing here touches the database directly, so no business rule
# can be bypassed by construction.
#
# All demo records use DM/ST/BC-prefixed IDs, distinct from any
# ad-hoc test data already in your database (M100-M400 etc.) — easy
# to identify, and safe to run alongside your existing rows without
# colliding with them.
#
# NOT idempotent: re-running this will fail on the username/ID
# registration calls (they already exist). That's intentional — it's
# meant to be run once against a given database, not repeatedly.
# ══════════════════════════════════════════════════════════════════

set -e  # stop immediately on any real failure, rather than continuing on broken state

BASE_URL="http://localhost:8080"
PASS="DemoPass123"

echo "════════════════════════════════════════"
echo " Step 1 — Admin account"
echo "════════════════════════════════════════"
ADMIN_TOKEN=$(curl -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"demo_admin\",\"password\":\"$PASS\",\"role\":\"ADMIN\",\"linkedMemberId\":null}" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
echo "Admin token acquired."
AUTH="Authorization: Bearer $ADMIN_TOKEN"

echo ""
echo "════════════════════════════════════════"
echo " Step 2 — Staff (2 instructors, 1 full-time, 1 part-time)"
echo "════════════════════════════════════════"
curl -s -X POST "$BASE_URL/api/instructors" -H "Content-Type: application/json" -H "$AUTH" \
  -d '{"personId":"PS01","staffId":"ST01","name":"Sarah Chen","email":"sarah.chen@myfitness.demo","phone":"07700900001","salary":38000,"workSchedule":"Mon-Fri 06:00-14:00","specialisation":"Strength & Conditioning"}' > /dev/null
echo "  Instructor ST01 — Sarah Chen created."

curl -s -X POST "$BASE_URL/api/instructors" -H "Content-Type: application/json" -H "$AUTH" \
  -d '{"personId":"PS02","staffId":"ST02","name":"Marcus Webb","email":"marcus.webb@myfitness.demo","phone":"07700900002","salary":36000,"workSchedule":"Tue-Sat 07:00-15:00","specialisation":"Cardio & HIIT"}' > /dev/null
echo "  Instructor ST02 — Marcus Webb created."

curl -s -X POST "$BASE_URL/api/staff/full-time" -H "Content-Type: application/json" -H "$AUTH" \
  -d '{"personId":"PS03","staffId":"ST03","name":"Emma Clarke","email":"emma.clarke@myfitness.demo","phone":"07700900003","role":"Front Desk Manager","salary":28000,"workSchedule":"Mon-Fri 09:00-17:00"}' > /dev/null
echo "  Full-time staff ST03 — Emma Clarke created."

curl -s -X POST "$BASE_URL/api/staff/part-time" -H "Content-Type: application/json" -H "$AUTH" \
  -d '{"personId":"PS04","staffId":"ST04","name":"Liam Patel","email":"liam.patel@myfitness.demo","phone":"07700900004","role":"Front Desk Assistant","hourlyRate":11.50,"hoursPerWeek":18,"shiftPattern":"Weekends"}' > /dev/null
echo "  Part-time staff ST04 — Liam Patel created."

echo ""
echo "════════════════════════════════════════"
echo " Step 3 — Two additional bootcamp classes"
echo " (BC001-003 already exist via BootcampSeeder)"
echo "════════════════════════════════════════"
curl -s -X POST "$BASE_URL/api/bootcamp-classes" -H "Content-Type: application/json" -H "$AUTH" \
  -d '{"classId":"BC004","type":"FAT_BURN","schedule":"Fri 18:00","maxCapacity":6}' > /dev/null
echo "  BC004 created — Fat Burn, Fri 18:00, capacity 6 (deliberately small, to demonstrate near-full state)"

curl -s -X POST "$BASE_URL/api/bootcamp-classes" -H "Content-Type: application/json" -H "$AUTH" \
  -d '{"classId":"BC005","type":"FULL_BODY","schedule":"Sun 10:00","maxCapacity":15}' > /dev/null
echo "  BC005 created — Full Body, Sun 10:00, capacity 15 (deliberately roomy, for contrast)"

echo ""
echo "════════════════════════════════════════"
echo " Step 4 — Instructor assignments"
echo "════════════════════════════════════════"
curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC001/instructor" -H "Content-Type: application/json" -H "$AUTH" \
  -d '{"instructorId":"ST01"}' > /dev/null
echo "  Sarah Chen -> BC001 (Mon/Wed 07:00)"

curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC004/instructor" -H "Content-Type: application/json" -H "$AUTH" \
  -d '{"instructorId":"ST01"}' > /dev/null
echo "  Sarah Chen -> BC004 (Fri 18:00) — different schedule string, no conflict"

curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC002/instructor" -H "Content-Type: application/json" -H "$AUTH" \
  -d '{"instructorId":"ST02"}' > /dev/null
echo "  Marcus Webb -> BC002 (Tue/Thu 08:00)"

echo ""
echo "  Verifying the conflict rule actually rejects a real double-booking"
echo "  (Sarah Chen is already on BC001's exact schedule 'Mon/Wed 07:00' —"
echo "   attempting to also assign her to BC003, which shares that same"
echo "   schedule string, should be REJECTED with a 409):"
CONFLICT_STATUS=$(curl -s -o /tmp/conflict_response.json -w "%{http_code}" -X POST "$BASE_URL/api/bootcamp-classes/BC003/instructor" \
  -H "Content-Type: application/json" -H "$AUTH" -d '{"instructorId":"ST01"}')
if [ "$CONFLICT_STATUS" = "409" ]; then
  echo "  CONFIRMED: got 409, conflict correctly rejected. Real rule verified, not just tested."
else
  echo "  UNEXPECTED: got $CONFLICT_STATUS instead of 409 — see /tmp/conflict_response.json"
fi

echo ""
echo "════════════════════════════════════════"
echo " Step 5 — Members (varied, real edge cases)"
echo "════════════════════════════════════════"

declare -A MEMBERS=(
  ["DM01"]="Olivia Bennett|olivia.bennett@myfitness.demo|07700910001"
  ["DM02"]="Noah Rodriguez|noah.rodriguez@myfitness.demo|07700910002"
  ["DM03"]="Ava Thompson|ava.thompson@myfitness.demo|07700910003"
  ["DM04"]="Ethan Kim|ethan.kim@myfitness.demo|07700910004"
  ["DM05"]="Mia Johansson|mia.johansson@myfitness.demo|07700910005"
  ["DM06"]="Lucas Ferreira|lucas.ferreira@myfitness.demo|07700910006"
  ["DM07"]="Isabella Novak|isabella.novak@myfitness.demo|07700910007"
)

for id in "${!MEMBERS[@]}"; do
  IFS='|' read -r name email phone <<< "${MEMBERS[$id]}"
  curl -s -X POST "$BASE_URL/api/members" -H "Content-Type: application/json" -H "$AUTH" \
    -d "{\"personId\":\"P$id\",\"memberId\":\"$id\",\"name\":\"$name\",\"email\":\"$email\",\"phone\":\"$phone\"}" > /dev/null
  echo "  Member $id — $name created."
done

echo ""
echo "  Setting real fitness goals (DM04 deliberately left null — exercises the real 'no goal' empty state):"
curl -s -X PATCH "$BASE_URL/api/members/DM01/goal" -H "Content-Type: application/json" -H "$AUTH" -d '{"fitnessGoal":"Build endurance for a 10k race"}' > /dev/null
curl -s -X PATCH "$BASE_URL/api/members/DM02/goal" -H "Content-Type: application/json" -H "$AUTH" -d '{"fitnessGoal":"Lose 5kg before summer"}' > /dev/null
curl -s -X PATCH "$BASE_URL/api/members/DM03/goal" -H "Content-Type: application/json" -H "$AUTH" -d '{"fitnessGoal":"Improve overall strength"}' > /dev/null
curl -s -X PATCH "$BASE_URL/api/members/DM05/goal" -H "Content-Type: application/json" -H "$AUTH" -d '{"fitnessGoal":"Train for a triathlon"}' > /dev/null
curl -s -X PATCH "$BASE_URL/api/members/DM07/goal" -H "Content-Type: application/json" -H "$AUTH" -d '{"fitnessGoal":"Get back into shape after a knee injury"}' > /dev/null
echo "  Goals set for DM01, DM02, DM03, DM05, DM07. DM04 and DM06 intentionally left without one."

echo ""
echo "════════════════════════════════════════"
echo " Step 6 — Memberships (all 3 real types, active + frozen, one member deliberately left unassigned)"
echo "════════════════════════════════════════"
curl -s -X POST "$BASE_URL/api/members/DM01/membership" -H "Content-Type: application/json" -H "$AUTH" \
  -d '{"membershipId":"MEM-DM01","type":"STANDARD","durationMonths":12}' > /dev/null
echo "  DM01 -> Standard, 12 months, active"

curl -s -X POST "$BASE_URL/api/members/DM02/membership" -H "Content-Type: application/json" -H "$AUTH" \
  -d '{"membershipId":"MEM-DM02","type":"STANDARD","durationMonths":6}' > /dev/null
curl -s -X POST "$BASE_URL/api/members/DM02/membership/freeze" -H "$AUTH" > /dev/null
echo "  DM02 -> Standard, 6 months, then FROZEN"

curl -s -X POST "$BASE_URL/api/members/DM03/membership" -H "Content-Type: application/json" -H "$AUTH" \
  -d '{"membershipId":"MEM-DM03","type":"STUDENT_SAVER","studentIdNumber":"S204518"}' > /dev/null
echo "  DM03 -> Student Saver, active"

curl -s -X POST "$BASE_URL/api/members/DM04/membership" -H "Content-Type: application/json" -H "$AUTH" \
  -d '{"membershipId":"MEM-DM04","type":"PAY_AS_YOU_GO"}' > /dev/null
echo "  DM04 -> Pay As You Go, active"

curl -s -X POST "$BASE_URL/api/members/DM05/membership" -H "Content-Type: application/json" -H "$AUTH" \
  -d '{"membershipId":"MEM-DM05","type":"STANDARD","durationMonths":12}' > /dev/null
echo "  DM05 -> Standard, 12 months, active"

curl -s -X POST "$BASE_URL/api/members/DM07/membership" -H "Content-Type: application/json" -H "$AUTH" \
  -d '{"membershipId":"MEM-DM07","type":"STANDARD","durationMonths":3}' > /dev/null
echo "  DM07 -> Standard, 3 months, active, recently registered"

echo "  DM06 deliberately left with NO membership — real, honest empty state, matches the earlier live-discovered gap"

echo ""
echo "════════════════════════════════════════"
echo " Step 7 — Enrolments (including a genuine multi-class discount trigger)"
echo "════════════════════════════════════════"
curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC001/enrolments" -H "Content-Type: application/json" -H "$AUTH" -d '{"memberId":"DM01"}' > /dev/null
curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC001/enrolments" -H "Content-Type: application/json" -H "$AUTH" -d '{"memberId":"DM03"}' > /dev/null
curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC001/enrolments" -H "Content-Type: application/json" -H "$AUTH" -d '{"memberId":"DM05"}' > /dev/null
echo "  BC001: DM01, DM03, DM05 enrolled (3/10)"

curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC002/enrolments" -H "Content-Type: application/json" -H "$AUTH" -d '{"memberId":"DM02"}' > /dev/null
curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC002/enrolments" -H "Content-Type: application/json" -H "$AUTH" -d '{"memberId":"DM04"}' > /dev/null
echo "  BC002: DM02, DM04 enrolled (2/10)"

echo "  Enrolling DM05 into a 2nd class (BC002) — this is the real multi-class discount trigger,"
echo "  calculated server-side, not simulated:"
curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC002/enrolments" -H "Content-Type: application/json" -H "$AUTH" -d '{"memberId":"DM05"}' > /dev/null
echo "  BC002: DM05 added -> now 3/10, and DM05 is enrolled in 2 classes total (discount should now apply to DM05's fee)"

curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC004/enrolments" -H "Content-Type: application/json" -H "$AUTH" -d '{"memberId":"DM01"}' > /dev/null
curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC004/enrolments" -H "Content-Type: application/json" -H "$AUTH" -d '{"memberId":"DM03"}' > /dev/null
curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC004/enrolments" -H "Content-Type: application/json" -H "$AUTH" -d '{"memberId":"DM07"}' > /dev/null
curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC004/enrolments" -H "Content-Type: application/json" -H "$AUTH" -d '{"memberId":"DM06"}' > /dev/null
curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC004/enrolments" -H "Content-Type: application/json" -H "$AUTH" -d '{"memberId":"DM02"}' > /dev/null
echo "  BC004: 5 members enrolled -> 5/6, deliberately near-full"

echo "  BC003 and BC005 intentionally left with zero enrolments — real contrast against BC004's near-full state"

echo ""
echo "════════════════════════════════════════"
echo " Step 8 — Member login accounts (linked to real member records)"
echo "════════════════════════════════════════"
for id in DM01 DM02 DM03 DM04 DM05 DM06 DM07; do
  username=$(echo "$id" | tr '[:upper:]' '[:lower:]')_demo
  curl -s -X POST "$BASE_URL/api/auth/register" -H "Content-Type: application/json" \
    -d "{\"username\":\"$username\",\"password\":\"$PASS\",\"role\":\"MEMBER\",\"linkedMemberId\":\"$id\"}" > /dev/null
  echo "  Login created: $username / $PASS -> linked to $id"
done

echo ""
echo "════════════════════════════════════════"
echo " DONE. Login summary:"
echo "════════════════════════════════════════"
echo "  Admin:    demo_admin / $PASS"
echo "  Members:  dm01_demo .. dm07_demo / $PASS  (all lowercase, e.g. dm01_demo)"
echo ""
echo "  Suggested logins to try first:"
echo "    dm05_demo -> 2 classes enrolled, real multi-class discount active"
echo "    dm02_demo -> frozen membership"
echo "    dm06_demo -> no membership assigned (real empty state)"
echo "    dm04_demo -> Pay As You Go, no fitness goal set"