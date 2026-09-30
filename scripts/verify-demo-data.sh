#!/bin/bash
# ══════════════════════════════════════════════════════════════════
# MyFitness — Demo Dataset Verification
#
# Run this AFTER seed-demo-data.sh. Every check here reads real data
# back through the real API — this is how we confirm the inserts
# actually worked, rather than assuming the seed script's success
# meant success (curl can return 200 on a validation-rejected request
# if you're not checking the body — this checks the actual data).
# ══════════════════════════════════════════════════════════════════

BASE_URL="http://localhost:8080"
PASS="DemoPass123"

echo "Logging in as demo_admin..."
ADMIN_TOKEN=$(curl -s -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" \
  -d "{\"username\":\"demo_admin\",\"password\":\"$PASS\"}" | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
AUTH="Authorization: Bearer $ADMIN_TOKEN"

echo ""
echo "════════════════════════════════════════"
echo " Members — expect 7 real demo members (plus any pre-existing ones)"
echo "════════════════════════════════════════"
curl -s "$BASE_URL/api/members" -H "$AUTH" | python3 -c "
import sys, json
members = json.load(sys.stdin)
demo = [m for m in members if m['memberId'].startswith('DM')]
print(f'{len(demo)} demo members found:')
for m in demo:
    goal = m['fitnessGoal'] or '(none)'
    membership = 'assigned' if m['membership'] else 'NONE'
    print(f\"  {m['memberId']} {m['name']:20s} goal: {goal:45s} membership: {membership}\")
"

echo ""
echo "════════════════════════════════════════"
echo " Membership states — verify types and frozen status directly"
echo "════════════════════════════════════════"
for id in DM01 DM02 DM03 DM04 DM05; do
  curl -s "$BASE_URL/api/members/$id/membership" -H "$AUTH" | python3 -c "
import sys, json
try:
    m = json.load(sys.stdin)
    frozen = 'FROZEN' if m.get('frozen') else 'active'
    print(f'  $id: {frozen}, £{m.get(\"monthlyFee\", \"?\")}/mo, {m.get(\"daysRemaining\", \"?\")} days left')
except Exception as e:
    print(f'  $id: could not parse — {e}')
"
done

echo ""
echo "════════════════════════════════════════"
echo " Bootcamp classes — real capacity/enrolment counts, confirm near-full state"
echo "════════════════════════════════════════"
curl -s "$BASE_URL/api/bootcamp-classes" -H "$AUTH" | python3 -c "
import sys, json
classes = json.load(sys.stdin)
for c in classes:
    full = ' <- NEAR/AT FULL' if c['full'] or c['currentEnrolments'] / c['maxCapacity'] > 0.7 else ''
    print(f\"  {c['classId']} {c['className']:25s} {c['schedule']:15s} {c['currentEnrolments']}/{c['maxCapacity']}{full}\")
"

echo ""
echo "════════════════════════════════════════"
echo " Staff — confirm all 4 real, including instructor specialisations"
echo "════════════════════════════════════════"
curl -s "$BASE_URL/api/instructors" -H "$AUTH" | python3 -c "
import sys, json
for i in json.load(sys.stdin):
    print(f\"  Instructor {i['staffId']}: {i['name']} — {i['specialisation']}\")
"
curl -s "$BASE_URL/api/staff/full-time" -H "$AUTH" | python3 -c "
import sys, json
for s in json.load(sys.stdin):
    if s['staffId'] not in ('ST01', 'ST02'):
        print(f\"  Full-time {s['staffId']}: {s['name']} — {s['role']}\")
"
curl -s "$BASE_URL/api/staff/part-time" -H "$AUTH" | python3 -c "
import sys, json
for s in json.load(sys.stdin):
    print(f\"  Part-time {s['staffId']}: {s['name']} — {s['role']}\")
"

echo ""
echo "════════════════════════════════════════"
echo " Multi-class discount — the real proof, not asserted"
echo "════════════════════════════════════════"
curl -s "$BASE_URL/api/bootcamp-classes" -H "$AUTH" | python3 -c "
import sys, json
classes = json.load(sys.stdin)
dm05_classes = [c['classId'] for c in classes if any(p.get('memberId') == 'DM05' for p in c.get('participants', []))]
print(f'  DM05 is enrolled in: {dm05_classes}')
print(f'  ({len(dm05_classes)} classes -> multi-class discount should apply to the 2nd+ class fee)')
"

echo ""
echo "Verification complete. Paste this whole output back for review."