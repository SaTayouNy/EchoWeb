// Демо-данные. Используются, пока не задан VITE_API_URL (см. README.md и API.md).
// Формат объектов здесь = формат ответов API, который ожидает интерфейс.
import {avatars,posters} from './placeholders.js';

const MOVIES=[
 {id:0,title:'Дюна: Часть вторая',year:2024,genre:'Фантастика',director:'Дени Вильнёв',country:'США, Канада',poster:posters[0],desc:'Пол Атрейдес объединяется с Чани и фрименами, чтобы отомстить за свою семью и сделать выбор между любовью и судьбой целой вселенной.',reviews:1284,ratings:18420,preferred:12482,total:86,parts:[8,7,10,9,9],authorComment:{author:'Дени Вильнёв',role:'Режиссёр',avatar:avatars[3],text:'Мне было важно, чтобы масштаб мира никогда не вытеснял личный выбор героя. Тишина здесь работает так же сильно, как действие.',likes:1482,date:'18 сентября 2026'}},
 {id:1,title:'Интерстеллар',year:2014,genre:'Фантастика',director:'Кристофер Нолан',country:'США, Великобритания',poster:posters[1],desc:'Группа исследователей отправляется через червоточину в космосе, чтобы найти новый дом для человечества.',reviews:3421,ratings:46211,preferred:22391,total:92,parts:[9,9,10,10,9],authorComment:null},
 {id:2,title:'Семь',year:1995,genre:'Триллер',director:'Дэвид Финчер',country:'США',poster:posters[2],desc:'Два детектива преследуют серийного убийцу, строящего преступления вокруг семи смертных грехов.',reviews:982,ratings:11882,preferred:7314,total:88,parts:[9,8,9,8,9],authorComment:null},
 {id:3,title:'Начало',year:2010,genre:'Фантастика',director:'Кристофер Нолан',country:'США, Великобритания',poster:posters[3],desc:'Профессиональный вор проникает в сны людей, чтобы украсть или внедрить идеи, меняющие реальность.',reviews:2602,ratings:35902,preferred:18660,total:90,parts:[9,8,10,9,9],authorComment:null},
 {id:4,title:'Паразиты',year:2019,genre:'Драма',director:'Пон Джун-хо',country:'Южная Корея',poster:posters[4],desc:'История двух семей из разных социальных слоёв превращается в напряжённую и едкую социальную сатиру.',reviews:1750,ratings:24010,preferred:15304,total:94,parts:[10,9,9,9,10],authorComment:null},
 {id:5,title:'Тёмный рыцарь',year:2008,genre:'Боевик',director:'Кристофер Нолан',country:'США',poster:posters[5],desc:'Бэтмен сталкивается с Джокером, который превращает Готэм в испытание для морали, порядка и личного выбора.',reviews:3912,ratings:55690,preferred:30211,total:93,parts:[9,10,9,9,10],authorComment:null}
];

const SCORE_SETS={
  0:{reviews:{total:86,parts:[8,7,10,9,9]},ratings:{total:83,parts:[8,8,10,8,9]},critics:{total:91,parts:[9,8,10,9,10]}},
  1:{reviews:{total:92,parts:[9,9,10,10,9]},ratings:{total:89,parts:[9,8,10,9,9]},critics:{total:84,parts:[8,8,9,9,8]}},
  2:{reviews:{total:88,parts:[9,8,9,8,9]},ratings:{total:86,parts:[8,8,9,8,9]},critics:{total:null,parts:[]}},
  3:{reviews:{total:90,parts:[9,8,10,9,9]},ratings:{total:88,parts:[8,8,10,9,9]},critics:{total:85,parts:[8,8,9,9,9]}},
  4:{reviews:{total:94,parts:[10,9,9,9,10]},ratings:{total:90,parts:[9,9,9,9,9]},critics:{total:96,parts:[10,9,10,9,10]}},
  5:{reviews:{total:93,parts:[9,10,9,9,10]},ratings:{total:92,parts:[9,9,9,9,10]},critics:{total:88,parts:[9,9,9,8,9]}}
};
MOVIES.forEach(m=>m.scoreSets=SCORE_SETS[m.id]);

// Дополнительные авторские комментарии для главной и заполненных карточек.
// При этом у «Интерстеллара», «Семи» и «Тёмного рыцаря» остаётся состояние без комментария.
MOVIES[0].authorComment.title='О ритме и масштабе';
MOVIES[3].authorComment={title:'Архитектура сна',author:'Кристофер Нолан',role:'Режиссёр',avatar:avatars[1],text:'Мне всегда была интересна идея пространства сна как архитектуры, которую можно строить и разрушать.',likes:920,date:'16 сентября 2026'};
MOVIES[4].authorComment={title:'Юмор рядом с тревогой',author:'Пон Джун-хо',role:'Режиссёр',avatar:avatars[4],text:'Юмор и тревога для меня существуют рядом — именно так социальная реальность становится особенно заметной.',likes:741,date:'14 сентября 2026'};



