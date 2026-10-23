// Собирает реестр карточек партнёров в docs/08-partners/feature-cards.md.
// Использование: node partner-cards.mjs issues.json docs/08-partners/feature-cards.md
// issues.json — вывод `gh issue list --json number,title,body,labels,state,createdAt,url`.
import { readFileSync, writeFileSync } from 'node:fs';

const [issuesPath, docPath] = process.argv.slice(2);
const START = '<!-- PARTNER-CARDS:START -->';
const END = '<!-- PARTNER-CARDS:END -->';

const FIELDS = {
  org: 'Организация / автор',
  name: 'Название функции',
  problem: 'Какую проблему решает',
  role: 'Для кого',
  scenario: 'Как это должно работать',
  result: 'Ожидаемый результат',
  priority: 'Важность для вас',
  example: 'Пример или референс',
};

// Тело формы Issues: "### Поле\n\nзначение\n\n### Следующее поле …"
function parseBody(body = '') {
  const out = {};
  const parts = body.replace(/\r\n/g, '\n').split(/^###\s+/m).slice(1);
  for (const part of parts) {
    const nl = part.indexOf('\n');
    const label = (nl < 0 ? part : part.slice(0, nl)).trim();
    let value = nl < 0 ? '' : part.slice(nl + 1).trim();
    if (value === '_No response_' || value === 'None') value = '';
    out[label] = value;
  }
  const res = {};
  for (const [key, label] of Object.entries(FIELDS)) res[key] = out[label] || '';
  return res;
}

function status(issue) {
  const labels = (issue.labels || []).map(l => (typeof l === 'string' ? l : l.name));
  if (labels.includes('реализовано')) return '🚀 Реализована';
  if (labels.includes('решение: отклонено')) return '❌ Отклонена';
  if (labels.includes('решение: отложено')) return '⏸ Отложена';
  if (labels.includes('решение: принято')) return '✅ Принята';
  if (labels.includes('на рассмотрении')) return '👀 На рассмотрении';
  return String(issue.state).toUpperCase() === 'CLOSED' ? 'Закрыта' : '🆕 Новая';
}

const cell = s => (s || '—').replace(/\|/g, '\\|').replace(/\s*\n\s*/g, ' ').trim();
const date = iso => {
  const d = new Date(iso);
  return isNaN(d) ? '' : `${String(d.getUTCDate()).padStart(2, '0')}.${String(d.getUTCMonth() + 1).padStart(2, '0')}.${d.getUTCFullYear()}`;
};
const title = (issue, f) => f.name || issue.title.replace(/^\[Партнёр\]\s*/, '');

function render(issues) {
  if (!issues.length) return '_Карточек пока нет._';
  const sorted = [...issues].sort((a, b) => a.number - b.number);
  const rows = sorted.map(i => {
    const f = parseBody(i.body);
    return `| [#${i.number}](${i.url}) | ${cell(title(i, f))} | ${cell(f.org)} | ${cell(f.role)} | ${cell(f.priority)} | ${status(i)} | ${date(i.createdAt)} |`;
  });
  const cards = sorted.map(i => {
    const f = parseBody(i.body);
    const block = (h, v) => `**${h}**\n\n${(v || '—').trim()}\n`;
    return [
      `<details>`,
      `<summary><b>#${i.number} · ${cell(title(i, f))}</b> — ${cell(f.org)} · ${status(i)}</summary>`,
      ``,
      block('Какую проблему решает', f.problem),
      block('Для кого', f.role),
      block('Как это должно работать', f.scenario),
      block('Ожидаемый результат', f.result),
      block('Важность для партнёра', f.priority),
      block('Пример или референс', f.example),
      `[Обсуждение и решение →](${i.url})`,
      ``,
      `</details>`,
    ].join('\n');
  });
  return [
    `Всего карточек: **${sorted.length}**`,
    '',
    '| № | Функция | Партнёр | Для кого | Важность | Статус | Подана |',
    '|---|---|---|---|---|---|---|',
    ...rows,
    '',
    '### Карточки целиком',
    '',
    cards.join('\n\n'),
  ].join('\n');
}

const issues = JSON.parse(readFileSync(issuesPath, 'utf8'));
const doc = readFileSync(docPath, 'utf8');
const a = doc.indexOf(START), b = doc.indexOf(END);
if (a < 0 || b < a) throw new Error('В документе нет маркеров реестра');
const next = doc.slice(0, a + START.length) + '\n' + render(issues) + '\n' + doc.slice(b);
if (next !== doc) { writeFileSync(docPath, next); console.log('Реестр обновлён'); }
else console.log('Изменений нет');
