import { useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { memberApi } from '../api/memberApi';
import { bootcampApi } from '../api/bootcampApi';
import { trainerApi } from '../api/trainerApi';
import { ApiError } from '../api/client';

import PageShell from '../components/layout/PageShell';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorMessage from '../components/ErrorMessage';
import AdminHeroBanner from '../components/dashboard/AdminHeroBanner';
import MembershipCard from '../components/dashboard/MembershipCard';
import GoalCard from '../components/dashboard/GoalCard';
import BootcampClassesCard from '../components/dashboard/BootcampClassesCard';
import BootcampSummaryCard from '../components/dashboard/BootcampSummaryCard';
import QuickActions from '../components/dashboard/QuickActions';
import AiAssistantCard from '../components/dashboard/AiAssistantCard';
import EmptyProfileState from '../components/dashboard/EmptyProfileState';
import AdminStatsGrid from '../components/dashboard/AdminStatsGrid';
import ClassCapacityOverview from '../components/dashboard/ClassCapacityOverview';
import MembersNeedingAttention from '../components/dashboard/MembersNeedingAttention';
import StaffOverviewPanel from '../components/dashboard/StaffOverviewPanel';
import RecentMembersPanel from '../components/dashboard/RecentMembersPanel';
import AdminQuickActions from '../components/dashboard/AdminQuickActions';
import AdminAiBanner from '../components/dashboard/AdminAiBanner';

import styles from './Dashboard.module.css';

/**
 * Dashboard — role branches INSIDE this one component (not via
 * RoleRoute, which guards whole ROUTES, not content within one). Data
 * fetching is entirely real: MEMBER calls GET /api/members/me (built
 * specifically to support this page) and the 404 "no profile linked"
 * response is caught explicitly and routed to a real empty state, not
 * treated as a generic error. ADMIN fetches three real list endpoints
 * in parallel and uses their actual lengths for counts — no dedicated
 * count endpoint exists on the backend.
 */
export default function Dashboard() {
  const { user } = useAuth();
  const isAdmin = user.role === 'ADMIN';

  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [hasNoProfile, setHasNoProfile] = useState(false);

  // MEMBER state
  const [profile, setProfile] = useState(null);
  const [myClasses, setMyClasses] = useState([]);

  // ADMIN state — full real records kept (not just counts), so the
  // richer panels below can derive everything they need without a
  // second round of fetches.
  const [adminMembers, setAdminMembers] = useState([]);
  const [adminClasses, setAdminClasses] = useState([]);
  const [instructors, setInstructors] = useState([]);
  const [fullTimeStaff, setFullTimeStaff] = useState([]);
  const [partTimeStaff, setPartTimeStaff] = useState([]);

  useEffect(() => {
    async function loadDashboard() {
      setIsLoading(true);
      setError('');
      setHasNoProfile(false);

      try {
        if (isAdmin) {
          const [members, classes, insts, ft, pt] = await Promise.all([
            memberApi.getAll(),
            bootcampApi.getAllClasses(),
            trainerApi.getInstructors(),
            trainerApi.getFullTimeStaff(),
            trainerApi.getPartTimeStaff(),
          ]);
          setAdminMembers(members);
          setAdminClasses(classes);
          setInstructors(insts);
          setFullTimeStaff(ft);
          setPartTimeStaff(pt);
        } else {
          const myProfile = await memberApi.getMyProfile();
          setProfile(myProfile);

          const allClasses = await bootcampApi.getAllClasses();
          const enrolled = allClasses.filter((bc) =>
            bc.participants.some((p) => p.memberId === myProfile.memberId)
          );
          setMyClasses(enrolled);
        }
      } catch (err) {
        // The 404 from GET /api/members/me for an account with no
        // linkedMemberId is an EXPECTED, real state — not a failure.
        if (err instanceof ApiError && err.status === 404) {
          setHasNoProfile(true);
        } else {
          setError(err instanceof ApiError ? err.message : 'Could not load your dashboard.');
        }
      } finally {
        setIsLoading(false);
      }
    }
    loadDashboard();
  }, [isAdmin]);

  /**
   * Explicit, documented exclusion rule: SCHED-A and SCHED-B are real
   * rows in the database (confirmed, not hidden or deleted — see
   * ClassCapacityOverview's own note), but were created as ad-hoc
   * scheduling-conflict test scratch data, not part of the deliberate
   * demo class set. Excluded from every primary Admin metric below
   * (KPI count, capacity totals, the capacity overview panel) so the
   * dashboard reflects the gym's real operational classes. No backend
   * change and no deletion — purely a frontend display rule.
   */
  const primaryClasses = adminClasses.filter((c) => !c.classId.startsWith('SCHED-'));
  const totalEnrolled = primaryClasses.reduce((sum, c) => sum + c.currentEnrolments, 0);
  const totalCapacity = primaryClasses.reduce((sum, c) => sum + c.maxCapacity, 0);

  const oneWeekAgo = new Date();
  oneWeekAgo.setDate(oneWeekAgo.getDate() - 7);
  const newMembersThisWeek = adminMembers.filter(
    (m) => new Date(m.registrationDate) >= oneWeekAgo
  ).length;

  const now = new Date();

  return (
    <PageShell
      backgroundImage={isAdmin ? undefined : '/assets/member-dashboard-bg.jpg'}
      heroBanner={isAdmin && !isLoading && !error && !hasNoProfile ? <AdminHeroBanner now={now} /> : null}
    >
      {isLoading ? (
        <div className={styles.centeredState}>
          <LoadingSpinner label="Loading your dashboard…" />
        </div>
      ) : error ? (
        <div className={styles.centeredState}>
          <ErrorMessage>{error}</ErrorMessage>
        </div>
      ) : hasNoProfile ? (
        <div className={styles.centeredState}>
          <EmptyProfileState username={user.username} />
        </div>
      ) : (
        <>
          {!isAdmin && (
            <div className={styles.header}>
              <div>
                <h1 className={styles.greeting}>
                  Welcome back{profile?.name ? `, ${profile.name}` : ''}! 👋
                </h1>
                <p className={styles.headerSub}>Your fitness journey starts here.</p>
              </div>
            </div>
          )}

          {isAdmin ? (
            <>
              <AdminStatsGrid
                memberCount={adminMembers.length}
                newMembersThisWeek={newMembersThisWeek}
                classCount={primaryClasses.length}
                instructorCount={instructors.length}
                fullTimeCount={fullTimeStaff.length}
                partTimeCount={partTimeStaff.length}
                totalEnrolled={totalEnrolled}
                totalCapacity={totalCapacity}
              />

              <div className={styles.adminMidGrid}>
                <ClassCapacityOverview classes={primaryClasses} />
                <MembersNeedingAttention members={adminMembers} />
              </div>

              <div className={styles.adminBottomGrid}>
                <StaffOverviewPanel instructors={instructors} fullTime={fullTimeStaff} partTime={partTimeStaff} />
                <RecentMembersPanel members={adminMembers} />
                <AdminQuickActions />
              </div>

              <AdminAiBanner />
            </>
          ) : (
            <>
              <div className={styles.summaryGrid}>
                <MembershipCard membership={profile.membership} />
                <div id="goal-card-section">
                  <GoalCard
                    memberId={profile.memberId}
                    fitnessGoal={profile.fitnessGoal}
                    onGoalUpdated={(newGoal) => setProfile((p) => ({ ...p, fitnessGoal: newGoal }))}
                  />
                </div>
                <BootcampSummaryCard count={myClasses.length} />
              </div>

              <div className={styles.classesSection}>
                <BootcampClassesCard classes={myClasses} />
              </div>

              <div className={styles.bottomGrid}>
                <QuickActions />
                <AiAssistantCard />
              </div>
            </>
          )}
        </>
      )}
    </PageShell>
  );
}