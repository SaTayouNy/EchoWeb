/**
 * «Эхо» — анкета фокус-групп в Google Формах.
 *
 * createFocusGroupForm()   — создаёт ДВЕ формы и выводит две ссылки:
 *                            1) основная анкета (рабочий сайт): код участника, UTM, регистрация;
 *                            2) демо-анкета для прохождения интерфейса на демонстрационных данных
 *                               до релиза: без кодов, без регистрации и без Метрики.
 * createParticipantLinks() — таблица ссылок для участников основной анкеты (анкета с кодом + сайт с UTM).
 *
 * Каждая форма создаётся ОДИН раз. Повторный запуск не создаёт новые формы,
 * а выводит ссылки на уже созданные — ссылки, которые вы раздали, не меняются.
 * Текст анкеты и инструкция: docs/09-research/focus-groups-form.md
 * Запуск: script.google.com → Новый проект → вставить файл → заполнить CONFIG → Выполнить.
 */

const CONFIG = {
  SITE_URL: 'https://ВАШ-ДОМЕН',               // адрес основного сайта, без «/» в конце
  CALIBRATION_FILM: 'Интерстеллар (2014)',      // задание 2
  PARTICIPANTS_PER_GROUP: 20,
  UTM_CAMPAIGN: 'fg-2026-11',
  DEMO_SITE_URL: '',                            // адрес демо-версии сайта; пусто — участнику откроет ведущий
};

const GROUPS = [
  { prefix: 'PR', label: 'Профессионалы кино', medium: 'pro' },
  { prefix: 'LB', label: 'Любители кино', medium: 'fan' },
];

const PARAMS = [
  'Драматургия и структура',
  'Персонажи и исполнение',
  'Визуальный язык',
  'Звук и музыка',
  'Впечатление',
];

const ASPECTS = [
  'Темп повествования', 'Концовка', 'Мотивация героев', 'Актёрская игра',
  'Операторская работа', 'Спецэффекты (VFX)', 'Костюмы и грим', 'Саундтрек',
  'Желание пересмотреть', 'Монтаж', 'Диалоги', 'Атмосфера', 'Режиссура',
  'Главная идея фильма',
];

// ---------- помощники ----------

function one_(form, title, choices, opts) {
  const item = form.addMultipleChoiceItem().setTitle(title).setChoiceValues(choices);
  return finish_(item, opts);
}

function many_(form, title, choices, opts) {
  const item = form.addCheckboxItem().setTitle(title).setChoiceValues(choices);
  return finish_(item, opts);
}

function scale_(form, title, upper, low, high, opts) {
  const item = form.addScaleItem().setTitle(title).setBounds(1, upper).setLabels(low, high);
  return finish_(item, opts);
}

function grid_(form, title, rows, columns, opts) {
  const item = form.addGridItem().setTitle(title).setRows(rows).setColumns(columns);
  return finish_(item, opts);
}

function text_(form, title, opts) {
  return finish_(form.addTextItem().setTitle(title), Object.assign({ required: false }, opts));
}

function para_(form, title, opts) {
  return finish_(form.addParagraphTextItem().setTitle(title), Object.assign({ required: false }, opts));
}

function finish_(item, opts) {
  const o = Object.assign({ required: true }, opts);
  item.setRequired(o.required);
  if (o.help) item.setHelpText(o.help);
  if (o.other) item.showOtherOption(true);
  return item;
}

function page_(form, title, help) {
  const p = form.addPageBreakItem().setTitle(title);
  if (help) p.setHelpText(help);
  return p;
}

function setGroupNavigation_(item, pagesByPrefix) {
  item.setChoices(GROUPS.map(g => item.createChoice(g.label, pagesByPrefix[g.prefix])));
}

// ---------- форма ----------

function createFocusGroupForm() {
  buildForm_(false);
  buildForm_(true);
}

