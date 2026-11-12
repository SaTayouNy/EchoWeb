/**
 * Демо-режим панели: тот же контракт API, что в BACKEND.md, но данные в памяти браузера.
 * Ничего не отправляется на сервер. Данные вымышленные.
 */
export function createDemoApi() {
  const now = Date.now(), h = 36e5;
  const iso = hoursAgo => new Date(now - hoursAgo * h).toISOString();
  let seq = 100;
  const uid = () => `00000000-0000-4000-8000-${String(++seq).padStart(12, '0')}`;
  const me = { userId: uid(), username: 'roman_pm', role: 'ADMIN', isActive: true, createdAt: iso(2400) };

  const mk = (username, createdHours, extra = {}) => ({
    userId: uid(), username, email: null, role: 'USER', isActive: true, createdAt: iso(createdHours),
    checkedAt: null, checkedBy: null, blockedReason: null, media: false, author: false, bio: '', avatarUrl: null,
    stats: { ratings: 0, reviews: 0, receivedLikes: 0 }, ...extra,
  });
  const users = [
    mk('kinoman_ekb', 3, { stats: { ratings: 4, reviews: 1, receivedLikes: 0 }, bio: 'Смотрю всё подряд, люблю Тарковского' }),
    mk('anna_frames', 9, { stats: { ratings: 12, reviews: 2, receivedLikes: 3 } }),
    mk('vlad_2007', 20, { stats: { ratings: 1, reviews: 1, receivedLikes: 0 } }),
    mk('xXdestroyerXx', 30, { stats: { ratings: 0, reviews: 0, receivedLikes: 0 } }),
    mk('lena.cinema', 46, { stats: { ratings: 7, reviews: 0, receivedLikes: 0 }, email: 'lena@example.com' }),
    mk('student_vgik', 70, { stats: { ratings: 3, reviews: 1, receivedLikes: 1 } }),
    mk('masha_reviews', 300, { checkedAt: iso(250), checkedBy: 'roman_pm', stats: { ratings: 31, reviews: 9, receivedLikes: 27 }, media: true, bio: 'Пишу о кино в Telegram-канале' }),
    mk('igor_k', 500, { checkedAt: iso(480), checkedBy: 'roman_pm', stats: { ratings: 15, reviews: 3, receivedLikes: 6 } }),
    mk('spam_bot_77', 200, { isActive: false, checkedAt: iso(190), checkedBy: 'roman_pm', blockedReason: 'Реклама в рецензиях' }),
  ];
  const byName = n => users.find(u => u.username === n);

  const film = (title, director, tmdbId) => ({ mediaType: 'MOVIE', tmdbId, title, director, posterUrl: null });
  const rv = (user, f, title, content, params, total, status, createdHours, extra = {}) => ({
    reviewId: uid(), userId: byName(user).userId, media: f, title, content, likes: 0, status,
    rating: { parameters: Object.fromEntries(['dramaturgy_structure', 'characters_performance', 'visual_language', 'sound_music', 'impression'].map((k, i) => [k, params[i]])), finalScore: total },
    createdAt: iso(createdHours), updatedAt: iso(createdHours), publishedAt: status === 'PUBLISHED' ? iso(createdHours - 1) : null, ...extra,
  });
  const stalker = film('Сталкер', 'Андрей Тарковский', 1398), parasite = film('Паразиты', 'Пон Джун-хо', 496243),
    dune = film('Дюна: Часть вторая', 'Дени Вильнёв', 693134), brat = film('Брат', 'Алексей Балабанов', 20992), solaris = film('Солярис', 'Андрей Тарковский', 593);
  const long = 'Фильм долго раскачивается, и первые сорок минут кажутся медленными, но это осознанный выбор. Камера почти не двигается, звук выстроен вокруг тишины и капающей воды, и к середине понимаешь, что именно это состояние и есть сюжет. ';
  const reviews = [
    rv('kinoman_ekb', stalker, 'Тишина как главный герой', long + 'Актёры играют сдержанно, Кайдановский держит каждую сцену взглядом. Визуально это одна из самых цельных работ: зелёный цвет Зоны против сепии города работает лучше любых объяснений. Финальная сцена со стаканом оставляет вопрос открытым, и это правильно.', [9, 9, 10, 9, 10], 94, 'PENDING_MODERATION', 30),
    rv('vlad_2007', dune, 'Норм', 'Красиво, но долго. Музыка громкая. В целом норм, смотреть можно.', [6, 6, 9, 8, 6], 64, 'PENDING_MODERATION', 18),
    rv('anna_frames', parasite, 'Про лестницы и запахи', 'Пон Джун-хо строит фильм вертикально: лестницы, полуподвал, дом на холме. ' + long + 'Сцена с ливнем — лучшее, что я видела в кино за год. В финале оказывается, что отец так и остаётся в подвале, и это делает фильм по-настоящему жестоким.', [10, 9, 10, 8, 9], 91, 'PENDING_MODERATION', 9),
    rv('student_vgik', brat, 'Почему это до сих пор работает', 'Балабанов снимает Петербург как чужой город для героя, и мы видим его глазами Данилы. Да, режиссёр местами — полный дебил в обращении со вторыми ролями, но ' + long + 'Музыка Наутилуса работает как внутренний голос героя.', [8, 7, 7, 10, 8], 79, 'PENDING_MODERATION', 4),
    rv('anna_frames', solaris, 'Океан памяти', long + 'Солярис — фильм о том, как мы не можем отпустить прошлое. Баньонис играет усталость лучше всех.', [8, 9, 9, 9, 8], 84, 'PENDING_MODERATION', 2),
    rv('masha_reviews', stalker, 'Зона внутри', long + 'Пересматриваю раз в год и каждый раз вижу новое.', [10, 10, 10, 9, 10], 98, 'PUBLISHED', 120, { likes: 14 }),
    rv('igor_k', parasite, 'Жанровая машина', long + 'Сначала комедия, потом триллер, потом драма — и всё это без швов.', [9, 9, 9, 8, 9], 88, 'PUBLISHED', 200, { likes: 6 }),
    rv('spam_bot_77', dune, 'Смотри бесплатно', 'Смотрите все фильмы бесплатно на нашем сайте, переходите по ссылке в профиле!!!', [10, 10, 10, 10, 10], 100, 'REJECTED', 190, { rejectReason: 'SPAM', rejectComment: '' }),
  ];
  const authors = [
    { applicationId: uid(), userId: uid(), username: 'd.sokolov', name: 'Дмитрий Соколов', specialty: 'Режиссёр', status: 'PENDING', createdAt: iso(26), links: ['https://www.kinopoisk.ru/name/0000000/'], films: ['«Обратная сторона» (2025, короткий метр)'], note: 'Выпускник киношколы, работаю со студией' },
    { applicationId: uid(), userId: uid(), username: 'ksenia_dop', name: 'Ксения Белова', specialty: 'Оператор', status: 'PENDING', createdAt: iso(5), links: [], films: [], note: '' },
    { applicationId: uid(), userId: uid(), username: 'a.morozov', name: 'Алексей Морозов', specialty: 'Сценарист', status: 'VERIFIED', createdAt: iso(400), decidedAt: iso(380), decidedBy: 'roman_pm', links: ['https://example.com/portfolio'], films: ['«Тихий двор» (2024)'] },
    { applicationId: uid(), userId: uid(), username: 'fake_spielberg', name: 'Стивен Спилберг', specialty: 'Режиссёр', status: 'REJECTED', createdAt: iso(300), decidedAt: iso(290), decidedBy: 'roman_pm', reason: 'Не подтверждено' },
  ];
  const audit = [
    { id: uid(), at: iso(190), actor: { username: 'roman_pm' }, action: 'REVIEW_REJECTED', targetLabel: '«Смотри бесплатно» — @spam_bot_77', reason: 'SPAM' },
    { id: uid(), at: iso(189), actor: { username: 'roman_pm' }, action: 'USER_BLOCKED', targetLabel: '@spam_bot_77', reason: 'Реклама в рецензиях' },
    { id: uid(), at: iso(119), actor: { username: 'roman_pm' }, action: 'REVIEW_PUBLISHED', targetLabel: '«Зона внутри» — @masha_reviews' },
    { id: uid(), at: iso(290), actor: { username: 'roman_pm' }, action: 'AUTHOR_REJECTED', targetLabel: 'Стивен Спилберг', reason: 'Не подтверждено' },
  ];
  const log = (action, targetLabel, reason) => audit.unshift({ id: uid(), at: new Date().toISOString(), actor: { username: me.username }, action, targetLabel, reason });

  const page = (items, query) => {
    const limit = Number(query.get('limit') || 20), offset = Number(query.get('offset') || 0);
    return { items: items.slice(offset, offset + limit), meta: { limit, offset, total: items.length } };
  };
  const userState = u => !u.isActive ? 'BLOCKED' : u.checkedAt ? 'CHECKED' : 'NEW';
  const fail = (status, message) => { const e = new Error(message); e.status = status; throw e; };
  const label = r => `«${r.title}» — @${users.find(u => u.userId === r.userId)?.username}`;
  const clone = x => JSON.parse(JSON.stringify(x));

  return async function demoApi(method, fullPath, body) {
    await new Promise(r => setTimeout(r, 120));
    const url = new URL(fullPath, 'https://demo.local'); const p = url.pathname; const qy = url.searchParams;
    let m;
    if (p === '/v1/auth/login' && method === 'POST') return { accessToken: 'demo', refreshToken: 'demo' };
    if (p === '/v1/me') return clone(me);
    if (p === '/v2/admin/stats') {
      const pend = reviews.filter(r => r.status === 'PENDING_MODERATION').sort((a, b) => a.createdAt.localeCompare(b.createdAt));
      const week = r => r.updatedAt > new Date(now - 7 * 24 * h).toISOString();
      return { pendingReviews: pend.length, oldestPendingAt: pend[0]?.createdAt || null, newUsers: users.filter(u => userState(u) === 'NEW').length,
        pendingAuthors: authors.filter(a => a.status === 'PENDING').length,
        published7d: reviews.filter(r => r.status === 'PUBLISHED' && week(r)).length, rejected7d: reviews.filter(r => r.status === 'REJECTED' && week(r)).length };
    }
    if (p === '/v2/reviews/moderation') return clone(page(reviews.filter(r => r.status === 'PENDING_MODERATION').sort((a, b) => a.createdAt.localeCompare(b.createdAt)), qy));
    if (p === '/v2/admin/reviews') return clone(page(reviews.filter(r => r.status === qy.get('status')).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)), qy));
    if ((m = p.match(/^\/v2\/reviews\/([^/]+)\/publish$/))) {
      const r = reviews.find(x => x.reviewId === m[1]) || fail(404, 'Рецензия не найдена');
      Object.assign(r, { status: 'PUBLISHED', publishedAt: new Date().toISOString(), updatedAt: new Date().toISOString(), rejectReason: null }); log('REVIEW_PUBLISHED', label(r)); return clone(r);
    }
    if ((m = p.match(/^\/v2\/admin\/reviews\/([^/]+)\/(reject|unpublish)$/))) {
      const r = reviews.find(x => x.reviewId === m[1]) || fail(404, 'Рецензия не найдена');
      Object.assign(r, { status: 'REJECTED', publishedAt: null, updatedAt: new Date().toISOString(), rejectReason: body?.reason, rejectComment: body?.comment });
      log(m[2] === 'reject' ? 'REVIEW_REJECTED' : 'REVIEW_UNPUBLISHED', label(r), body?.reason); return clone(r);
    }
    if (p === '/v2/admin/users') return clone(page(users.filter(u => userState(u) === qy.get('state')).sort((a, b) => b.createdAt.localeCompare(a.createdAt)), qy));
    if ((m = p.match(/^\/v2\/admin\/users\/([^/]+)$/))) {
      const u = users.find(x => x.userId === m[1]) || fail(404, 'Пользователь не найден');
      return clone({ ...u, recentReviews: reviews.filter(r => r.userId === u.userId).slice(0, 5) });
    }
    if ((m = p.match(/^\/v1\/users\/([^/]+)$/))) {
      const u = users.find(x => x.userId === m[1]) || fail(404, 'Пользователь не найден');
      return { userId: u.userId, username: u.username, avatarUrl: u.avatarUrl, bio: u.bio };
    }
    if ((m = p.match(/^\/v2\/admin\/users\/([^/]+)\/(check|block|unblock|username|media|role)$/))) {
      const u = users.find(x => x.userId === m[1]) || fail(404, 'Пользователь не найден');
      const at = new Date().toISOString();
      if (m[2] === 'check') { Object.assign(u, { checkedAt: at, checkedBy: me.username }); log('USER_CHECKED', '@' + u.username); }
      if (m[2] === 'block') { Object.assign(u, { isActive: false, blockedReason: body?.reason, checkedAt: u.checkedAt || at, checkedBy: u.checkedBy || me.username }); log('USER_BLOCKED', '@' + u.username, body?.reason); }
      if (m[2] === 'unblock') { Object.assign(u, { isActive: true, blockedReason: null }); log('USER_UNBLOCKED', '@' + u.username); }
      if (m[2] === 'username') {
        const name = String(body?.username || '').trim();
        if (!/^[\p{L}\p{N}._-]{3,40}$/u.test(name)) fail(400, 'Ник: 3–40 символов, буквы, цифры, точка, дефис, подчёркивание');
        if (users.some(x => x.username.toLowerCase() === name.toLowerCase() && x !== u)) fail(409, 'Такой ник уже занят');
        log('USER_RENAMED', `@${u.username} → @${name}`); u.username = name;
      }
      if (m[2] === 'media') { u.media = !!body?.enabled; log(u.media ? 'USER_MEDIA_ON' : 'USER_MEDIA_OFF', '@' + u.username); }
      if (m[2] === 'role') { u.role = body?.role; log('USER_ROLE', `@${u.username} → ${u.role}`); }
      return clone(u);
    }
    if (p === '/v2/admin/authors') return clone(page(authors.filter(a => a.status === qy.get('status')).sort((a, b) => b.createdAt.localeCompare(a.createdAt)), qy));
    if ((m = p.match(/^\/v2\/admin\/authors\/([^/]+)\/(verify|reject|request-info)$/))) {
      const a = authors.find(x => x.applicationId === m[1]) || fail(404, 'Заявка не найдена');
      const at = new Date().toISOString();
      if (m[2] === 'verify') { Object.assign(a, { status: 'VERIFIED', decidedAt: at, decidedBy: me.username, reason: null }); log('AUTHOR_VERIFIED', a.name); }
      if (m[2] === 'reject') { Object.assign(a, { status: 'REJECTED', decidedAt: at, decidedBy: me.username, reason: body?.reason }); log('AUTHOR_REJECTED', a.name, body?.reason); }
      if (m[2] === 'request-info') { a.note = `Запрошено подтверждение: ${body?.comment || ''}`; log('AUTHOR_INFO_REQUESTED', a.name); }
      return clone(a);
    }
    if (p === '/v2/admin/audit') return clone(page(audit, qy));
    fail(404, 'Not found');
  };
}
