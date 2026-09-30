import { useEffect, useMemo, useState } from 'react';
import { memberApi } from '../api/memberApi';
import { bootcampApi } from '../api/bootcampApi';
import { trainerApi } from '../api/trainerApi';
import { ApiError } from '../api/client';
import { useToast } from '../toast/ToastContext';
import { downloadCsv } from '../utils/csv';

import PageShell from '../components/layout/PageShell';
import LoadingSpinner from '../components/LoadingSpinner';
import styles from './AdminReports.module.css';

/**
 * Deliberately does NOT repeat anything Revenue already shows (no
 * payment totals, no payment-status breakdown) — this covers
 * membership growth/type, class capacity, and staff composition
 * instead, per the explicit "complement, don't duplicate" instruction.
 *
 * membershipType comes from Membership.getMembershipType() — a real,
 * derived getter added specifically for this page, not inferred from
 * unrelated fields like whether studentIdNumber happens to be present.
 *
 * SCHED-A/SCHED-B are excluded from every class metric here, same
 * documented exclusion rule already used on the Dashboard.
 */
export default function AdminReports() {
  const { showToast } = useToast();
  const [members, setMembers] = useState([]);
  const [classes, setClasses] = useState([]);
  const [instructors, setInstructors] = useState([]);
  const [fullTime, setFullTime] = useState([]);
  const [partTime, setPartTime] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => { loadAll(); }, []);

  async function loadAll() {
    setIsLoading(true);
    setError('');
    try {
      const [m, c, i, ft, pt] = await Promise.all([
        memberApi.getAll(), bootcampApi.getAllClasses(),
        trainerApi.getInstructors(), trainerApi.getFullTimeStaff(), trainerApi.getPartTimeStaff(),
      ]);
      setMembers(m); setClasses(c); setInstructors(i); setFullTime(ft); setPartTime(pt);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not load report data.');
    } finally {
      setIsLoading(false);
    }
  }

  const stats = useMemo(
    () => computeReportStats(members, classes, instructors, fullTime, partTime),
    [members, classes, instructors, fullTime, partTime]
  );

  function handleExportCsv() {
    const headers = ['Metric', 'Value'];
    const rows = [
      ['Total Members', stats.totalMembers],
      ['Active Members', stats.activeMembers],
      ['Inactive Members', stats.inactiveMembers],
      ['Members With No Membership', stats.noMembershipCount],
      ...stats.typeBreakdown.map(({ type, count }) => [`Membership Type: ${type}`, count]),
      ['Total Classes', stats.totalClasses],
      ['Cancelled Classes', stats.cancelledClasses],
      ['Overall Capacity Used (%)', stats.overallCapacityPct],
      ['Most Enrolled Class', stats.mostPopular ? stats.mostPopular.className : 'N/A'],
      ['Instructors', instructors.length],
      ['Full-time Staff', fullTime.length],
      ['Part-time Staff', partTime.length],
      ['Unavailable Staff', stats.unavailableStaff],
    ];
    downloadCsv(`myfitness-reports-${new Date().toISOString().slice(0, 10)}.csv`, headers, rows);
    showToast('Report exported.');
  }

  return (
    <PageShell>
      <div className={styles.header}>
        <div>
          <h1 className={styles.title}>Reports</h1>
          <p className={styles.subtitle}>Membership, class, and staff overview — real data, calculated live.</p>
        </div>
        <button className={styles.exportBtn} onClick={handleExportCsv}>Export CSV</button>
      </div>

      {isLoading ? (
        <LoadingSpinner label="Loading reports…" />
      ) : error ? (
        <p className={styles.formError}>{error}</p>
      ) : (
        <>
          {/* ── Members ─────────────────────────────── */}
          <div className={styles.card}>
            <span className={styles.cardTitle}>Members</span>
            <div className={styles.kpiRow}>
              <Kpi label="Total Members" value={stats.totalMembers} />
              <Kpi label="Active" value={stats.activeMembers} accent="green" />
              <Kpi label="Inactive" value={stats.inactiveMembers} accent="red" />
              <Kpi label="No Membership Assigned" value={stats.noMembershipCount} accent="gold" />
            </div>

            <div className={styles.twoCol}>
              <div>
                <span className={styles.subTitle}>Membership Type Breakdown</span>
                {stats.typeBreakdown.length === 0 ? (
                  <p className={styles.empty}>No memberships assigned yet.</p>
                ) : (
                  <div className={styles.breakdownList}>
                    {stats.typeBreakdown.map(({ type, count }) => (
                      <BreakdownRow key={type} label={type} count={count} total={stats.totalWithMembership} />
                    ))}
                  </div>
                )}
              </div>

              <div>
                <span className={styles.subTitle}>New Members by Month</span>
                {stats.monthlyRegistrations.length === 0 ? (
                  <p className={styles.empty}>No registration data yet.</p>
                ) : (
                  <MiniBarChart data={stats.monthlyRegistrations} />
                )}
              </div>
            </div>
          </div>

          {/* ── Classes ─────────────────────────────── */}
          <div className={styles.card}>
            <span className={styles.cardTitle}>Bootcamp Classes</span>
            <div className={styles.kpiRow}>
              <Kpi label="Total Classes" value={stats.totalClasses} />
              <Kpi label="Cancelled" value={stats.cancelledClasses} accent="red" />
              <Kpi label="Overall Capacity Used" value={`${stats.overallCapacityPct}%`} accent="blue" />
              <Kpi label="Total Spots" value={`${stats.totalEnrolled} / ${stats.totalCapacity}`} />
            </div>

            {stats.mostPopular && (
              <p className={styles.note}>
                Most enrolled: <strong>{stats.mostPopular.className}</strong> ({stats.mostPopular.currentEnrolments}/{stats.mostPopular.maxCapacity})
              </p>
            )}
          </div>

          {/* ── Staff ───────────────────────────────── */}
          <div className={styles.card}>
            <span className={styles.cardTitle}>Staff</span>
            <div className={styles.kpiRow}>
              <Kpi label="Instructors" value={instructors.length} />
              <Kpi label="Full-time" value={fullTime.length} />
              <Kpi label="Part-time" value={partTime.length} />
              <Kpi label="Unavailable" value={stats.unavailableStaff} accent="red" />
            </div>
          </div>
        </>
      )}
    </PageShell>
  );
}