function buildForm_(demo) {
  const key = demo ? 'DEMO_' : '';
  const label = demo ? '2. Демо-анкета' : '1. Основная анкета';
  const props = PropertiesService.getScriptProperties();
  const existing = props.getProperty(key + 'FORM_ID');
  if (existing) {
    const old = FormApp.openById(existing);
    Logger.log(label + ' уже создана, ссылка прежняя: ' + old.getPublishedUrl());
    Logger.log(label + ', редактирование: ' + old.getEditUrl());
    Logger.log('Чтобы создать новую форму, удалите свойство ' + key + 'FORM_ID: ' +
      'Настройки проекта → Свойства скрипта.');
    return;
  }

  const form = FormApp.create(demo
    ? '«Эхо» — знакомство с интерфейсом (демо-версия)'
    : '«Эхо» — тестирование оценки фильмов');
  form
    .setDescription(
      'Спасибо, что помогаете «Эхо» — новой платформе для осмысленных оценок и рецензий на кино. ' +
      (demo
        ? 'Это демонстрационная версия сайта: фильмы, оценки и рецензии в ней — примеры, ваши действия ' +
          'не сохраняются. Вы выполните несколько заданий и ответите на вопросы. Займёт 15–20 минут. '
        : 'Вы выполните несколько заданий на сайте и ответите на вопросы. Займёт 20–25 минут. ') +
      'Правильных и неправильных ответов нет: мы проверяем сайт, а не вас. ' +
      'Если что-то непонятно, значит, это нужно исправить нам.')
    .setCollectEmail(false)
    .setProgressBar(true)
    .setAllowResponseEdits(false)
    .setShowLinkToRespondAgain(true)
    .setConfirmationMessage(demo
      ? 'Спасибо! Ваши ответы помогут сделать «Эхо» удобнее.'
      : 'Спасибо! Ведущий подготовит компьютер для следующего участника.');

  // Раздел 0. Начало
  one_(form, 'Согласие на участие', ['Согласен(на) участвовать'], {
    help: demo
      ? 'Мы не собираем имя, почту и телефон. Ответы нужны только для улучшения «Эхо». ' +
        'Вы можете прекратить участие в любой момент.'
      : 'Мы не собираем имя, почту и телефон. Яндекс.Метрика записывает ваши действия на сайте ' +
        '(нажатия, прокрутку), но не содержимое полей. Данные нужны только для улучшения «Эхо». ' +
        'Вы можете прекратить участие в любой момент.',
  });
  let code = null;
  if (!demo) {
    code = text_(form, 'Код участника', { required: true, help: 'Заполнен заранее, не меняйте.' });
    code.setValidation(FormApp.createTextValidation()
      .requireTextMatchesPattern('^(PR|LB)[0-9]{2}$')
      .setHelpText('Формат кода: PR07 или LB12')
      .build());
  }
  const group1 = form.addMultipleChoiceItem().setRequired(true);
  if (demo) {
    group1.setTitle('Кто вы?').setHelpText('Профессионал — работаете или учитесь в кино.');
  } else {
    group1.setTitle('Группа').setHelpText('Заполнена заранее.');
  }

  // Раздел 1П. Профессионалы
  const pPro = page_(form, 'О вас');
  one_(form, 'Ваша основная область в кино', [
    'Сценарий и драматургия', 'Режиссура', 'Актёрская работа', 'Операторская работа', 'Монтаж',
    'Художник-постановщик, костюм, грим', 'VFX и анимация', 'Звук и музыка', 'Продюсирование',
    'Кинокритика, журналистика'], { other: true });
  one_(form, 'Ваш опыт в кино', ['Учусь', 'До 3 лет', '3–10 лет', 'Больше 10 лет']);

  // Раздел 1Л. Любители
  const pFan = page_(form, 'О вас');
  one_(form, 'Что для вас кино?', [
    'Отдых и развлечение', 'Увлечение: читаю и смотрю о кино',
    'Серьёзный интерес: изучаю кино сам(а), пишу о нём'], { other: true });

  // Раздел 2. Общее
  const pCommon = page_(form, 'О вас (продолжение)');
  pFan.setGoToPage(pCommon); // профессионалы после 1П пропускают 1Л
  one_(form, 'Ваш возраст', ['18–24', '25–34', '35–44', '45 и старше']);
  one_(form, 'Как часто вы смотрите фильмы?', ['Каждый день', 'Несколько раз в неделю', 'Раз в неделю', 'Реже']);
  many_(form, 'Где вы фиксируете или обсуждаете мнение о фильмах?', [
    'Кинопоиск', 'Letterboxd', 'IMDb', 'Соцсети, Telegram', 'Обсуждаю с друзьями устно', 'Нигде'], { other: true });
  one_(form, 'Как часто вы пишете отзывы или рецензии на фильмы?', [
    'Никогда', 'Реже раза в месяц', 'Несколько раз в месяц', 'Каждую неделю']);
  one_(form, 'С какого устройства вы проходите тест?', ['Компьютер или ноутбук', 'Планшет', 'Телефон']);

  setGroupNavigation_(group1, { PR: pPro, LB: pFan });

  // Раздел 3. Задание 1
  const openSite = demo && CONFIG.DEMO_SITE_URL
    ? 'Откройте демо-версию сайта «Эхо» в новой вкладке: ' + CONFIG.DEMO_SITE_URL + '. '
    : 'Перейдите на вкладку с сайтом «Эхо». ';
  page_(form, 'Задание 1 из 3. Первое впечатление',
    openSite + 'Посмотрите на главную страницу 15 секунд, ничего не нажимая. Затем вернитесь сюда.');
  para_(form, 'Своими словами: что это за сайт и зачем он нужен?', { required: true });
  scale_(form, 'Захотелось ли вам продолжить знакомство с сайтом?', 5, 'Совсем нет', 'Очень');

  // Раздел 4. Задание 2
  if (demo) {
    page_(form, 'Задание 2 из 3. Оценка фильма',
      'Выберите на сайте любой фильм из каталога и поставьте ему свою оценку. Если сайт предложит войти — ' +
      'войдите как зритель: в демо-версии вход имитируется, логин и пароль можно не вводить. ' +
      'Когда закончите, вернитесь сюда.');
    text_(form, 'Какой фильм вы оценили?');
  } else {
    page_(form, 'Задание 2 из 3. Оценка фильма',
      'Найдите на сайте фильм «' + CONFIG.CALIBRATION_FILM + '» и поставьте ему свою оценку. ' +
      'Если сайт попросит войти — зарегистрируйтесь (придумайте никнейм и пароль, почта не нужна). ' +
      'Если вы не смотрели этот фильм, оцените любой другой, который хорошо помните. Когда закончите, вернитесь сюда.');
    one_(form, 'Какой фильм вы оценили?', ['«' + CONFIG.CALIBRATION_FILM + '»'], { other: true });
  }
  one_(form, 'Удалось ли поставить оценку?', ['Да, без трудностей', 'Да, но с трудностями', 'Нет']);
  scale_(form, 'В целом, насколько сложно или легко было поставить оценку?', 7, 'Очень сложно', 'Очень легко');
  one_(form, 'Как быстро вы нашли, где ставить оценку на странице фильма?', [
    'Сразу', 'Пришлось прокрутить, но нашёл(ла) быстро', 'Искал(а) долго',
    demo ? 'Нашёл(ла) с чужой помощью' : 'Нашёл(ла) с помощью ведущего', 'Не нашёл(ла)']);
  one_(form, 'Где вы в первую очередь искали, как поставить оценку?', [
    'Рядом с постером и названием', 'Рядом с оценками фильма', 'Внизу страницы, после рецензий',
    'В меню или профиле'], { other: true });
  one_(form, 'Как вам число действий, которые понадобились для оценки?', [
    'Слишком много', 'В самый раз', 'Можно было бы и больше, если это даёт точность']);
  scale_(form, 'Насколько удобно выставлять значения ползунками?', 5, 'Очень неудобно', 'Очень удобно');
  if (!demo) {
    one_(form, 'Регистрация или вход', ['Прошли просто', 'Были вопросы', 'Было сложно', 'Не понадобились']);
  }
  para_(form, 'В какой момент вы засомневались, что делать, или что-то мешало?');

  // Раздел 5. Параметры
  page_(form, 'Параметры оценки',
    'Вспомните пять параметров, по которым вы только что оценивали фильм. Можно снова посмотреть на них на сайте.');
  grid_(form, 'Насколько понятно, что оценивать в каждом параметре?', PARAMS,
    ['1 — совсем непонятно', '2', '3', '4', '5 — полностью понятно']);
  grid_(form, 'К какому параметру вы бы отнесли каждый аспект фильма?', ASPECTS,
    PARAMS.concat(['Ни к одному', 'Сразу к нескольким']));
  scale_(form, 'Насколько оценка по параметрам передаёт ваше мнение лучше, чем одна общая цифра?', 5,
    'Хуже', 'Намного лучше', { help: '3 — так же' });
  scale_(form, 'Насколько итоговая цифра совпала с вашим ощущением от фильма, который вы оценили?', 5,
    'Совсем не совпала', 'Полностью совпала');
  // последний вопрос раздела: от ответа зависит, показывать ли раздел 5А
  const enough = form.addMultipleChoiceItem().setTitle('Хватает ли пяти параметров, чтобы описать фильм?')
    .setRequired(true);

  // Раздел 5А. Только для ответов «не хватает» и «слишком много»
  const pChange = page_(form, 'Параметры: что изменить');
  para_(form, 'Какого параметра не хватает или какой лишний? Почему?', { required: true });

  // Раздел 6. Задание 3
  const pReview = page_(form, 'Задание 3 из 3. Рецензия (по желанию)',
    'Если хотите, напишите короткую рецензию на фильм, который вы оценили в задании 2 (от 150 символов). ' +
    (demo ? 'В демо-версии рецензия никуда не отправится. ' : '') +
    'Это необязательно. Затем вернитесь сюда.');
  one_(form, 'Вы написали рецензию?', ['Да', 'Начал(а), но не закончил(а)', 'Нет']);
  many_(form, 'Что вас остановило или могло бы остановить от рецензии?', [
    'Нет времени сейчас', 'Не знаю, что написать', '150 символов — много', 'Не хочу писать публично',
    'Кажется, никто не прочитает', 'Неудобная форма', 'Ничего не остановило'], { other: true });
  many_(form, 'Что мотивировало бы вас писать рецензии регулярно?', [
    'Лайки и отклик других зрителей', 'Отклик создателя фильма', 'Статус и баллы на сайте',
    'Кино-дневник для себя', 'Подсказки, о чём написать', 'Публикации лучших рецензий в соцсетях «Эхо»',
    'Ничего'], { other: true });
  enough.setChoices([
    enough.createChoice('Да, полностью', pReview),
    enough.createChoice('В целом да', pReview),
    enough.createChoice('Не хватает чего-то важного', pChange),
    enough.createChoice('Параметров слишком много', pChange),
  ]);
  const group2 = form.addMultipleChoiceItem().setRequired(true);
  if (demo) {
    group2.setTitle('Вы создаёте фильмы — профессионально или учитесь?')
      .setHelpText('Если да, зададим ещё три вопроса с точки зрения автора.');
  } else {
    group2.setTitle('Группа').setHelpText('Заполнена заранее.');
  }

  // Раздел 7. Взгляд автора
  const pAuthor = page_(form, 'Взгляд автора',
    'Представьте, что на «Эхо» есть ваш фильм и зрители оценивают его по этим параметрам.');
  scale_(form, 'Насколько полезной была бы вам такая обратная связь?', 5, 'Бесполезна', 'Очень полезна');
  many_(form, 'Какие параметры были бы вам полезнее всего как автору?', PARAMS.concat(['Текст рецензии', 'Ни один']));
  para_(form, 'Чего не хватает, чтобы обратная связь была полезной создателю фильма?');

  // Раздел 8. Итог
  const pFinal = page_(form, 'Итог');
  scale_(form, 'Возможности «Эхо» соответствуют тому, что мне нужно', 7, 'Совсем не согласен(на)', 'Полностью согласен(на)');
  scale_(form, '«Эхо» легко пользоваться', 7, 'Совсем не согласен(на)', 'Полностью согласен(на)');
  scale_(form, 'Насколько вероятно, что вы будете пользоваться «Эхо» ' +
    (demo ? 'после запуска?' : 'после теста?'), 5, 'Точно нет', 'Точно да');
  para_(form, 'Что понравилось больше всего?');
  para_(form, 'Что в первую очередь стоит улучшить?');
  if (!demo) {
    text_(form, 'Ваш никнейм на «Эхо»', {
      help: 'Необязательно. Нужен, чтобы сопоставить вашу оценку. Не пишите настоящее имя.' });
  }

  if (demo) {
    group2.setChoices([group2.createChoice('Да', pAuthor), group2.createChoice('Нет', pFinal)]);
  } else {
    setGroupNavigation_(group2, { PR: pAuthor, LB: pFinal });
  }

  // Раздел 9. Для ведущего
  page_(form, 'Для ведущего', demo
    ? 'Если тест проходил с ведущим, передайте ему компьютер. Если ведущего не было, ответьте сами.'
    : 'Передайте компьютер ведущему. Спасибо за участие!');
  one_(form, 'Помогали ли вы участнику?', demo
    ? ['Нет', 'Один раз', 'Несколько раз', 'Ведущего не было']
    : ['Нет', 'Один раз', 'Несколько раз']);
  para_(form, 'Где участник застрял или просил помощи?');
  many_(form, 'Были ли технические сбои?', demo
    ? ['Не было', 'Сайт не открывался или долго грузился', 'Ошибка при сохранении оценки',
      'Ошибка при отправке рецензии']
    : ['Не было', 'Сайт не открывался или долго грузился', 'Ошибка при регистрации',
      'Ошибка при сохранении оценки', 'Ошибка при отправке рецензии'], { other: true });

  // Таблица ответов
  const ss = SpreadsheetApp.create(demo ? '«Эхо» — ответы (демо-версия)' : '«Эхо» — ответы фокус-групп');
  form.setDestination(FormApp.DestinationType.SPREADSHEET, ss.getId());

  const saved = {};
  saved[key + 'FORM_ID'] = form.getId();
  if (!demo) {
    saved.CODE_ITEM = String(code.getId());
    saved.GROUP_ITEM_1 = String(group1.getId());
    saved.GROUP_ITEM_2 = String(group2.getId());
  }
  props.setProperties(saved);

  Logger.log(label + ' — для участников: ' + form.getPublishedUrl());
  Logger.log(label + ' — редактирование: ' + form.getEditUrl());
  Logger.log(label + ' — таблица ответов: ' + ss.getUrl());
}