const AUTHOR_WORKS=[
 {id:'aw0',title:'Дюна: Часть вторая',year:2024,genre:'Фантастика',director:'Дени Вильнёв',role:'Режиссёр',poster:posters[0],reviews:1284,ratings:18420,popularity:12482,scores:{reviews:86,ratings:83,critics:91}},
 {id:'aw1',title:'Прибытие',year:2016,genre:'Фантастика',director:'Дени Вильнёв',role:'Режиссёр',poster:posters[1],reviews:942,ratings:12840,popularity:8421,scores:{reviews:89,ratings:88,critics:92}},
 {id:'aw2',title:'Пленницы',year:2013,genre:'Триллер',director:'Дени Вильнёв',role:'Режиссёр',poster:posters[2],reviews:711,ratings:9012,popularity:6310,scores:{reviews:88,ratings:86,critics:90}},
 {id:'aw3',title:'Бегущий по лезвию 2049',year:2017,genre:'Фантастика',director:'Дени Вильнёв',role:'Режиссёр',poster:posters[3],reviews:1190,ratings:16770,popularity:11030,scores:{reviews:91,ratings:89,critics:93}}
];
const PEOPLE={
 directors:[['Дени Вильнёв',avatars[3]],['Кристофер Нолан',avatars[1]],['Дэвид Финчер',avatars[5]],['Пон Джун-хо',avatars[4]],['Уэс Андерсон',avatars[0]]],
 actors:[['Тимоти Шаламе',avatars[4]],['Зендея',avatars[2]],['Тильда Суинтон',avatars[0]],['Хоакин Феникс',avatars[3]],['Флоренс Пью',avatars[5]]]
};
const CREW={
 actors:[['Тимоти Шаламе','Пол Атрейдес',avatars[4]],['Зендея','Чани',avatars[2]],['Ребекка Фергюсон','Леди Джессика',avatars[0]],['Хавьер Бардем','Стилгар',avatars[3]],['Флоренс Пью','Принцесса Ирулан',avatars[5]],['Остин Батлер','Фейд-Раута',avatars[1]]],
 team:[['Дени Вильнёв','Режиссёр',avatars[3]],['Грег Фрейзер','Оператор',avatars[1]],['Ханс Циммер','Композитор',avatars[5]],['Джо Уокер','Монтаж',avatars[4]],['Патрис Верметт','Художник',avatars[2]]]
};
const AUTHOR_PROFILES={
 'Дени Вильнёв':{
   image:avatars[3],specialty:'Режиссёр · сценарист · продюсер',verified:true,
   authorRatings:31,comments:14,authorLikes:612,preferred:48210,scores:{reviews:89,ratings:87,critics:92},
   works:AUTHOR_WORKS
 },
 'Тимоти Шаламе':{
   image:avatars[4],specialty:'Актёр',verified:true,authorRatings:12,comments:4,preferred:32920,
   scores:{reviews:88,ratings:86,critics:90},
   works:[{id:'tc0',title:'Дюна: Часть вторая',year:2024,genre:'Фантастика',director:'Дени Вильнёв',role:'Пол Атрейдес',poster:posters[0],reviews:1284,ratings:18420,popularity:12482,scores:{reviews:86,ratings:83,critics:91}}]
 },
 'Зендея':{
   image:avatars[2],specialty:'Актриса',verified:true,authorRatings:10,comments:3,preferred:37410,
   scores:{reviews:89,ratings:87,critics:91},
   works:[{id:'z0',title:'Дюна: Часть вторая',year:2024,genre:'Фантастика',director:'Дени Вильнёв',role:'Чани',poster:posters[0],reviews:1284,ratings:18420,popularity:12482,scores:{reviews:86,ratings:83,critics:91}}]
 },
 'Ребекка Фергюсон':{
   image:avatars[0],specialty:'Актриса',verified:true,authorRatings:8,comments:2,preferred:18420,
   scores:{reviews:87,ratings:85,critics:89},
   works:[{id:'rf0',title:'Дюна: Часть вторая',year:2024,genre:'Фантастика',director:'Дени Вильнёв',role:'Леди Джессика',poster:posters[0],reviews:1284,ratings:18420,popularity:12482,scores:{reviews:86,ratings:83,critics:91}}]
 },
 'Хавьер Бардем':{
   image:avatars[3],specialty:'Актёр',verified:true,authorRatings:11,comments:3,preferred:21680,
   scores:{reviews:88,ratings:86,critics:90},
   works:[{id:'jb0',title:'Дюна: Часть вторая',year:2024,genre:'Фантастика',director:'Дени Вильнёв',role:'Стилгар',poster:posters[0],reviews:1284,ratings:18420,popularity:12482,scores:{reviews:86,ratings:83,critics:91}}]
 },
 'Флоренс Пью':{
   image:avatars[5],specialty:'Актриса',verified:true,authorRatings:9,comments:2,preferred:25140,
   scores:{reviews:89,ratings:87,critics:90},
   works:[{id:'fp0',title:'Дюна: Часть вторая',year:2024,genre:'Фантастика',director:'Дени Вильнёв',role:'Принцесса Ирулан',poster:posters[0],reviews:1284,ratings:18420,popularity:12482,scores:{reviews:86,ratings:83,critics:91}}]
 },
 'Остин Батлер':{
   image:avatars[1],specialty:'Актёр',verified:true,authorRatings:7,comments:2,preferred:19720,
   scores:{reviews:87,ratings:85,critics:89},
   works:[{id:'ab0',title:'Дюна: Часть вторая',year:2024,genre:'Фантастика',director:'Дени Вильнёв',role:'Фейд-Раута',poster:posters[0],reviews:1284,ratings:18420,popularity:12482,scores:{reviews:86,ratings:83,critics:91}}]
 },
 'Грег Фрейзер':{
   image:avatars[1],specialty:'Оператор',verified:true,authorRatings:5,comments:3,preferred:9340,
   scores:{reviews:91,ratings:89,critics:93},
   works:[{id:'gf0',title:'Дюна: Часть вторая',year:2024,genre:'Фантастика',director:'Дени Вильнёв',role:'Оператор',poster:posters[0],reviews:1284,ratings:18420,popularity:12482,scores:{reviews:86,ratings:83,critics:91}}]
 },
 'Ханс Циммер':{
   image:avatars[5],specialty:'Композитор',verified:true,authorRatings:16,comments:6,preferred:25820,
   scores:{reviews:93,ratings:91,critics:94},
   works:[{id:'hz0',title:'Дюна: Часть вторая',year:2024,genre:'Фантастика',director:'Дени Вильнёв',role:'Композитор',poster:posters[0],reviews:1284,ratings:18420,popularity:12482,scores:{reviews:86,ratings:83,critics:91}}]
 },
 'Джо Уокер':{
   image:avatars[4],specialty:'Монтажёр',verified:true,authorRatings:4,comments:1,preferred:5410,
   scores:{reviews:87,ratings:85,critics:89},
   works:[{id:'jw0',title:'Дюна: Часть вторая',year:2024,genre:'Фантастика',director:'Дени Вильнёв',role:'Монтаж',poster:posters[0],reviews:1284,ratings:18420,popularity:12482,scores:{reviews:86,ratings:83,critics:91}}]
 },
 'Патрис Верметт':{
   image:avatars[2],specialty:'Художник-постановщик',verified:true,authorRatings:4,comments:2,preferred:6120,
   scores:{reviews:90,ratings:88,critics:92},
   works:[{id:'pv0',title:'Дюна: Часть вторая',year:2024,genre:'Фантастика',director:'Дени Вильнёв',role:'Художник-постановщик',poster:posters[0],reviews:1284,ratings:18420,popularity:12482,scores:{reviews:86,ratings:83,critics:91}}]
 }
};

