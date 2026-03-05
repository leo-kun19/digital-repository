import type { TimelineItem } from '../types/workflow';

interface CaseTimelineProps {
  items: TimelineItem[];
  emptyLabel?: string;
}

const EVENT_ICONS: Record<string, string> = {
  REGISTRATION_CREATED: '📋',
  REGISTRATION_SUBMITTED: '📨',
  REGISTRATION_APPROVED: '✅',
  REGISTRATION_VERIFIED: '🔍',
  REGISTRATION_REJECTED: '❌',
  SUBMISSION_UPLOADED: '📄',
  SUPERVISOR_FORWARDED_TO_LIBRARY: '📦',
  SUPERVISOR_REQUESTED_REVISION: '🔄',
  LIBRARY_CHECKLIST_REVIEWED: '📝',
  LIBRARY_APPROVED_FOR_CLEARANCE: '🏛️',
  LIBRARY_REQUESTED_REVISION: '🔄',
  CLEARANCE_SUBMITTED: '📃',
  CLEARANCE_APPROVED: '✅',
  PUBLISHED: '🚀',
};

function getIcon(type: string): string {
  return EVENT_ICONS[type] ?? '📌';
}

export default function CaseTimeline({ items, emptyLabel = 'No timeline activity yet.' }: CaseTimelineProps) {
  if (items.length === 0) {
    return (
      <div className="su-empty-state">
        <div className="su-empty-icon">📭</div>
        <div>{emptyLabel}</div>
      </div>
    );
  }

  return (
    <ul className="timeline-list list-unstyled mb-0">
      {items.map((item, index) => (
        <li key={`${item.type}-${item.at ?? index}-${index}`} className="fade-in" style={{ animationDelay: `${index * 0.05}s` }}>
          <div className="d-flex flex-wrap align-items-center gap-2 mb-1">
            <span style={{ fontSize: '1rem' }}>{getIcon(item.type)}</span>
            <span className="text-muted small">{item.at ? new Date(item.at).toLocaleString() : 'N/A'}</span>
            {item.actorRole && (
              <span className="badge bg-secondary-subtle text-secondary-emphasis"
                style={{ borderRadius: '999px', fontSize: '0.7rem', padding: '0.2rem 0.5rem' }}>
                {item.actorRole}
              </span>
            )}
          </div>
          <div className="fw-semibold" style={{ fontSize: '0.9rem' }}>{item.message || item.type}</div>
          {item.actorEmail && <div className="text-muted small">{item.actorEmail}</div>}
        </li>
      ))}
    </ul>
  );
}
