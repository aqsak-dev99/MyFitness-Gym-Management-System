import { useState } from 'react';
import GlassCard from './GlassCard';
import ConfirmDialog from '../ConfirmDialog';
import styles from './MembershipPaymentPanel.module.css';

/**
 * The member's payment panel on the Membership page.
 *
 * What it shows comes straight from the backend's own derived fields —
 * membership.paymentStatus (OVERDUE / DUE_SOON / PAID / N/A) and
 * membership.nextPaymentDueDate — so the UI never recomputes billing
 * rules; it only presents them. The "Pay now" button appears only when
 * the status is OVERDUE or DUE_SOON, matching the server, which also
 * refuses a payment when nothing is due.
 *
 * "Pay now" is a SIMULATED payment: no card is entered or charged. The
 * confirm dialog and a footnote say so plainly.
 *
 * The page owns the API call (onPay); this component owns only the
 * confirm-dialog state, so it stays presentational.
 */

const MAX_PAYMENTS_SHOWN = 5;

// Dates arrive as "YYYY-MM-DD". Parse the parts directly into a LOCAL
// date — new Date("2026-08-08") would be read as UTC and can display a
// day early in some time zones.
function parseDate(value) {
  if (!value) return null;
  if (Array.isArray(value)) return new Date(value[0], value[1] - 1, value[2]);
  const [y, m, d] = String(value).slice(0, 10).split('-').map(Number);
  return new Date(y, m - 1, d);
}

function formatDate(value) {
  const d = parseDate(value);
  return d
    ? d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
    : '—';
}

function daysFromToday(value) {
  const d = parseDate(value);
  if (!d) return null;
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.round((d - today) / 86400000);
}

function money(amount) {
  return `£${Number(amount).toFixed(2)}`;
}

function plural(n, word) {
  return `${n} ${word}${n === 1 ? '' : 's'}`;
}

// Stored descriptions end in a class name ("… — StandardMembership").
// Tidy it for display only; nothing is changed in the data.
function prettyDescription(description) {
  return String(description || 'Payment')
    .replace(/Membership$/, '')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .trim();
}

function statusChip(status) {
  if (status === 'COMPLETED') return { label: 'Completed', className: styles.chipOk };
  if (status === 'FAILED') return { label: 'Failed', className: styles.chipDanger };
  return { label: 'Pending', className: styles.chipWarn };
}

function describeStatus(membership) {
  const fee = money(membership.monthlyFee);
  const due = formatDate(membership.nextPaymentDueDate);
  const days = daysFromToday(membership.nextPaymentDueDate);

  switch (membership.paymentStatus) {
    case 'OVERDUE':
      return {
        label: 'Overdue',
        chip: styles.chipDanger,
        headline: `${fee} was due on ${due}`,
        detail: `${plural(Math.abs(days), 'day')} overdue. Pay now to bring your membership up to date.`,
        canPay: true,
      };
    case 'DUE_SOON':
      return {
        label: 'Due soon',
        chip: styles.chipWarn,
        headline: `${fee} is due on ${due}`,
        detail: days === 0 ? 'Due today.' : days === 1 ? 'Due tomorrow.' : `Due in ${plural(days, 'day')}.`,
        canPay: true,
      };
    case 'PAID':
      return {
        label: 'Paid up',
        chip: styles.chipOk,
        headline: `Next payment: ${fee} on ${due}`,
        detail: 'You are all paid up. Nothing to do right now.',
        canPay: false,
      };
    default:
      return {
        label: 'No recurring payments',
        chip: styles.chipMuted,
        headline: 'This membership has no monthly payments.',
        detail: 'There is nothing to pay here.',
        canPay: false,
      };
  }
}

export default function MembershipPaymentPanel({ membership, payments = [], onPay, isPaying = false }) {
  const [confirmOpen, setConfirmOpen] = useState(false);
  if (!membership) return null;

  const info = describeStatus(membership);
  const fee = money(membership.monthlyFee);

  const recent = [...payments]
    .sort((a, b) => (parseDate(b.paymentDate) ?? 0) - (parseDate(a.paymentDate) ?? 0))
    .slice(0, MAX_PAYMENTS_SHOWN);

  async function handleConfirm() {
    setConfirmOpen(false);
    await onPay();
  }

  return (
    <>
      <GlassCard className={styles.panel}>
        <div className={styles.header}>
          <span className={styles.eyebrow}>Payments</span>
          <span className={`${styles.chip} ${info.chip}`}>{info.label}</span>
        </div>

        <p className={styles.headline}>{info.headline}</p>
        <p className={styles.detail}>{info.detail}</p>

        {info.canPay && (
          <>
            <button
              type="button"
              className={styles.payBtn}
              onClick={() => setConfirmOpen(true)}
              disabled={isPaying}
            >
              {isPaying ? 'Processing…' : `Pay ${fee} now`}
            </button>
            <p className={styles.footnote}>
              Demo mode — this simulates a payment. No card is used and nothing is charged.
            </p>
          </>
        )}

        {recent.length > 0 && (
          <div className={styles.history}>
            <h2 className={styles.historyTitle}>Recent payments</h2>
            <ul className={styles.list}>
              {recent.map((p) => {
                const chip = statusChip(p.status);
                return (
                  <li key={p.paymentId} className={styles.row}>
                    <div className={styles.rowMain}>
                      <span className={styles.rowDesc}>{prettyDescription(p.description)}</span>
                      <span className={styles.rowDate}>{formatDate(p.paymentDate)}</span>
                    </div>
                    <span className={styles.rowAmount}>{money(p.amount)}</span>
                    <span className={`${styles.chip} ${chip.className}`}>{chip.label}</span>
                  </li>
                );
              })}
            </ul>
          </div>
        )}
      </GlassCard>

      {/* Rendered OUTSIDE the GlassCard on purpose: the card's backdrop-filter
          and hover transform make it the containing block for any
          position:fixed child, which would trap the dialog's full-screen
          overlay inside the card instead of covering the page. */}
      <ConfirmDialog
        open={confirmOpen}
        tone="primary"
        title={`Pay ${fee}?`}
        message={`This is a demo: no real card is charged. It records a ${fee} membership payment on your account and moves your next due date forward by one month.`}
        confirmLabel={`Pay ${fee}`}
        onConfirm={handleConfirm}
        onCancel={() => setConfirmOpen(false)}
      />
    </>
  );
}