const REVIEWS=[
 {id:1,movieId:0,user:'frames.by.ira',avatar:avatars[2],status:'Лазурь',kind:'user',total:86,parts:[8,7,10,9,9],title:'Большое кино, которое не боится тишины',text:'«Дюна: Часть вторая» работает не только масштабом. Самое интересное здесь — как фильм умеет удерживать напряжение в паузах, во взглядах и в ощущении неизбежности. Экшен действительно огромный, но Вильнёв не позволяет ему превратить историю в шум. Пустыня ощущается почти отдельным персонажем: она давит, диктует ритм и превращает каждое решение героев в часть чего-то намного большего. Мне особенно понравилось, что Пол здесь постепенно перестаёт быть просто героем, которому хочется сопереживать. Чем ближе он подходит к своей цели, тем тревожнее становится наблюдать за тем, во что эта цель его превращает. Это делает фильм намного интереснее обычной истории о мести. Вторая половина фильма постоянно меняет наше отношение к образу героя и к тому мифу, который вокруг него строится. И именно поэтому финальные решения воспринимаются не как победа, а как начало чего-то пугающего. Визуальная часть потрясающая, но она не существует отдельно от смысла: масштаб архитектуры, пустоты и толпы буквально показывает, насколько маленьким оказывается отдельный человек внутри большой идеи. После фильма мне хотелось не обсуждать, насколько красивыми были сцены, а спорить о том, когда именно Пол перестал контролировать собственную историю.',likes:327,date:'22 сентября 2026',spoiler:false,popular:327,authorLikes:['Дени Вильнёв']},
 {id:2,movieId:0,user:'CINEMA//NOW',avatar:avatars[1],status:'Медиа',kind:'media',total:91,parts:[9,8,10,9,10],title:'Спектакль масштаба, который остаётся личной драмой',text:'Продолжение строится на редком балансе: визуальное величие не вытесняет характеры. Особенно хорошо работает конфликт между мифом, который создаёт общество, и человеком, вынужденным в этот миф войти.',likes:611,date:'21 сентября 2026',spoiler:false,popular:611,authorLikes:['Дени Вильнёв']},
 {id:3,movieId:1,user:'NADYA',avatar:avatars[0],status:'Лазурь',kind:'user',total:92,parts:[9,9,10,10,9],title:'Редкий блокбастер, который не боится быть сентиментальным',text:'Научная фантастика здесь не декорация, а способ буквально растянуть человеческую разлуку во времени. Чем больше масштаб, тем личнее становится история.',likes:203,date:'20 сентября 2026',spoiler:false,popular:203,authorLikes:['Кристофер Нолан']},
 {id:4,movieId:0,user:'noirframe',avatar:avatars[4],status:'Мята',kind:'user',total:78,parts:[8,7,9,8,7],title:'Красиво, но главный поворот меня оттолкнул',text:'Я не буду раскрывать детали без предупреждения, потому что именно один из поздних выборов героя полностью изменил моё отношение к фильму.',likes:94,date:'19 сентября 2026',spoiler:true,popular:94,authorLikes:[]}
];