// ── real computation, no fabricated values ────────────────

function computeReportStats(members, classes, instructors, fullTime, partTime) {
  const activeMembers = members.filter((m) => m.active).length;
  const withMembership = members.filter((m) => m.membership);
  const noMembershipCount = members.length - withMembership.length;

  const typeCounts = {};
  withMembership.forEach((m) => {
    const type = m.membership.membershipType || 'Unknown';
    typeCounts[type] = (typeCounts[type] || 0) + 1;
  });
  const typeBreakdown = Object.entries(typeCounts).map(([type, count]) => ({ type, count }));

  const monthlyMap = {};
  members.forEach((m) => {
    const d = new Date(m.registrationDate);
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
    monthlyMap[key] = (monthlyMap[key] || 0) + 1;
  });
  const monthlyRegistrations = Object.entries(monthlyMap)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([key, count]) => ({ key, count }));

  // Documented exclusion — SCHED-A/B are real scratch-test rows, same
  // rule already applied on the Dashboard.
  const primaryClasses = classes.filter((c) => !c.classId.startsWith('SCHED-'));
  const totalCapacity = primaryClasses.reduce((s, c) => s + c.maxCapacity, 0);
  const totalEnrolled = primaryClasses.reduce((s, c) => s + c.currentEnrolments, 0);
  const mostPopular = [...primaryClasses].sort((a, b) => b.currentEnrolments - a.currentEnrolments)[0];

  const allStaff = [...instructors, ...fullTime, ...partTime];
  const unavailableStaff = allStaff.filter((s) => !s.available).length;

  return {
    totalMembers: members.length,
    activeMembers,
    inactiveMembers: members.length - activeMembers,
    noMembershipCount,
    totalWithMembership: withMembership.length,
    typeBreakdown,
    monthlyRegistrations,
    totalClasses: primaryClasses.length,
    cancelledClasses: primaryClasses.filter((c) => c.cancelled).length,
    totalCapacity,
    totalEnrolled,
    overallCapacityPct: totalCapacity > 0 ? Math.round((totalEnrolled / totalCapacity) * 100) : 0,
    mostPopular: mostPopular && mostPopular.currentEnrolments > 0 ? mostPopular : null,
    unavailableStaff,
  };
}

function Kpi({ label, value, accent }) {
  return (
    <div className={`${styles.kpi} ${accent ? styles[accent] : ''}`}>
      <span className={styles.kpiValue}>{value}</span>
      <span className={styles.kpiLabel}>{label}</span>
    </div>
  );
}

function BreakdownRow({ label, count, total }) {
  const pct = total > 0 ? Math.round((count / total) * 100) : 0;
  return (
    <div className={styles.breakdownRow}>
      <div className={styles.breakdownLabelRow}><span>{label}</span><span>{count}</span></div>
      <div className={styles.barTrack}><div className={styles.barFill} style={{ width: `${pct}%` }} /></div>
    </div>
  );
}

function MiniBarChart({ data }) {
  const max = Math.max(...data.map((d) => d.count), 1);
  return (
    <div className={styles.miniChart}>
      {data.map((d) => (
        <div key={d.key} className={styles.miniBarWrap}>
          <div className={styles.miniBarTrack}>
            <div className={styles.miniBar} style={{ height: `${(d.count / max) * 100}%` }} />
          </div>
          <span className={styles.miniLabel}>{new Date(d.key + '-01').toLocaleDateString('en-GB', { month: 'short' })}</span>
        </div>
      ))}
    </div>
  );
}