import { getMissionStatusLabel } from '../utils/missionStatus';

const STATUS_VARIANTS = {
  PENDING: 'status-badge--pending',
  IN_PROGRESS: 'status-badge--in-progress',
  COMPLETED: 'status-badge--completed',
  CANCELLED: 'status-badge--cancelled',
};

export default function StatusBadge({ status }) {
  const variant = STATUS_VARIANTS[status] ?? '';

  return <span className={`status-badge ${variant}`.trim()}>{getMissionStatusLabel(status)}</span>;
}
