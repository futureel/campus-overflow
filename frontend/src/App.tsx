import { BrowserRouter, Link, Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth';
import { Toasts } from './components/Toasts';
import { useNotificationStream } from './useNotificationStream';
import { QuestionListPage } from './pages/QuestionListPage';
import { QuestionDetailPage } from './pages/QuestionDetailPage';
import { AskPage } from './pages/AskPage';
import { LoginPage } from './pages/LoginPage';
import { LeaderboardPage } from './pages/LeaderboardPage';
import { NotificationsPage } from './pages/NotificationsPage';

function Header() {
  const { me, logout } = useAuth();
  const navigate = useNavigate();
  const { unread, toasts, dismiss } = useNotificationStream(me);

  return (
    <>
      <header className="topbar">
        <Link className="brand" to="/">CampusOverflow</Link>
        <nav>
          <Link to="/">问题</Link>
          <Link to="/leaderboard">排行榜</Link>
          {me && <Link to="/notifications">通知{unread > 0 && <span className="badge">{unread}</span>}</Link>}
        </nav>
        <div className="spacer" />
        {me ? (
          <div className="user">
            <span className="username">{me.displayName}</span>
            <span className="role">{me.role}</span>
            <button onClick={() => logout().then(() => navigate('/'))}>退出</button>
          </div>
        ) : (
          <Link className="primary" to="/login">登录 / 注册</Link>
        )}
      </header>
      <Toasts items={toasts} onDismiss={dismiss} />
    </>
  );
}

function RequireAuth({ children }: { children: JSX.Element }) {
  const { me, loading } = useAuth();
  if (loading) return <p className="muted">加载中…</p>;
  return me ? children : <Navigate to="/login" replace />;
}

export function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Header />
        <main>
          <Routes>
            <Route path="/" element={<QuestionListPage />} />
            <Route path="/questions/:id" element={<QuestionDetailPage />} />
            <Route path="/ask" element={<RequireAuth><AskPage /></RequireAuth>} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/leaderboard" element={<LeaderboardPage />} />
            <Route path="/notifications" element={<RequireAuth><NotificationsPage /></RequireAuth>} />
          </Routes>
        </main>
      </AuthProvider>
    </BrowserRouter>
  );
}
