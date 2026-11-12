/**
 * Эхо — форма записи на закрытый бета-тест.
 *
 * Как запустить (5 минут):
 * 1. Откройте https://script.google.com → «Новый проект».
 * 2. Удалите всё в редакторе и вставьте этот файл целиком.
 * 3. Вверху выберите функцию createBetaSignupForm → «Выполнить».
 *    Google попросит разрешение на создание форм и таблиц — разрешите.
 * 4. Откройте «Журнал выполнения»: там ссылки на форму, редактор формы,
 *    таблицу ответов и три ссылки для разных каналов (колледж, студия, знакомые).
 *
 * Повторный запуск не создаёт вторую форму, а снова печатает ссылки.
 * Чтобы создать форму заново, выполните resetBetaSignupForm, затем createBetaSignupForm.
 */

var FORM_TITLE = 'Эхо — запись на закрытый бета-тест';
var CHANNELS = ['Колледж', 'Киностудия', 'Знакомые', 'Другое'];

function createBetaSignupForm() {
  var props = PropertiesService.getScriptProperties();
  var existingId = props.getProperty('BETA_FORM_ID');
  if (existingId) {
    try {
      printLinks_(FormApp.openById(existingId));
      return;
    } catch (e) {
      props.deleteProperty('BETA_FORM_ID'); // форму удалили вручную — создаём новую
    }
  }

  var form = FormApp.create(FORM_TITLE);
  form.setDescription(
    '«Эхо» — платформа, где фильм оценивают по параметрам качества отдельно от личного впечатления ' +
    'и пишут рецензии, а авторы фильмов видят обратную связь.\n\n' +
    'Мы открываем сайт небольшими группами. Запишитесь — и мы напишем вам в Telegram, когда откроем доступ. ' +
    'Бета длится 2–3 недели: вы пользуетесь сайтом как обычно и рассказываете нам, что сломано или непонятно.\n\n' +
    'Заполнение займёт 2 минуты.'
  );
  form.setCollectEmail(false);
  form.setLimitOneResponsePerUser(false); // не требуем входа в Google-аккаунт
  form.setAllowResponseEdits(false);
  form.setShowLinkToRespondAgain(false);
  form.setProgressBar(false);
  form.setConfirmationMessage(
    'Спасибо, вы в списке! Мы напишем вам в Telegram, когда откроем доступ для вашей группы. ' +
    'Пожалуйста, не пересылайте ссылку на сайт, если она к вам попадёт раньше, — доступ выдаём по очереди.'
  );

  // 1. Контакты
  form.addSectionHeaderItem().setTitle('Как с вами связаться');
  form.addTextItem()
    .setTitle('Как к вам обращаться')
    .setHelpText('Имя или ник — как вам удобнее')
    .setRequired(true);

  var tgValidation = FormApp.createTextValidation()
    .requireTextMatchesPattern('^@?[A-Za-z0-9_]{5,32}$')
    .setHelpText('Укажите ник в Telegram, например @echo_movies')
    .build();
  form.addTextItem()
    .setTitle('Ваш ник в Telegram')
    .setHelpText('Сюда пришлём приглашение в группу бета-теста')
    .setValidation(tgValidation)
    .setRequired(true);

  var channelItem = form.addMultipleChoiceItem()
    .setTitle('Откуда вы узнали о бета-тесте')
    .setChoiceValues(CHANNELS)
    .setRequired(true);

  // 2. О вас и кино
  form.addSectionHeaderItem().setTitle('Вы и кино');
  form.addCheckboxItem()
    .setTitle('Как вы связаны с кино')
    .setHelpText('Можно выбрать несколько')
    .setChoiceValues([
      'Смотрю фильмы для себя',
      'Пишу о кино: рецензии, блог, канал',
      'Учусь кино',
      'Работаю в кино: режиссёр, сценарист, оператор и т. д.',
      'Снимаю свои фильмы'
    ])
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('Как часто вы смотрите фильмы')
    .setChoiceValues(['Несколько раз в неделю', 'Примерно раз в неделю', 'Пару раз в месяц', 'Реже'])
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('Делитесь ли вы мнением о фильмах')
    .setChoiceValues([
      'Регулярно пишу рецензии или отзывы',
      'Ставлю оценки, но редко пишу',
      'Обсуждаю с друзьями, но нигде не пишу',
      'Обычно нет'
    ])
    .setRequired(true);

  form.addCheckboxItem()
    .setTitle('Где вы сейчас оцениваете фильмы')
    .setHelpText('Можно выбрать несколько')
    .setChoiceValues(['Кинопоиск', 'Letterboxd', 'IMDb', 'Telegram-каналы или соцсети', 'Нигде'])
    .showOtherOption(true)
    .setRequired(true);

  // 3. Участие
  form.addSectionHeaderItem().setTitle('Участие в бета-тесте');
  form.addCheckboxItem()
    .setTitle('С каких устройств будете заходить')
    .setChoiceValues(['Компьютер или ноутбук', 'Планшет', 'Телефон'])
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('Готовы ли вы заходить на сайт 2–3 раза в неделю в течение 2–3 недель и писать нам об ошибках')
    .setChoiceValues(['Да', 'Скорее да', 'Пока не уверен(а)'])
    .setRequired(true);

  form.addParagraphTextItem()
    .setTitle('Чего вы ждёте от сайта о кино')
    .setHelpText('Необязательно, но нам очень поможет');

  // 4. Согласие
  form.addCheckboxItem()
    .setTitle('Согласие на обработку данных')
    .setHelpText(
      'Мы используем имя или ник и Telegram только чтобы пригласить вас в бета-тест и связаться по нему. ' +
      'Данные не передаются третьим лицам и удаляются после окончания бета-теста. ' +
      'Вопросы и отзыв согласия: support@echomovies.ru'
    )
    .setChoiceValues(['Согласен(на) на обработку указанных данных для участия в бета-тесте'])
    .setRequired(true);

  // Таблица ответов
  var sheet = SpreadsheetApp.create(FORM_TITLE + ' (ответы)');
  form.setDestination(FormApp.DestinationType.SPREADSHEET, sheet.getId());

  props.setProperty('BETA_FORM_ID', form.getId());
  props.setProperty('BETA_SHEET_ID', sheet.getId());
  props.setProperty('BETA_CHANNEL_ITEM_ID', String(channelItem.getId()));
  printLinks_(form);
}

function printLinks_(form) {
  var props = PropertiesService.getScriptProperties();
  var sheetId = props.getProperty('BETA_SHEET_ID');
  var channelItem = form.getItemById(Number(props.getProperty('BETA_CHANNEL_ITEM_ID'))).asMultipleChoiceItem();

  Logger.log('Форма (ссылка для участников): ' + form.getPublishedUrl());
  Logger.log('Редактор формы: ' + form.getEditUrl());
  if (sheetId) Logger.log('Таблица ответов: https://docs.google.com/spreadsheets/d/' + sheetId + '/edit');
  Logger.log('— Ссылки по каналам (канал уже отмечен в форме) —');
  CHANNELS.slice(0, 3).forEach(function (channel) {
    var url = form.createResponse()
      .withItemResponse(channelItem.createResponse(channel))
      .toPrefilledUrl();
    Logger.log(channel + ': ' + url);
  });
}

function resetBetaSignupForm() {
  var props = PropertiesService.getScriptProperties();
  ['BETA_FORM_ID', 'BETA_SHEET_ID', 'BETA_CHANNEL_ITEM_ID'].forEach(function (k) { props.deleteProperty(k); });
  Logger.log('Сброшено. Старая форма осталась в Google Диске — удалите её вручную, если не нужна.');
}
