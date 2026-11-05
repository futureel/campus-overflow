import { useEffect, useState } from 'react';
import { api } from './api';
import type { Me } from './types';

export type ToastItem = { id: number; title: string; link: string };

const STREAM_PATH = '/api/v1/notifications/stream';
const POLL_INTERVAL_MS = 30_000;
const TOAST_TTL_MS = 6_000;

/**
 * 未读通知 + 实时提醒。
 * 优先用 SSE（EventSource，靠会话 Cookie 鉴权）；后端若没有该端点或连接中断，
 * 自动退回 30 秒轮询，保证功能不会因为后端暂未实现而失效。
 */
export function useNotificationStream(me: Me | null) {
  const [unread, setUnread] = useState(0);
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  useEffect(() => {
    if (!me) {
      setUnread(0);
      setToasts([]);
      return;
    }

    let source: EventSource | null = null;
    let pollTimer: number | undefined;
    let disposed = false;

    const refreshUnread = () => {
      api.unreadCount().then((r) => setUnread(r.count)).catch(() => undefined);
    };

    const pushToast = (title: string, link: string) => {
      const id = Date.now() + Math.random();
      setToasts((list) => [...list.slice(-4), { id, title, link }]);
      window.setTimeout(() => {
        setToasts((list) => list.filter((t) => t.id !== id));
      }, TOAST_TTL_MS);
    };

    const startPolling = () => {
      if (pollTimer !== undefined || disposed) return;
      pollTimer = window.setInterval(refreshUnread, POLL_INTERVAL_MS);
    };

    const stopPolling = () => {
      if (pollTimer !== undefined) {
        clearInterval(pollTimer);
        pollTimer = undefined;
      }
    };

    refreshUnread();
    startPolling();

    try {
      source = new EventSource(STREAM_PATH, { withCredentials: true });

      source.onopen = () => stopPolling();

      const onNotification = (event: Event) => {
        const payload = event as MessageEvent<string>;
        try {
          const data = JSON.parse(payload.data) as { title?: string; link?: string; read?: boolean };
          if (data.title) {
            pushToast(data.title, data.link ?? '/notifications');
          }
        } catch {
          // 后端推送的不是 JSON 时不弹提示，只刷新未读数
        }
        refreshUnread();
      };

      source.addEventListener('notification', onNotification);
      source.onmessage = onNotification;

      source.onerror = () => {
        // 端点不存在（404）或连接断开：关掉 SSE，退回轮询，避免无谓的重连风暴
        source?.close();
        source = null;
        startPolling();
      };
    } catch {
      startPolling();
    }

    return () => {
      disposed = true;
      source?.close();
      stopPolling();
    };
  }, [me]);

  const dismiss = (id: number) => setToasts((list) => list.filter((t) => t.id !== id));

  return { unread, toasts, dismiss };
}
