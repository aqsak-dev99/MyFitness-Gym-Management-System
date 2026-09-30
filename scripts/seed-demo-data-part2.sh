#!/bin/bash
# ══════════════════════════════════════════════════════════════════
# Continuation of seed-demo-data.sh, starting from Step 5.
# Steps 1-4 already succeeded in your database — do NOT re-run the
# original script, it will fail on duplicate admin/staff/class IDs.
#
# Two fixes from the original:
#  1. Members/goals/memberships/enrolments rewritten without
#     `declare -A` (bash 3.2, macOS's default, doesn't support it).
#  2. A real conflict test: BC006 shares BC001's exact schedule
#     string, so assigning Sarah Chen there genuinely should be
#     rejected — BC003 (my original test target) never actually
#     conflicted with anything.
# ══════════════════════════════════════════════════════════════════

set -e

BASE_URL="http://localhost:8080"
PASS="DemoPass123"

echo "Logging in as demo_admin (already created)..."
ADMIN_TOKEN=$(curl -s -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" \
  -d "{\"username\":\"demo_admin\",\"password\":\"$PASS\"}" | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
AUTH="Authorization: Bearer $ADMIN_TOKEN"
echo "Token acquired."

echo ""
echo "════════════════════════════════════════"
echo " Step 4b — A genuine conflict test (correcting my earlier mistake)"
echo "════════════════════════════════════════"
curl -s -X POST "$BASE_URL/api/bootcamp-classes" -H "Content-Type: application/json" -H "$AUTH" \
  -d '{"classId":"BC006","type":"FITNESS_AND_ENDURANCE","schedule":"Mon/Wed 07:00","maxCapacity":8}' > /dev/null
echo "  BC006 created — schedule 'Mon/Wed 07:00', deliberately identical to BC001's"

echo "  Attempting to assign Sarah Chen (already on BC001, same exact schedule) to BC006:"
CONFLICT_STATUS=$(curl -s -o /tmp/conflict_response.json -w "%{http_code}" -X POST "$BASE_URL/api/bootcamp-classes/BC006/instructor" \
  -H "Content-Type: application/json" -H "$AUTH" -d '{"instructorId":"ST01"}')
if [ "$CONFLICT_STATUS" = "409" ]; then
  echo "  CONFIRMED: got 409. The conflict rule genuinely works — verified, not assumed."
else
  echo "  STILL UNEXPECTED: got $CONFLICT_STATUS — see /tmp/conflict_response.json. This would be worth investigating for real."
fi

echo "  Assigning Marcus Webb to BC006 instead (his existing schedule is Tue/Thu 08:00 - no conflict):"
curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC006/instructor" -H "Content-Type: application/json" -H "$AUTH" \
  -d '{"instructorId":"ST02"}' > /dev/null
echo "  Marcus Webb -> BC006 succeeded (genuinely different instructor, so no conflict)"

echo ""
echo "════════════════════════════════════════"
echo " Step 5 — Members (bash 3.2 compatible — plain arrays, no declare -A)"
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
  curl -s -X POST "$BASE_URL/api/members" -H "Content-Type: application/json" -H "$AUTH" \
    -d "{\"personId\":\"P$id\",\"memberId\":\"$id\",\"name\":\"$name\",\"email\":\"$email\",\"phone\":\"$phone\"}" > /dev/null
  echo "  Member $id — $name created."
done

echo ""
echo "  Setting real fitness goals (DM04 and DM06 deliberately left null):"
curl -s -X PATCH "$BASE_URL/api/members/DM01/goal" -H "Content-Type: application/json" -H "$AUTH" -d '{"fitnessGoal":"Build endurance for a 10k race"}' > /dev/null
curl -s -X PATCH "$BASE_URL/api/members/DM02/goal" -H "Content-Type: application/json" -H "$AUTH" -d '{"fitnessGoal":"Lose 5kg before summer"}' > /dev/null
curl -s -X PATCH "$BASE_URL/api/members/DM03/goal" -H "Content-Type: application/json" -H "$AUTH" -d '{"fitnessGoal":"Improve overall strength"}' > /dev/null
curl -s -X PATCH "$BASE_URL/api/members/DM05/goal" -H "Content-Type: application/json" -H "$AUTH" -d '{"fitnessGoal":"Train for a triathlon"}' > /dev/null
curl -s -X PATCH "$BASE_URL/api/members/DM07/goal" -H "Content-Type: application/json" -H "$AUTH" -d '{"fitnessGoal":"Get back into shape after a knee injury"}' > /dev/null
echo "  Done."

echo ""
echo "════════════════════════════════════════"
echo " Step 6 — Memberships"
echo "════════════════════════════════════════"
curl -s -X POST "$BASE_URL/api/members/DM01/membership" -H "Content-Type: application/json" -H "$AUTH" -d '{"membershipId":"MEM-DM01","type":"STANDARD","durationMonths":12}' > /dev/null
echo "  DM01 -> Standard, 12 months, active"

curl -s -X POST "$BASE_URL/api/members/DM02/membership" -H "Content-Type: application/json" -H "$AUTH" -d '{"membershipId":"MEM-DM02","type":"STANDARD","durationMonths":6}' > /dev/null
curl -s -X POST "$BASE_URL/api/members/DM02/membership/freeze" -H "$AUTH" > /dev/null
echo "  DM02 -> Standard, 6 months, then FROZEN"

curl -s -X POST "$BASE_URL/api/members/DM03/membership" -H "Content-Type: application/json" -H "$AUTH" -d '{"membershipId":"MEM-DM03","type":"STUDENT_SAVER","studentIdNumber":"S204518"}' > /dev/null
echo "  DM03 -> Student Saver, active"

curl -s -X POST "$BASE_URL/api/members/DM04/membership" -H "Content-Type: application/json" -H "$AUTH" -d '{"membershipId":"MEM-DM04","type":"PAY_AS_YOU_GO"}' > /dev/null
echo "  DM04 -> Pay As You Go, active"

curl -s -X POST "$BASE_URL/api/members/DM05/membership" -H "Content-Type: application/json" -H "$AUTH" -d '{"membershipId":"MEM-DM05","type":"STANDARD","durationMonths":12}' > /dev/null
echo "  DM05 -> Standard, 12 months, active"

curl -s -X POST "$BASE_URL/api/members/DM07/membership" -H "Content-Type: application/json" -H "$AUTH" -d '{"membershipId":"MEM-DM07","type":"STANDARD","durationMonths":3}' > /dev/null
echo "  DM07 -> Standard, 3 months, active"

echo "  DM06 deliberately left with NO membership."

echo ""
echo "════════════════════════════════════════"
echo " Step 7 — Enrolments"
echo "════════════════════════════════════════"
curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC001/enrolments" -H "Content-Type: application/json" -H "$AUTH" -d '{"memberId":"DM01"}' > /dev/null
curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC001/enrolments" -H "Content-Type: application/json" -H "$AUTH" -d '{"memberId":"DM03"}' > /dev/null
curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC001/enrolments" -H "Content-Type: application/json" -H "$AUTH" -d '{"memberId":"DM05"}' > /dev/null
echo "  BC001: DM01, DM03, DM05 (3/10)"

curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC002/enrolments" -H "Content-Type: application/json" -H "$AUTH" -d '{"memberId":"DM02"}' > /dev/null
curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC002/enrolments" -H "Content-Type: application/json" -H "$AUTH" -d '{"memberId":"DM04"}' > /dev/null
echo "  BC002: DM02, DM04 (2/10)"

echo "  Enrolling DM05 into a 2nd class — real multi-class discount trigger:"
curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC002/enrolments" -H "Content-Type: application/json" -H "$AUTH" -d '{"memberId":"DM05"}' > /dev/null
echo "  BC002: DM05 added -> 3/10. DM05 now in 2 classes total."

curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC004/enrolments" -H "Content-Type: application/json" -H "$AUTH" -d '{"memberId":"DM01"}' > /dev/null
curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC004/enrolments" -H "Content-Type: application/json" -H "$AUTH" -d '{"memberId":"DM03"}' > /dev/null
curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC004/enrolments" -H "Content-Type: application/json" -H "$AUTH" -d '{"memberId":"DM07"}' > /dev/null
curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC004/enrolments" -H "Content-Type: application/json" -H "$AUTH" -d '{"memberId":"DM06"}' > /dev/null
curl -s -X POST "$BASE_URL/api/bootcamp-classes/BC004/enrolments" -H "Content-Type: application/json" -H "$AUTH" -d '{"memberId":"DM02"}' > /dev/null
echo "  BC004: 5 members -> 5/6, deliberately near-full"

echo "  BC003 and BC005 intentionally left with zero enrolments."

echo ""
echo "════════════════════════════════════════"
echo " Step 8 — Member login accounts"
echo "════════════════════════════════════════"
for id in "${MEMBER_IDS[@]}"; do
  username=$(echo "$id" | tr '[:upper:]' '[:lower:]')_demo
  curl -s -X POST "$BASE_URL/api/auth/register" -H "Content-Type: application/json" \
    -d "{\"username\":\"$username\",\"password\":\"$PASS\",\"role\":\"MEMBER\",\"linkedMemberId\":\"$id\"}" > /dev/null
  echo "  Login: $username / $PASS -> $id"
done

echo ""
echo "════════════════════════════════════════"
echo " DONE."
echo "════════════════════════════════════════"
echo "  Admin:  demo_admin / $PASS"
echo "  Members: dm01_demo .. dm07_demo / $PASS"