// ---------- ссылки участников ----------

function createParticipantLinks() {
  if (CONFIG.SITE_URL.indexOf('ВАШ-ДОМЕН') !== -1) {
    throw new Error('Укажите адрес сайта в CONFIG.SITE_URL');
  }
  const props = PropertiesService.getScriptProperties();
  const formId = props.getProperty('FORM_ID');
  if (!formId) throw new Error('Сначала запустите createFocusGroupForm');

  const form = FormApp.openById(formId);
  const codeItem = form.getItemById(Number(props.getProperty('CODE_ITEM'))).asTextItem();
  const group1 = form.getItemById(Number(props.getProperty('GROUP_ITEM_1'))).asMultipleChoiceItem();
  const group2 = form.getItemById(Number(props.getProperty('GROUP_ITEM_2'))).asMultipleChoiceItem();

  const rows = [];
  GROUPS.forEach(g => {
    for (let i = 1; i <= CONFIG.PARTICIPANTS_PER_GROUP; i++) {
      const id = g.prefix + String(i).padStart(2, '0');
      const formUrl = form.createResponse()
        .withItemResponse(codeItem.createResponse(id))
        .withItemResponse(group1.createResponse(g.label))
        .withItemResponse(group2.createResponse(g.label))
        .toPrefilledUrl();
      const siteUrl = CONFIG.SITE_URL + '/?utm_source=focusgroup&utm_medium=' + g.medium +
        '&utm_campaign=' + CONFIG.UTM_CAMPAIGN + '&utm_content=' + id;
      rows.push([id, g.label, formUrl, siteUrl, '', false, '']);
    }
  });

  const ss = SpreadsheetApp.create('«Эхо» — ссылки участников фокус-групп');
  const sheet = ss.getActiveSheet().setName('Участники');
  sheet.getRange(1, 1, 1, 7).setValues([[
    'Код', 'Группа', '1. Анкета', '2. Сайт', 'Дата сессии', 'Проведено', 'Комментарий ведущего']])
    .setFontWeight('bold');
  sheet.getRange(2, 1, rows.length, 7).setValues(rows);
  sheet.getRange(2, 6, rows.length, 1).insertCheckboxes();
  sheet.setFrozenRows(1);
  sheet.autoResizeColumns(1, 2);

  Logger.log('Таблица ссылок: ' + ss.getUrl());
}
