/**
 * Hand-written outline icons, no icon library dependency. Consistent
 * 24x24 viewBox, stroke-based, matching the reference images' clean
 * line-icon style. Each takes a size/color prop for reuse across cards.
 */
const base = { fill: 'none', strokeWidth: 1.7, strokeLinecap: 'round', strokeLinejoin: 'round' };

export function CardIcon({ size = 20, color = 'currentColor' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" stroke={color} {...base}>
      <rect x="2" y="5" width="20" height="14" rx="2.5" />
      <path d="M2 10h20" />
      <path d="M6 15h4" />
    </svg>
  );
}

export function TargetIcon({ size = 20, color = 'currentColor' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" stroke={color} {...base}>
      <circle cx="12" cy="12" r="9" />
      <circle cx="12" cy="12" r="5" />
      <circle cx="12" cy="12" r="1" fill={color} />
    </svg>
  );
}

export function RevenueIcon({ size = 22, color = 'currentColor' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" stroke={color} {...base}>
      <path d="M12 3v18" />
      <path d="M16.5 7.5c0-1.66-2-3-4.5-3s-4.5 1.34-4.5 3 2 3 4.5 3 4.5 1.34 4.5 3-2 3-4.5 3-4.5-1.34-4.5-3" />
    </svg>
  );
}

export function ReportsIcon({ size = 22, color = 'currentColor' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" stroke={color} {...base}>
      <rect x="3" y="3" width="18" height="18" rx="2" />
      <path d="M8 16v-4M12 16V8M16 16v-6" />
    </svg>
  );
}

export function CalendarIcon({ size = 20, color = 'currentColor' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" stroke={color} {...base}>
      <rect x="3" y="4.5" width="18" height="16" rx="2.5" />
      <path d="M3 9.5h18" />
      <path d="M8 2.5v4M16 2.5v4" />
    </svg>
  );
}

export function SparkleIcon({ size = 20, color = 'currentColor' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" stroke={color} {...base}>
      <path d="M12 3l1.8 5.4L19 10l-5.2 1.6L12 17l-1.8-5.4L5 10l5.2-1.6L12 3z" />
      <path d="M19 15l.7 2.1L22 18l-2.3.9L19 21l-.7-2.1L16 18l2.3-.9L19 15z" />
    </svg>
  );
}

export function UsersIcon({ size = 22, color = 'currentColor' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" stroke={color} {...base}>
      <circle cx="9" cy="8" r="3.2" />
      <path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6" />
      <circle cx="17.5" cy="9" r="2.4" />
      <path d="M15.8 14.2c2.4.3 4.2 2.4 4.2 5" />
    </svg>
  );
}

export function LayersIcon({ size = 22, color = 'currentColor' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" stroke={color} {...base}>
      <path d="M12 3l9 5-9 5-9-5 9-5z" />
      <path d="M3 13l9 5 9-5" />
    </svg>
  );
}

export function ShieldIcon({ size = 22, color = 'currentColor' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" stroke={color} {...base}>
      <path d="M12 3l7 3v6c0 4.4-3 7.5-7 9-4-1.5-7-4.6-7-9V6l7-3z" />
      <path d="M9 12l2 2 4-4" />
    </svg>
  );
}

export function ProfileGlyph({ size = 40, color = 'currentColor' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" stroke={color} {...base}>
      <circle cx="12" cy="8" r="3.6" />
      <path d="M4.5 20c0-4.1 3.4-7.5 7.5-7.5s7.5 3.4 7.5 7.5" />
    </svg>
  );
}

export function SettingsIcon({ size = 18, color = 'currentColor' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" stroke={color} {...base}>
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.7 1.7 0 00.3 1.9l.1.1a2 2 0 11-2.9 2.9l-.1-.1a1.7 1.7 0 00-1.9-.3 1.7 1.7 0 00-1 1.6V21a2 2 0 11-4 0v-.2a1.7 1.7 0 00-1.1-1.6 1.7 1.7 0 00-1.9.3l-.1.1a2 2 0 11-2.9-2.9l.1-.1a1.7 1.7 0 00.3-1.9 1.7 1.7 0 00-1.6-1H3a2 2 0 110-4h.2a1.7 1.7 0 001.6-1.1 1.7 1.7 0 00-.3-1.9l-.1-.1a2 2 0 112.9-2.9l.1.1a1.7 1.7 0 001.9.3H9.5a1.7 1.7 0 001-1.6V3a2 2 0 114 0v.2a1.7 1.7 0 001 1.6 1.7 1.7 0 001.9-.3l.1-.1a2 2 0 112.9 2.9l-.1.1a1.7 1.7 0 00-.3 1.9v.1a1.7 1.7 0 001.6 1H21a2 2 0 110 4h-.2a1.7 1.7 0 00-1.6 1z" />
    </svg>
  );
}

export function ChatIcon({ size = 20, color = 'currentColor' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" stroke={color} {...base}>
      <path d="M4 5.5h16a1 1 0 011 1v10a1 1 0 01-1 1H9l-4.5 3.5V17.5H4a1 1 0 01-1-1v-10a1 1 0 011-1z" />
      <circle cx="9" cy="11.5" r="0.9" fill={color} stroke="none" />
      <circle cx="12.5" cy="11.5" r="0.9" fill={color} stroke="none" />
      <circle cx="16" cy="11.5" r="0.9" fill={color} stroke="none" />
    </svg>
  );
}

export function ArrowIcon({ size = 16, color = 'currentColor' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" stroke={color} {...base}>
      <path d="M5 12h14M13 6l6 6-6 6" />
    </svg>
  );
}

export function SunIcon({ size = 16, color = 'currentColor' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" stroke={color} {...base}>
      <circle cx="12" cy="12" r="4.5" />
      <path d="M12 2.5v2.5M12 19v2.5M4.2 4.2l1.8 1.8M18 18l1.8 1.8M2.5 12H5M19 12h2.5M4.2 19.8L6 18M18 6l1.8-1.8" />
    </svg>
  );
}

export function MoonIcon({ size = 16, color = 'currentColor' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" stroke={color} {...base}>
      <path d="M20 14.5A8.5 8.5 0 1110 3.2a7 7 0 0010 11.3z" />
    </svg>
  );
}