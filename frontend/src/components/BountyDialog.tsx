import { useState } from 'react';

export type BountyValue = { points: number; days: number };

type Props = {
  title?: string;
  initial?: BountyValue;
  onCancel: () => void;
  onConfirm: (value: BountyValue) => void;
};

const MIN_POINTS = 10;
const MAX_POINTS = 500;
const MAX_DAYS = 30;

/** 悬赏弹窗：替代原来的 window.prompt，避免手机端被系统弹窗打断、也无法校验范围。 */
export function BountyDialog({ title = '发起悬赏', initial, onCancel, onConfirm }: Props) {
  const [points, setPoints] = useState(initial?.points ?? 50);
  const [days, setDays] = useState(initial?.days ?? 7);

  const valid = points >= MIN_POINTS && points <= MAX_POINTS && days >= 1 && days <= MAX_DAYS;

  return (
    <div className="modal-backdrop" onClick={onCancel}>
      <div className="modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <h3>{title}</h3>

        <label>
          悬赏积分（{MIN_POINTS}–{MAX_POINTS}，当前 <b>{points}</b>）
          <input
            type="range"
            min={MIN_POINTS}
            max={MAX_POINTS}
            step={10}
            value={points}
            onChange={(e) => setPoints(Number(e.target.value))}
          />
        </label>

        <label>
          有效期
          <select value={days} onChange={(e) => setDays(Number(e.target.value))}>
            {[1, 3, 7, 14, 30].map((d) => (
              <option key={d} value={d}>{d} 天</option>
            ))}
          </select>
        </label>

        <p className="muted">
          积分会从你的可用声誉中冻结；有效期内无人回答或无人被采纳时自动退回。
        </p>

        <div className="modal-actions">
          <button type="button" onClick={onCancel}>取消</button>
          <button type="button" className="primary" disabled={!valid} onClick={() => onConfirm({ points, days })}>
            确认悬赏 {points} 分
          </button>
        </div>
      </div>
    </div>
  );
}