const USER_PROFILES={
 'frames.by.ira':{nickname:'frames.by.ira',bio:'Смотрю кино через визуальный язык, монтаж и музыку. Люблю находить детали, которые легко пропустить.',avatar:avatars[2],joined:'3 ноября 2024',points:1760,prefMovies:[4,0,2,1,3],prefDirectors:[],prefActors:[]},
 'NADYA':{nickname:'NADYA',bio:'Люблю медленное кино, психологические триллеры и фильмы, после которых хочется спорить до ночи.',avatar:avatars[0],joined:'12 апреля 2025',points:2840,prefMovies:[0,1,2,3,4],prefDirectors:[0,1,2,3,4],prefActors:[0,1,2,3,4]},
 'noirframe':{nickname:'noirframe',bio:'Нуар, неонуар и всё, что снято в полутьме. Пишу о том, что зацепило.',avatar:avatars[4],joined:'7 февраля 2026',points:640,prefMovies:[2,5,3],prefDirectors:[2],prefActors:[]},
 'CINEMA//NOW':{nickname:'CINEMA//NOW',kind:'media',bio:'Независимое медиа о кино: рецензии на премьеры и разборы классики.',avatar:avatars[1],joined:'1 сентября 2024',points:5400,prefMovies:[4,1,0],prefDirectors:[3,1],prefActors:[]}
};

// Съёмочная группа есть только у «Дюны»: у остальных фильмов в демо-данных состав пустой.
MOVIES.forEach(m=>{m.crew=m.id===0?CREW:{actors:[],team:[]}});

const DEMO_USER={nickname:'NADYA',bio:'Люблю медленное кино, психологические триллеры и фильмы, после которых хочется спорить до ночи.',avatar:avatars[0],joined:'12 апреля 2025',points:2840,
 stats:{reviews:1,ratings:3,receivedLikes:203,givenLikes:58,authorLikesReceived:1},
 ratings:[{movieId:4,score:90,parts:[9,9,9,9,9],order:0},{movieId:2,score:86,parts:[9,8,9,8,9],order:1},{movieId:5,score:78,parts:[8,8,9,8,8],order:2}],
 prefMovies:[0,1,2,3,4],prefDirectors:[0,1,2,3,4],prefActors:[0,1,2,3,4]};
const DEMO_AUTHOR='Дени Вильнёв';

export function createMockData(){
 return structuredClone({movies:MOVIES,reviews:REVIEWS,authors:AUTHOR_PROFILES,people:PEOPLE,users:USER_PROFILES,demoUser:DEMO_USER,demoAuthor:DEMO_AUTHOR});
}
