#!/bin/bash
# ══════════════════════════════════════════════════════════════════
# Revenue demo data — adds 6 new members with genuinely varied
# membership statuses, so the Revenue page's Payment Status Breakdown
# demonstrates all four real states (PAID/DUE_SOON/OVERDUE/N-A).
#
# Every status shown is genuinely computed by getPaymentStatus() from
# a real, persisted startDate — nothing is hardcoded or faked. The
# backdating uses real dates (via macOS's `date -v`), computed
# relative to today, not hardcoded calendar dates that would go stale.
#
# Does NOT touch DM01-DM07 or the six memberships affected by the
# earlier startDate-restoration bug — entirely new members, new IDs.
# Safe to run once; re-running would fail on duplicate member IDs
# (by design — this isn't meant to be idempotent like
# reconcile-demo-data.sh, since these are meant to be created exactly
# once).
# ══════════════════════════════════════════════════════════════════

BASE_URL="http://localhost:8080"
PASS="DemoPass123"

ADMIN_TOKEN=$(curl -s -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" \
  -d "{\"username\":\"demo_admin\",\"password\":\"$PASS\"}" | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
AUTH="Authorization: Bearer $ADMIN_TOKEN"
echo "Admin token acquired."

# ──────────────────────────────────────────────────────────
# Real dates, computed relative to today — never hardcoded
# ──────────────────────────────────────────────────────────
OVERDUE_START_1=$(date -v-65d +%Y-%m-%d)   # ~2 months + a few days ago
OVERDUE_START_2=$(date -v-58d +%Y-%m-%d)
DUESOON_START_1=$(date -v-31d +%Y-%m-%d)   # lands ~1 day from now
DUESOON_START_2=$(date -v-30d +%Y-%m-%d)   # lands ~2 days from now

echo ""
echo "Computed backdates: Overdue starts=[$OVERDUE_START_1, $OVERDUE_START_2], Due-soon starts=[$DUESOON_START_1, $DUESOON_START_2]"

create_member_and_membership() {
  local id="$1" name="$2" email="$3" phone="$4" membershipType="$5" durationOrStudentId="$6" backdateTo="$7" label="$8"

  STATUS=$(curl -s -o /tmp/r.json -w "%{http_code}" -X POST "$BASE_URL/api/members" -H "Content-Type: application/json" -H "$AUTH" \
    -d "{\"personId\":\"P$id\",\"memberId\":\"$id\",\"name\":\"$name\",\"email\":\"$email\",\"phone\":\"$phone\"}")
  if [ "$STATUS" != "201" ]; then echo "  [FAILED] $id member creation -> HTTP $STATUS: $(cat /tmp/r.json)"; return; fi
  echo "  [CREATED] $id ($name)"

  if [ "$membershipType" = "PAY_AS_YOU_GO" ]; then
    PAYLOAD="{\"membershipId\":\"MEM-$id\",\"type\":\"PAY_AS_YOU_GO\"}"
  elif [ "$membershipType" = "STUDENT_SAVER" ]; then
    PAYLOAD="{\"membershipId\":\"MEM-$id\",\"type\":\"STUDENT_SAVER\",\"studentIdNumber\":\"$durationOrStudentId\"}"
  else
    PAYLOAD="{\"membershipId\":\"MEM-$id\",\"type\":\"STANDARD\",\"durationMonths\":$durationOrStudentId}"
  fi
  STATUS=$(curl -s -o /tmp/r.json -w "%{http_code}" -X POST "$BASE_URL/api/members/$id/membership" -H "Content-Type: application/json" -H "$AUTH" -d "$PAYLOAD")
  if [ "$STATUS" != "201" ]; then echo "  [FAILED] $id membership assignment -> HTTP $STATUS: $(cat /tmp/r.json)"; return; fi
  echo "  [CREATED] $id membership ($membershipType) -> intended: $label"

  if [ -n "$backdateTo" ]; then
    curl -s -o /dev/null -X PATCH "$BASE_URL/api/admin/members/$id/membership/start-date" -H "Content-Type: application/json" -H "$AUTH" \
      -d "{\"startDate\":\"$backdateTo\"}"
    curl -s -o /dev/null -X POST "$BASE_URL/api/admin/members/$id/membership/clear-due-date" -H "$AUTH"
    echo "  [BACKDATED] $id start date set to $backdateTo"
  fi
}

echo ""
echo "════════════════════════════════════════"
echo " Creating 6 new Revenue demo members"
echo "════════════════════════════════════════"

create_member_and_membership DM08 "James Whitfield"   "james.whitfield@myfitness.demo"   "07700920008" STANDARD       12 "$OVERDUE_START_1" "OVERDUE"
create_member_and_membership DM09 "Charlotte Reyes"    "charlotte.reyes@myfitness.demo"   "07700920009" STANDARD       6  "$OVERDUE_START_2" "OVERDUE"
create_member_and_membership DM10 "Daniel Osei"        "daniel.osei@myfitness.demo"       "07700920010" STUDENT_SAVER  "S304921" "$DUESOON_START_1" "DUE_SOON"
create_member_and_membership DM11 "Priya Nair"         "priya.nair@myfitness.demo"        "07700920011" STANDARD       3  "$DUESOON_START_2" "DUE_SOON"
create_member_and_membership DM12 "Ryan Coleman"       "ryan.coleman@myfitness.demo"      "07700920012" STANDARD       12 ""                 "PAID"
create_member_and_membership DM13 "Sofia Marchetti"    "sofia.marchetti@myfitness.demo"   "07700920013" PAY_AS_YOU_GO  "" ""                 "N/A (no recurring due date)"

echo ""
echo "════════════════════════════════════════"
echo " Recomputing due dates for the 4 backdated members"
echo "════════════════════════════════════════"
curl -s -X POST "$BASE_URL/api/admin/backfill-membership-due-dates" -H "$AUTH"
echo ""

echo ""
echo "════════════════════════════════════════"
echo " Real resulting statuses — computed, not asserted"
echo "════════════════════════════════════════"
curl -s "$BASE_URL/api/members" -H "$AUTH" | python3 -c "
import sys, json
members = json.load(sys.stdin)
for m in members:
    if m.get('membership') and m['memberId'] in ('DM08','DM09','DM10','DM11','DM12','DM13'):
        print(m['memberId'], m['name'], '| status:', m['membership'].get('paymentStatus'), '| dueDate:', m['membership'].get('nextPaymentDueDate'))
"

echo ""
echo "Done. Check the Revenue page's Payment Status Breakdown now."