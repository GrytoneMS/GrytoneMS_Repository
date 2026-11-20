import { NavLink } from 'react-router'
import { useNotifications } from './context'
import './notifications.css'

export function NotificationLink() {
  const { unread, example } = useNotifications()
  return <NavLink className="notification-link" to={example ? '/notifications?notificationExample=true' : '/notifications'}>
    <span className="notification-label" data-label="알림"><span>알림</span></span>{unread !== null && unread > 0 && <span className="notification-badge" aria-label={`안 읽은 알림 ${unread}개`}>{unread > 99 ? '99+' : unread}</span>}
  </NavLink>
}
