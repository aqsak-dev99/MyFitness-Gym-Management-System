import { useEffect, useMemo, useState } from 'react';
import { memberApi } from '../api/memberApi';
import { ApiError } from '../api/client';
import { useToast } from '../toast/ToastContext';
import { downloadCsv } from '../utils/csv';

import PageShell from '../components/layout/PageShell';
import LoadingSpinner from '../components/LoadingSpinner';
import styles from './AdminRevenue.module.css';

/**
 * Real revenue, computed entirely from data the backend already
 * returns — GET /api/members embeds each member's real membership
 * (including the derived paymentStatus field) and full paymentHistory
 * (real Payment records, reused from the exact same class bootcamp
 * fees use). No new backend endpoint was needed for this page — see
 * the inspection notes in the accompanying message for why.
 *
 * Currency: £, matching the existing convention already established
 * in AdminMembers.jsx's membership panel, not introducing a second
 * currency symbol into the same app.
 */
export default function AdminRevenue() {
  const { showToast } = useToast();
  const [members, setMembers] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => { loadMembers(); }, []);

  async function loadMembers() {
    setIsLoading(true);
    setError('');
    try {
      setMembers(await memberApi.getAll());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not load revenue data.');
    } finally {
      setIsLoading(false);
    }
  }

  const stats = useMemo(() => computeRevenueStats(members), [members]);

  function handleExportCsv() {
    const headers = ['Member', 'Description', 'Date', 'Amount (GBP)'];
    const rows = stats.allCompletedPayments.map((p) => [
      p.memberName,
      p.description,
      new Date(p.paymentDate).toLocaleDateString('en-GB'),
      p.amount.toFixed(2),
    ]);
    downloadCsv(`myfitness-revenue-${new Date().toISOString().slice(0, 10)}.csv`, headers, rows);
    showToast('Revenue data exported.');
  }

  return (
    <PageShell>
      <div className={styles.header}>
        <div>
          <h1 className={styles.title}>Revenue</h1>
          <p className={styles.subtitle}>Real membership payment activity, calculated from actual records.</p>
        </div>
        <button className={styles.exportBtn} onClick={handleExportCsv}>Export CSV</button>
      </div>

      {isLoading ? (
        <LoadingSpinner label="Loading revenue data…" />
      ) : error ? (
        <p className={styles.formError}>{error}</p>
      ) : (
        <>
          <div className={styles.kpiGrid}>
            <KpiCard label="Total Revenue" value={formatMoney(stats.totalRevenue)} accent="green" />
            <KpiCard label="Revenue This Month" value={formatMoney(stats.thisMonthRevenue)} accent="blue" />
            <KpiCard label="Revenue This Year" value={formatMoney(stats.thisYearRevenue)} accent="purple" />
            <KpiCard label="Completed Payments" value={stats.completedCount} accent="green" />
            <KpiCard label="Due Soon" value={stats.dueSoonCount} accent="gold" />
            <KpiCard label="Overdue" value={stats.overdueCount} accent="red" />
          </div>

          <div className={styles.midGrid}>
            <div className={styles.card}>
              <span className={styles.cardTitle}>Monthly Revenue</span>
              {stats.monthly.length === 0 ? (
                <p className={styles.empty}>No completed payments yet — chart will populate as real payments come in.</p>
              ) : (
                <MonthlyBarChart data={stats.monthly} />
              )}
            </div>

            <div className={styles.card}>
              <span className={styles.cardTitle}>Payment Status Breakdown</span>
              {stats.membersWithMembership === 0 ? (
                <p className={styles.empty}>No members have a membership assigned yet.</p>
              ) : (
                <div className={styles.breakdownList}>
                  <BreakdownRow label="Paid" count={stats.paidCount} total={stats.membersWithMembership} color="green" />
                  <BreakdownRow label="Due Soon" count={stats.dueSoonCount} total={stats.membersWithMembership} color="gold" />
                  <BreakdownRow label="Overdue" count={stats.overdueCount} total={stats.membersWithMembership} color="red" />
                  <BreakdownRow label="No recurring due date" count={stats.naCount} total={stats.membersWithMembership} color="gray" />
                </div>
              )}
            </div>
          </div>

          <div className={styles.card}>
            <span className={styles.cardTitle}>Recent Payments</span>
            {stats.recentPayments.length === 0 ? (
              <p className={styles.empty}>No completed payments recorded yet.</p>
            ) : (
              <div className={styles.paymentsTable}>
                <div className={styles.paymentsHead}>
                  <span>Member</span><span>Description</span><span>Date</span><span>Amount</span>
                </div>
                {stats.recentPayments.map((p) => (
                  <div key={p.paymentId} className={styles.paymentsRow}>
                    <span className={styles.memberName}>{p.memberName}</span>
                    <span className={styles.paymentDesc}>{p.description}</span>
                    <span className={styles.paymentDate}>{new Date(p.paymentDate).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })}</span>
                    <span className={styles.paymentAmount}>{formatMoney(p.amount)}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </>
      )}
    </PageShell>
  );
}

// ── real computation, no fabricated values ────────────────

function computeRevenueStats(members) {
  const allPayments = members.flatMap((m) =>
    m.paymentHistory.map((p) => ({ ...p, memberName: m.name }))
  );
  const completed = allPayments.filter((p) => p.status === 'COMPLETED');

  const now = new Date();
  const totalRevenue = sumAmounts(completed);
  const thisMonthRevenue = sumAmounts(completed.filter((p) => isSameMonth(p.paymentDate, now)));
  const thisYearRevenue = sumAmounts(completed.filter((p) => new Date(p.paymentDate).getFullYear() === now.getFullYear()));

  const withMembership = members.filter((m) => m.membership);
  const countByStatus = (status) => withMembership.filter((m) => m.membership.paymentStatus === status).length;

  const monthlyMap = {};
  completed.forEach((p) => {
    const d = new Date(p.paymentDate);
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
    monthlyMap[key] = (monthlyMap[key] || 0) + p.amount;
  });
  const monthly = Object.entries(monthlyMap)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([key, total]) => ({ key, total }));

  const recentPayments = [...completed].sort(
    (a, b) => new Date(b.paymentDate) - new Date(a.paymentDate)
  ).slice(0, 10);

  return {
    totalRevenue, thisMonthRevenue, thisYearRevenue,
    completedCount: completed.length,
    overdueCount: countByStatus('OVERDUE'),
    dueSoonCount: countByStatus('DUE_SOON'),
    paidCount: countByStatus('PAID'),
    naCount: countByStatus('N/A'),
    membersWithMembership: withMembership.length,
    monthly,
    recentPayments,
    // Full list, not just the top-10 slice shown on screen — the CSV
    // export should give an admin everything, not just what fits the UI.
    allCompletedPayments: [...completed].sort((a, b) => new Date(b.paymentDate) - new Date(a.paymentDate)),
  };
}

function sumAmounts(payments) { return payments.reduce((sum, p) => sum + p.amount, 0); }
function isSameMonth(dateStr, ref) {
  const d = new Date(dateStr);
  return d.getMonth() === ref.getMonth() && d.getFullYear() === ref.getFullYear();
}
function formatMoney(n) { return `£${n.toLocaleString('en-GB', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`; }

function KpiCard({ label, value, accent }) {
  return (
    <div className={`${styles.kpiCard} ${styles[accent]}`}>
      <span className={styles.kpiLabel}>{label}</span>
      <span className={styles.kpiValue}>{value}</span>
    </div>
  );
}

function BreakdownRow({ label, count, total, color }) {
  const pct = total > 0 ? Math.round((count / total) * 100) : 0;
  return (
    <div className={styles.breakdownRow}>
      <div className={styles.breakdownLabelRow}>
        <span>{label}</span>
        <span className={styles.breakdownCount}>{count}</span>
      </div>
      <div className={styles.barTrack}>
        <div className={`${styles.barFill} ${styles[color]}`} style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}

/**
 * Hand-rolled, dependency-free bar chart — no charting library is
 * installed in this project (confirmed: package.json lists only react/
 * react-dom/react-router-dom), and adding one just for this single
 * chart would be exactly the "unnecessary dependency" this build was
 * told to avoid. Same plain-div-with-dynamic-width technique already
 * proven in ClassCapacityOverview's progress bars.
 */
function MonthlyBarChart({ data }) {
  const max = Math.max(...data.map((d) => d.total), 1);
  return (
    <div className={styles.chart}>
      {data.map((d) => (
        <div key={d.key} className={styles.chartBarWrap}>
          <div className={styles.chartBarTrack}>
            <div className={styles.chartBar} style={{ height: `${(d.total / max) * 100}%` }} />
          </div>
          <span className={styles.chartValue}>{formatMoney(d.total)}</span>
          <span className={styles.chartLabel}>
            {new Date(d.key + '-01').toLocaleDateString('en-GB', { month: 'short', year: '2-digit' })}
          </span>
        </div>
      ))}
    </div>
  );
}