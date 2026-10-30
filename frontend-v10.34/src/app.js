import './styles.css';
import {api,USE_MOCK} from './echo-api.js';
import {avatars,posters} from './placeholders.js';
import echoMark from './assets/echo-mark.png';

/* Данные приходят из api.loadInitialData() (демо-данные или сервер) при запуске — см. boot() в конце файла. */
let MOVIES=[],REVIEWS=[],PENDING_REVIEWS=[],AUTHOR_PROFILES={},PEOPLE={directors:[],actors:[]},USER_PROFILES={};
let DEMO_USER=null;
// Жанры TMDB: русская подпись → id жанра (параметр genreId каталога).
const GENRE_IDS={'Боевик':28,'Вестерн':37,'Военный':10752,'Детектив':9648,'Документальный':99,'Драма':18,'История':36,'Комедия':35,'Криминал':80,'Мелодрама':10749,'Музыка':10402,'Мультфильм':16,'Приключения':12,'Семейный':10751,'Триллер':53,'Ужасы':27,'Фантастика':878,'Фэнтези':14};
let searchTimer=null,catalogSerial=0,searchSerial=0;

const FIREFLIES=[
 {level:1,name:'Искра',threshold:0,color:'#ffd166',glow:'rgba(255,209,102,.55)'},
 {level:2,name:'Мята',threshold:500,color:'#8cf4b1',glow:'rgba(140,244,177,.55)'},
 {level:3,name:'Лазурь',threshold:1500,color:'#79d8ff',glow:'rgba(121,216,255,.55)'},
 {level:4,name:'Аметист',threshold:3000,color:'#b993ff',glow:'rgba(185,147,255,.60)'},
 {level:5,name:'Рубин',threshold:5000,color:'#ff7d9c',glow:'rgba(255,125,156,.58)'},
 {level:6,name:'Полярный',threshold:8000,color:'#f4fbff',glow:'rgba(244,251,255,.72)'}
];
// Баллы статуса — утверждённая таблица (совпадает с лендингом для авторов).
// Расчёт по статистике профиля — в echo-api.js (pointsFromStats).
const STATUS_ACTIONS=[
  ['Поставленный лайк на рецензию','+5'],
  ['Полученный лайк на рецензию','+10'],
  ['Оценка без рецензии','+50'],
  ['Рецензия','+250'],
  ['Лайк автора на вашу рецензию','+1000',true]
];

/* Аватар по умолчанию: первая буква никнейма на цветном фоне. Цвет стабилен для одного имени. */
const AVATAR_COLORS=[['#7c5cff','#4b2fc2'],['#e8577a','#9e2d4c'],['#2bb3c9','#16707f'],['#e08a3c','#9a531a'],['#3fae74','#1f6b45'],['#b45cd6','#6e2a8f'],['#4f7fe8','#2a4aa8'],['#c99a2e','#7d5c12']];
const letterAvatarCache=new Map();
function letterAvatar(name){
 const key=String(name||'').trim();
 if(letterAvatarCache.has(key))return letterAvatarCache.get(key);
 const ch=(key.match(/[\p{L}\p{N}]/u)?.[0]||'Э').toUpperCase();
 let h=0;for(const c of key)h=(h*31+c.codePointAt(0))>>>0;
 const [a,b]=AVATAR_COLORS[h%AVATAR_COLORS.length];
 const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" viewBox="0 0 160 160"><defs><linearGradient id="a" x1="0" y1="0" x2="1" y2="1"><stop stop-color="${a}"/><stop offset="1" stop-color="${b}"/></linearGradient></defs><rect width="160" height="160" fill="url(#a)"/><text x="80" y="84" text-anchor="middle" dominant-baseline="central" fill="#fff" font-family="Inter,Segoe UI,Arial,sans-serif" font-size="74" font-weight="800">${ch}</text></svg>`;
 const uri='data:image/svg+xml;charset=UTF-8,'+encodeURIComponent(svg);
 letterAvatarCache.set(key,uri);return uri;
}
function avatarOf(src,name){return src||letterAvatar(name)}
/* <img> аватара: при ошибке загрузки обработчик внизу файла подставит букву по data-name. */
function avatarImg(src,name,attrs='alt=""'){return `<img src="${avatarOf(src,name)}" data-name="${escapeHtml(name||'')}" ${attrs}>`}

const PARAM_INFO=[
  'Насколько ценно и убедительно фильм построен как повествование.',
  'Насколько убедительно созданы персонажи и насколько хорошо они существуют на экране.',
  'Насколько выразительно и цельно фильм использует изображение.',
  'Насколько звук и музыка помогают сценам, атмосфере и повествованию.',
  'Насколько сильный личный отклик оставил фильм после просмотра.'
];

// Review drafts are keyed by film. The prototype has no backend, so drafts are kept
// in this browser only; the UI never treats a draft as a published review.
const REVIEW_DRAFT_PREFIX='echo:review-draft:v1:';
const PENDING_REVIEW_KEY='echo:pending-review:v1';
const reviewDraftMemory=new Map();
function draftKey(movieId){return REVIEW_DRAFT_PREFIX+movieId}
function validMovieId(id){return Number.isInteger(id)&&MOVIES.some(m=>m.id===id)}
function cleanDraft(raw,movieId){
 if(!raw||typeof raw!=='object'||!validMovieId(movieId))return null;
 const parts=Array.isArray(raw.parts)&&raw.parts.length===5?raw.parts.map(x=>Math.max(1,Math.min(10,Math.round(Number(x)||5)))):[5,5,5,5,5];
 return {movieId,title:String(raw.title??'').slice(0,140),text:String(raw.text??'').slice(0,10000),parts,spoiler:!!raw.spoiler};
}
function readReviewDraft(movieId){
 if(reviewDraftMemory.has(movieId))return cleanDraft(reviewDraftMemory.get(movieId),movieId);
 try{
  const raw=localStorage.getItem(draftKey(movieId));
  if(!raw)return null;
  const draft=cleanDraft(JSON.parse(raw),movieId);
  if(draft)reviewDraftMemory.set(movieId,draft);
  return draft;
 }catch(_){return null}
}
function writeReviewDraft(movieId,data){
 const draft=cleanDraft(data,movieId);if(!draft)return;
 reviewDraftMemory.set(movieId,draft);
 try{localStorage.setItem(draftKey(movieId),JSON.stringify(draft))}catch(_){/* in-memory fallback */}
}
function clearReviewDraft(movieId){
 reviewDraftMemory.delete(movieId);
 try{localStorage.removeItem(draftKey(movieId))}catch(_){}
}
function readPendingReviewMovie(){
 try{const raw=sessionStorage.getItem(PENDING_REVIEW_KEY);if(raw===null)return null;const id=Number(raw);return validMovieId(id)?id:null}catch(_){return null}
}
function setPendingReviewMovie(movieId){
 state.pendingReviewMovieId=movieId;
 try{sessionStorage.setItem(PENDING_REVIEW_KEY,String(movieId))}catch(_){}
}
function clearPendingReviewMovie(){
 state.pendingReviewMovieId=null;
 try{sessionStorage.removeItem(PENDING_REVIEW_KEY)}catch(_){}
}
function captureReviewDraft(){
 const title=document.getElementById('reviewTitle');
 const text=document.getElementById('reviewText');
 if(!title||!text||!validMovieId(state.movieId))return null;
 const parts=[0,1,2,3,4].map(i=>Number(document.querySelector(`[data-param="${i}"]`)?.value)||5);
 return cleanDraft({title:title.value,text:text.value,parts,spoiler:readReviewDraft(state.movieId)?.spoiler||false},state.movieId);
}
function saveCurrentReviewDraft(){
 const draft=captureReviewDraft();if(draft)writeReviewDraft(draft.movieId,draft);
 return draft;
}
function returnToReview(movieId){
 if(!validMovieId(movieId))return;
 clearPendingReviewMovie();
 state.view='site';state.page='movie';state.movieId=movieId;
 render();
 const composer=document.querySelector('.composer');
 if(composer)composer.scrollIntoView({block:'start',behavior:'auto'});
 const editor=document.getElementById('reviewText');
 if(editor)editor.focus({preventScroll:true});
}

let state={
 view:readPendingReviewMovie()!==null?'userAuth':'site',page:'home',movieId:0,pendingReviewMovieId:readPendingReviewMovie(),userLogged:false,userAuth:'login',authorAuth:'login',studioPage:'dashboard',
 profileTab:'reviews',profileSort:'Новые',movieSort:'Новые',crewTab:'actors',searchQuery:'',searchTab:'all',
 openFilter:null,filters:{genre:'Все',year:'Все',country:'Все',sort:'Лучшие'},
 catalog:{ids:[],page:0,totalPages:1,totalResults:0,loading:false,error:''},searchResults:{ids:[],page:0,totalPages:1,totalResults:0,loading:false,error:''},
 expanded:new Set(),spoilers:new Set(),likedReviews:new Set(),likedAuthorComments:new Set(),helperOpen:false,helperDone:new Set(),
 editSection:'profile',editPrefType:'movies',editQuery:'',editAvatar:'',user:null,
 authorForm:{name:'',login:'',password:''},authorApplied:null,
 authorProfileTab:'works',authorSource:'all',authorLogged:false,authorAccount:'Дени Вильнёв',authorReturn:null,otherUser:'frames.by.ira',pendingReturn:null,authorName:'Дени Вильнёв',authorSpecialty:'Режиссёр',openAuthorSpecialty:false,authorFilmSort:'Новые',openStatusInfo:false,studioCommentMovie:0,openStudioMovieSelect:false
};

function firefly(f,size=26,locked=false,tag='span'){return `<${tag} class="firefly" style="--fly:${f.color};--glow:${f.glow};width:${size}px;height:${size}px;opacity:${locked?.28:1}" aria-hidden="true"><${tag} class="wing l"></${tag}><${tag} class="wing r"></${tag}><${tag} class="body"></${tag}></${tag}>`}
function rating(total,parts=[],kind='ratings',large=false){
 const labels=ECHO_PARAMETER_NAMES;
 if(total===null||total===undefined)return `<div class="rating-badge rating-${kind} rating-empty ${large?'rating-large':''}"><strong class="rating-total">—</strong><span class="rating-empty-text">Нет оценок</span></div>`;
 return `<div class="rating-badge rating-${kind} ${large?'rating-large':''}"><strong class="rating-total">${total}</strong>${parts.length?`<span class="rating-parts">${parts.map((v,i)=>`<i class="${i===4?'impression':''}" data-param-label="${labels[i]}" tabindex="0" aria-label="${labels[i]}: ${v}">${v}</i>`).join('')}</span>`:''}</div>`
}
const SCORE_SHORT={reviews:'рецензии',ratings:'оценки',critics:'медиа'};
function compactRating(total,kind,label){
 const empty=total===null||total===undefined;
 return `<div class="compact-score echo-score-tile rating-${kind} ${empty?'empty':''}" aria-label="${label}: ${empty?'нет оценок':total}"><strong>${empty?'—':total}</strong><span class="echo-score-label">${label}</span><span class="echo-score-short" aria-hidden="true">${SCORE_SHORT[kind]||''}</span></div>`;
}

function compactPosterParts(parts){
 const labels=['Драматургия и структура','Персонажи и исполнение','Визуальный язык','Звук и музыка','Впечатление'];
 return `<div class="poster-part-strip">${parts.map((v,i)=>`<span class="poster-part-pill ${i===4?'impression':''}" data-param-label="${labels[i]}">${v}</span>`).join('')}</div>`
}

function popularScoreStrip(movie){
 const defs=[
   ['reviews','Рецензии'],
   ['ratings','Оценки'],
   ['critics','Критики']
 ];
 return `<div class="popular-score-strip">${defs.map(([kind,label])=>{const s=scoreSet(movie,kind);return `<div class="popular-score-cell rating-${kind}"><b>${s.total??'—'}</b><span>${label}</span></div>`}).join('')}</div>`
}

function scoreSet(movie,kind){return movie.scoreSets?.[kind]||{total:null,parts:[]}}
function scoreTriplet(movie,withParts=false,large=false){
 const defs=[['reviews','По рецензиям'],['ratings','Без рецензий'],['critics','Медиа / критики']];
 return defs.map(([kind,label])=>{
   const s=scoreSet(movie,kind);
   return withParts?`<div class="score-item"><span>${label}</span>${rating(s.total,s.parts,kind,large)}</div>`:compactRating(s.total,kind,label);
 }).join('');
}
const ECHO_PARAMETER_NAMES=['Драматургия и структура','Персонажи и исполнение','Визуальный язык','Звук и музыка','Впечатление'];
function escapeHtml(value){return String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}

/* ===== v10.29: общие помощники — сессия автора, лайки автора, возвраты, единые оценки ===== */
const SCORE_KINDS=[['reviews','По рецензиям'],['ratings','Без рецензий'],['critics','Медиа / критики']];
function scoreTiles(scores){return SCORE_KINDS.map(([kind,label])=>compactRating(scores?.[kind],kind,label)).join('')}
function todayRu(){const M=['января','февраля','марта','апреля','мая','июня','июля','августа','сентября','октября','ноября','декабря'];const d=new Date();return `${d.getDate()} ${M[d.getMonth()]} ${d.getFullYear()}`}
/* Светлячок всегда соответствует текущему статусу по баллам — выбора нет. */
function levelFor(points){return [...FIREFLIES].reverse().find(f=>(Number(points)||0)>=f.threshold)||FIREFLIES[0]}
function levelName(points){return levelFor(points).name}
function isOwnReview(r){return !!(state.userLogged&&state.user&&(r.userId&&state.user.id?r.userId===state.user.id:r.user===state.user.nickname))}
/* Уровень автора рецензии: по баллам из профиля; у демо-данных — по названию статуса. */
function reviewLevel(r){
 if(r.kind!=='user')return null;
 if(isOwnReview(r))return levelFor(state.user.points);
 const points=typeof r.points==='number'?r.points:USER_PROFILES[r.user]?.points;
 if(typeof points==='number')return levelFor(points);
 return FIREFLIES.find(f=>f.name===r.status)||FIREFLIES[0];
}
function statusLabel(r){return r.kind==='user'?reviewLevel(r).name:r.status}
function statusColor(r){return r.kind==='media'?'var(--cyan)':r.kind==='author'?'var(--amber)':reviewLevel(r)?.color||'var(--violet)'}
/* Аватар автора рецензии со светлячком статуса — как в профиле. */
function reviewPerson(r,tag='button'){
 const lvl=reviewLevel(r);
 const attrs=tag==='button'?`class="person" ${personTarget(r)}`:'class="person"';
 return `<${tag} ${attrs}><i class="person-avatar" title="${lvl?`Статус «${lvl.name}»`:''}">${avatarImg(r.avatar,r.user)}${lvl?firefly(lvl,18,false,'i'):''}</i><div><strong>${escapeHtml(r.user)}</strong><span style="color:${statusColor(r)}">${escapeHtml(statusLabel(r))}</span></div></${tag}>`;
}
/* Статус модерации собственной рецензии. */
function moderationBadge(r,{showPublished=true}={}){
 if(r.published===false){
  if(r.moderation==='REJECTED')return '<span class="review-status is-rejected">Отклонена модератором</span>';
  return '<span class="review-status is-pending"><span class="review-status-dot" aria-hidden="true"></span>На модерации</span>';
 }
 return showPublished?'<span class="review-status is-published">✓ Опубликована</span>':'';
}
function isPhone(){return matchMedia('(max-width:767px)').matches}
function focusSearch(id){const n=document.getElementById(id);if(n&&n.offsetParent!==null){n.focus({preventScroll:true});const l=n.value.length;try{n.setSelectionRange(l,l)}catch(_){}}}
function loader(text='Загрузка…'){return `<div class="echo-loader" role="status" aria-live="polite"><span class="echo-spinner" aria-hidden="true"></span><span>${text}</span></div>`}
function personTarget(r){return r.kind==='author'?`data-author-name="${escapeHtml(r.user)}"`:`data-profile-user="${escapeHtml(r.user)}"`}
function authorLikeBadge(r){
 const names=r.authorLikes||[];if(!names.length)return'';
 const who=escapeHtml(names.join(', '));
 return `<span class="author-like" title="Лайк автора: ${who}" aria-label="Лайк автора: ${who}">★ ${names.length>1?`Лайки авторов · ${names.length}`:'Лайк автора'}</span>`;
}
function reviewFooter(r){
 if(r.published===false)return `<div class="review-footer"><span class="muted">${r.moderation==='REJECTED'?'Рецензия не прошла модерацию':'Появится в общем списке после проверки модератором'}</span><span>${r.date?`Отправлена ${r.date}`:''}</span></div>`;
 const liked=state.likedReviews.has(r.id);
 const like=`<button class="review-like ${liked?'liked':''}" data-like-review="${r.id}" aria-label="Нравится" aria-pressed="${liked}"><span class="like-heart" aria-hidden="true">♥</span><span class="like-count">${fmt(r.likes)}</span></button>`;
 return `<div class="review-footer"><span class="review-likes">${like}${authorLikeBadge(r)}</span><span>${r.date}</span></div>`;
}
/* Обычный лайк ставят и зрители, и авторы. Свой авторский комментарий автор не лайкает. */
function commentLike(id,likes){
 likes=Number(likes)||0;
 if(state.authorLogged&&MOVIES[id]?.authorComment?.author===state.authorAccount)return `<span class="comment-like is-static" aria-label="Лайков: ${likes}"><span class="like-heart" aria-hidden="true">♥</span><span class="like-count">${fmt(likes)}</span></span>`;
 const liked=state.likedAuthorComments.has(id);
 return `<button class="comment-like ${liked?'liked':''}" data-like-comment="${id}" aria-pressed="${liked}"><span class="like-heart" aria-hidden="true">♥</span><span class="like-count">${fmt(likes)}</span></button>`;
}
function authorRole(a){return (a.specialty||'Автор').split('·')[0].trim()}
/* Должности из титров TMDB приходят на английском — показываем по-русски. */
const CREW_ROLES={'director':'Режиссёр','screenplay':'Сценарист','writer':'Сценарист','story':'Автор сюжета','novel':'Автор романа','producer':'Продюсер','executive producer':'Исполнительный продюсер','director of photography':'Оператор','cinematography':'Оператор','original music composer':'Композитор','music':'Композитор','editor':'Монтажёр','production design':'Художник-постановщик','art direction':'Арт-директор','costume design':'Художник по костюмам','casting':'Кастинг','sound designer':'Звукорежиссёр','visual effects supervisor':'Супервайзер VFX'};
function ruRole(role){const r=String(role||'').trim();return CREW_ROLES[r.toLowerCase()]||r}
function authorMovieIds(name){const a=getAuthorProfile(name);return MOVIES.filter(m=>m.director===name||(a.works||[]).some(w=>w.title===m.title)).map(m=>m.id)}
function reviewsOnAuthorWorks(name){const ids=authorMovieIds(name);return REVIEWS.filter(r=>ids.includes(r.movieId)&&r.user!==name)}
function reviewerIdentity(){
 if(state.authorLogged){const a=getAuthorProfile(state.authorAccount);return {user:state.authorAccount,avatar:a.image,status:authorRole(a),kind:'author'}}
 return {user:state.user.nickname,userId:state.user.id,avatar:state.user.avatar,status:levelName(state.user.points),points:state.user.points,kind:'user'};
}
function otherUserProfile(name){
 if(USER_PROFILES[name])return USER_PROFILES[name];
 const r=REVIEWS.find(x=>x.user===name);
 return {nickname:name||'Пользователь',kind:r?.kind,bio:'',avatar:r?.avatar||'',joined:'—',points:r?.points||0,prefMovies:[],prefDirectors:[],prefActors:[]};
}
function authorAccountControls(){
 const a=getAuthorProfile(state.authorAccount);
 return `<div class="topbar-account"><button class="authors-btn" data-open-studio="dashboard"><span class="author-dot"></span><span>Эхо Studio</span></button><button class="account-btn author-account-btn" data-open-own-author aria-label="Профиль автора: ${escapeHtml(state.authorAccount)}">${avatarImg(a.image,state.authorAccount,'class="account-avatar" alt=""')}<span>Профиль</span></button></div>`;
}
/* Возврат с публичной страницы автора: запоминаем, откуда пришли. */
function captureReturn(){
 if(state.view==='studio')return {view:'studio',studioPage:state.studioPage,label:'← Вернуться в Эхо Studio'};
 const p=state.page;
 if(p==='authorProfile')return state.authorReturn;
 const label=p==='movie'?`← К фильму «${MOVIES[state.movieId]?.title||''}»`:p==='catalog'?'← К каталогу':p==='search'?'← К результатам поиска':p==='profile'?'← В мой профиль':p==='otherProfile'?`← К профилю ${state.otherUser}`:p==='editProfile'?'← К редактированию профиля':'← На главную';
 return {view:'site',page:p,movieId:state.movieId,otherUser:state.otherUser,label};
}
function openAuthorProfile(name){
 state.authorReturn=captureReturn()||null;
 state.authorName=name;state.authorProfileTab='works';
 go('authorProfile');
}
function goBackFromAuthor(){
 const r=state.authorReturn;
 if(!r){go('home');return}
 if(r.view==='studio'){state.view='studio';state.studioPage=r.studioPage||'dashboard';render();scrollTo(0,0);return}
 state.movieId=r.movieId;state.otherUser=r.otherUser;go(r.page||'home');
}
function commentConfirmModal(p){
 return `<div class="overlay" id="commentOverlay"><div class="modal submit-modal"><div class="modal-head"><div><span class="eyebrow author">Публикация</span><h2>Опубликовать авторский комментарий?</h2></div><button id="closeCommentModal" aria-label="Закрыть">×</button></div><div class="submit-review-preview author-comment-preview"><strong>${escapeHtml(p.title)}</strong><p>${escapeHtml(p.text.slice(0,220))}${p.text.length>220?'…':''}</p></div><p class="muted">Комментарий появится на странице фильма «${escapeHtml(p.movie)}»${p.replace?' и заменит текущий опубликованный комментарий':''}.</p><div class="submit-actions"><button class="btn ghost" id="cancelComment">Отмена</button><button class="btn author" id="confirmComment">Опубликовать</button></div></div></div>`;
}

/* ===== v10.30: единый счётчик «в предпочтениях» для фильма и автора ===== */
const PREF_DELTA={};
function personPrefCategory(name){
 if(PEOPLE.directors.some(p=>p[0]===name))return 'directors';
 if(PEOPLE.actors.some(p=>p[0]===name))return 'actors';
 const s=getAuthorProfile(name).specialty||'';
 if(/Актёр|Актриса/.test(s))return 'actors';
 if(/Режиссёр/.test(s))return 'directors';
 return null;
}
function prefTarget(key,create=false){
 const i=key.indexOf(':'),type=key.slice(0,i),val=key.slice(i+1);
 if(type==='movie'){const id=+val;return MOVIES[id]?{type,listKey:'prefMovies',editType:'movies',id,label:MOVIES[id].title}:null}
 const cat=personPrefCategory(val);if(!cat)return null;
 let idx=PEOPLE[cat].findIndex(p=>p[0]===val);
 if(idx<0&&create){PEOPLE[cat].push([val,getAuthorProfile(val).image]);idx=PEOPLE[cat].length-1}
 return {type,name:val,listKey:cat==='directors'?'prefDirectors':'prefActors',editType:cat,id:idx,label:val};
}
function adjustPref(t,d){
 if(t.type==='movie'){if(MOVIES[t.id])MOVIES[t.id].preferred=Math.max(0,MOVIES[t.id].preferred+d)}
 else PREF_DELTA[t.name]=(PREF_DELTA[t.name]||0)+d;
}
function prefCounter(key,count,{interactive=true}={}){
 const t=prefTarget(key);
 const canAct=interactive&&!!t&&!state.authorLogged;
 const added=!!t&&state.userLogged&&t.id>=0&&state.user[t.listKey].includes(t.id);
 const tag=canAct?'button':'div';
 const action=added?'Убрать из предпочтений':'Добавить в предпочтения';
 return `<${tag} class="pref-counter ${added?'is-added':''} ${canAct?'':'is-static'}" ${canAct?`data-pref-toggle="${escapeHtml(key)}" aria-pressed="${added}" title="${action}" aria-label="${action}. Сейчас в предпочтениях: ${fmt(count)}"`:`aria-label="В предпочтениях: ${fmt(count)}"`}><span class="pref-counter-heart" aria-hidden="true">♥</span><strong>${fmt(count)}</strong><span>в предпочтениях</span></${tag}>`;
}
function apiFail(e){showNotice(escapeHtml(e?.message||'Не удалось сохранить изменения.'),4200)}
function requireViewerLogin(){
 if(state.userLogged)return false;
 state.pendingReturn={page:state.page,movieId:state.movieId,authorName:state.authorName,authorReturn:state.authorReturn,otherUser:state.otherUser};
 state.view='userAuth';render();showNotice('Войдите, чтобы поставить лайк.',3200);return true;
}
function savePrefs(){
 api.savePreferences({
  movies:state.user.prefMovies,
  directors:state.user.prefDirectors.map(i=>PEOPLE.directors[i]?.[0]).filter(Boolean),
  actors:state.user.prefActors.map(i=>PEOPLE.actors[i]?.[0]).filter(Boolean)
 }).catch(apiFail);
}
function togglePreference(key){
 if(state.authorLogged)return;
 if(!state.userLogged){
  state.pendingReturn={page:state.page,movieId:state.movieId,authorName:state.authorName,authorReturn:state.authorReturn,otherUser:state.otherUser};
  state.view='userAuth';render();scrollTo(0,0);
  showNotice('Войдите, чтобы добавлять в предпочтения.',3200);return;
 }
 const t=prefTarget(key,true);if(!t)return;
 const list=state.user[t.listKey];
 if(list.includes(t.id)){
  state.user[t.listKey]=list.filter(x=>x!==t.id);adjustPref(t,-1);savePrefs();render();
  showNotice(`«${escapeHtml(t.label)}» убран из предпочтений.`,3200);return;
 }
 if(list.length>=5){
  state.editSection='preferences';state.editPrefType=t.editType;state.editQuery='';go('editProfile');
  showNotice('Топ-5 уже заполнен. Уберите один пункт, чтобы добавить новый.',4200);return;
 }
 state.user[t.listKey]=[...list,t.id];adjustPref(t,1);savePrefs();render();
 showNotice(`«${escapeHtml(t.label)}» добавлен в предпочтения · ${list.length+1}/5`,3200);
}
function echoMovieRow(m){
 const meta=[m.year,escapeHtml(m.genre),m.director?`<span class="inline-author-name" data-author-name="${escapeHtml(m.director)}">${escapeHtml(m.director)}</span>`:''].filter(Boolean).join(' · ');
 const counts=m.detailLoaded?`<span class="movie-counts">${fmt(m.reviews)} рецензий · ${fmt(m.ratings)} оценок</span>`:'<span class="movie-counts is-placeholder">Оценки Эхо — в карточке фильма</span>';
 return `<button class="movie-row echo-movie-row" data-movie="${m.id}"><img class="echo-movie-row-poster" src="${m.poster}" alt="Постер фильма ${escapeHtml(m.title)}"><div class="movie-copy"><div class="movie-copy-top"><h3>${escapeHtml(m.title)}</h3><p>${meta}</p></div>${counts}</div><div class="movie-scores compact-triplet echo-score-grid">${scoreTriplet(m)}</div></button>`;
}
/* Три группы оценок одним компонентом: итог и пять параметров, как у оценки без рецензии в профиле. */
const SCORE_BLOCKS=[['reviews','По рецензиям','оценки с текстом рецензии'],['ratings','Без рецензий','оценки без текста'],['critics','Медиа','оценки медиа и критиков']];
function scoreBlocks(sets,{legend=false,cls=''}={}){
 const blocks=SCORE_BLOCKS.map(([kind,label,hint])=>{const s=sets?.[kind]||{total:null,parts:[]};return `<div class="score-block score-block--${kind}"><div class="score-block-head"><strong class="score-block-label">${label}</strong><span class="score-block-hint">${hint}</span></div>${rating(s.total,Array.isArray(s.parts)?s.parts:[],kind)}</div>`}).join('');
 return `<div class="score-blocks-wrap ${cls}"><div class="score-blocks">${blocks}</div>${legend?'<p class="score-legend">Параметры по порядку: драматургия · персонажи · визуал · звук · <b>впечатление</b></p>':''}</div>`;
}
function movieScoreSets(m){return {reviews:scoreSet(m,'reviews'),ratings:scoreSet(m,'ratings'),critics:scoreSet(m,'critics')}}
/* Средние по нескольким фильмам: итог — среднее итогов, параметры — среднее каждого параметра. */
function averageScoreSets(movies,totals={}){
 return Object.fromEntries(SCORE_KINDS.map(([kind])=>{
  const sets=movies.map(m=>scoreSet(m,kind)).filter(s=>s.total!==null&&s.total!==undefined);
  const full=sets.filter(s=>Array.isArray(s.parts)&&s.parts.length===5);
  const avg=a=>a.reduce((x,y)=>x+y,0)/a.length;
  const total=totals[kind]??(sets.length?Math.round(avg(sets.map(s=>s.total))):null);
  const parts=full.length&&total!==null?[0,1,2,3,4].map(i=>Math.round(avg(full.map(s=>s.parts[i])))):[];
  return [kind,{total,parts}];
 }));
}
function filmAssessment(m,extraClass=''){
 return `<div class="echo-film-assessment ${extraClass}">${scoreBlocks(movieScoreSets(m))}</div>`;
}
function echoMovieSummary(m){
 const descOpen=state.descOpen===m.id,longDesc=String(m.desc||'').length>160;
 const metaLine=[m.genre,m.country].filter(Boolean).map(escapeHtml).join(' · ');
 const mobileHead=`<div class="m-film-head m-only"><img class="m-film-poster" src="${m.poster}" alt="Постер фильма ${escapeHtml(m.title)}"><div class="m-film-info"><span class="echo-film-kicker">Фильм${m.year?` · ${m.year}`:''}</span><h1>${escapeHtml(m.title)}</h1>${metaLine?`<p class="m-film-meta">${metaLine}</p>`:''}${m.director?`<p class="m-film-meta">Режиссёр <button class="author-inline-link" data-author-name="${escapeHtml(m.director)}">${escapeHtml(m.director)}</button></p>`:''}${prefCounter('movie:'+m.id,m.preferred)}</div></div>${m.desc?`<div class="m-film-desc m-only ${descOpen||!longDesc?'is-open':''}"><p>${escapeHtml(m.desc)}</p>${longDesc?`<button type="button" class="expand-btn" data-desc-toggle="${m.id}">${descOpen?'Свернуть':'Читать полностью'}</button>`:''}</div>`:''}`;
 return `<section class="echo-film-summary">${mobileHead}
   <div class="echo-film-poster"><img src="${m.poster}" alt="Постер фильма ${escapeHtml(m.title)}"></div>
   <div class="echo-film-main">
     <div class="echo-film-heading"><div><span class="echo-film-kicker">ФИЛЬМ · ${m.year}</span><h1>${escapeHtml(m.title)}</h1><p class="movie-description">${escapeHtml(m.desc)}</p></div>${prefCounter('movie:'+m.id,m.preferred)}</div>
     <div class="movie-fields echo-film-fields"><div class="movie-field"><span>Год</span><strong>${m.year}</strong></div><div class="movie-field"><span>Жанр</span><strong>${escapeHtml(m.genre)}</strong></div><div class="movie-field"><span>Страна</span><strong>${escapeHtml(m.country)}</strong></div><div class="movie-field"><span>Режиссёр</span><button class="author-inline-link" data-author-name="${escapeHtml(m.director)}">${escapeHtml(m.director)}</button></div></div>
     ${filmAssessment(m)}
   </div>
 </section>`;
}

function calcReviewTotal(parts){
 const form=(parts[0]+parts[1]+parts[2]+parts[3])/4;
 const base=form*10;
 const multiplier=.4+.6*(parts[4]-1)/9;
 return Math.round(base*multiplier)
}
function fmt(n){return Number(n).toLocaleString('ru-RU')}
async function loadCatalog(reset=false){
 if(USE_MOCK)return;
 if(reset)state.catalog={ids:[],page:0,totalPages:1,totalResults:0,loading:false,error:''};
 const catalog=state.catalog;
 if(catalog.loading||(!reset&&catalog.page>=catalog.totalPages))return;
 const serial=++catalogSerial;
 catalog.loading=true;catalog.error='';if(state.page==='catalog')render();
 try{
  const sort={Популярные:'popular',Лучшие:'best',Новые:'new',Старые:'old'}[state.filters.sort]||'popular';
  const result=await api.discoverMovies({page:catalog.page+1,sort,year:state.filters.year==='Все'?'':state.filters.year,
   genreId:GENRE_IDS[state.filters.genre]||'',country:state.filters.country==='Все'?'':state.filters.country});
  if(serial!==catalogSerial)return;
  for(const id of result.ids)if(!catalog.ids.includes(id))catalog.ids.push(id);
  catalog.page=result.page;catalog.totalPages=Math.min(result.totalPages,1000);
  catalog.totalResults=result.totalResults;
 }catch(error){if(serial===catalogSerial)catalog.error=error?.message||'Не удалось загрузить каталог'}
 finally{if(serial===catalogSerial){catalog.loading=false;if(state.page==='catalog')render()}}
}
async function loadSearch(reset=false){
 if(USE_MOCK)return;
 const query=state.searchQuery.trim();
 if(reset)state.searchResults={ids:[],page:0,totalPages:1,totalResults:0,loading:false,error:''};
 if(query.length<2){if(state.page==='search')render();return}
 const resultState=state.searchResults;
 if(resultState.loading||(!reset&&resultState.page>=resultState.totalPages))return;
 const serial=++searchSerial;
 resultState.loading=true;resultState.error='';if(state.page==='search')render();
 try{
  const result=await api.searchMovies(query,resultState.page+1);
  if(serial!==searchSerial||query!==state.searchQuery.trim())return;
  for(const id of result.ids)if(!resultState.ids.includes(id))resultState.ids.push(id);
  resultState.page=result.page;
  resultState.totalPages=Math.min(result.totalPages,1000);resultState.totalResults=result.totalResults;
 }catch(error){if(serial===searchSerial)resultState.error=error?.message||'Не удалось выполнить поиск'}
 finally{if(serial===searchSerial){resultState.loading=false;if(state.page==='search')render()}}
}
async function openMovie(id){
 if(!MOVIES[id])return;
 state.view='site';state.page='movie';state.movieId=id;
 if(!MOVIES[id].detailLoaded&&!USE_MOCK){
  if(leaveScroll===null)leaveScroll=scrollY;
  document.getElementById('root').innerHTML=siteShell(`<main class="page"><div class="page-loading">${loader('Загружаем фильм…')}</div></main>`);bind();
  try{await api.hydrateMovie(id)}catch(error){showNotice(error?.message||'Не удалось загрузить фильм');go('catalog');return}
 }
 render();scrollTo(0,0);
}
function go(page){state.view='site';state.page=page;render();scrollTo(0,0);if(page==='catalog'&&!USE_MOCK&&state.catalog.page===0&&!state.catalog.loading)loadCatalog(true)}
function currentFly(){return levelFor(state.user?.points)}
function reviewSort(list,sort){return [...list].sort((a,b)=>sort==='Популярные'?b.popular-a.popular:sort==='Старые'?a.id-b.id:b.id-a.id)}

function reviewCard(r,context='release'){
 const movie=MOVIES[r.movieId];const isLong=r.text.length>160;const expanded=state.expanded.has(r.id);const spoilerOpen=state.spoilers.has(r.id);
 const body=`<div class="review-copy ${isLong&&!expanded?'is-collapsed':''}"><p class="review-text">${escapeHtml(r.text)}</p></div>${isLong?`<button class="expand-btn" data-expand="${r.id}" aria-expanded="${expanded}">${expanded?'Свернуть':'Читать полностью'}</button>`:''}`;
 const reviewRating=rating(r.total,r.parts,'reviews');
 const content=r.spoiler&&!spoilerOpen?`<div class="spoiler-cover"><div><strong>⚠ Рецензия содержит спойлеры</strong></div><button data-spoiler="${r.id}">Показать</button></div>`:`${r.spoiler?'<span class="spoiler-label">СПОЙЛЕРЫ</span>':''}${body}`;
 const filmMeta=[movie.year,movie.director].filter(Boolean).map(escapeHtml).join(' · ');
 if(context==='profile'){
  const badge=isOwnReview(r)||r.published===false?`<div class="review-status-row">${moderationBadge(r)}</div>`:'';
  return `<article class="review profile-review ${r.published===false?'is-pending':''}"><div class="profile-review-head"><button class="profile-review-film" data-movie="${r.movieId}"><img src="${movie.poster}" alt=""><span><strong>${escapeHtml(movie.title)}</strong><em>${escapeHtml(movie.director||'')}</em></span></button>${reviewRating}</div>${badge}<h3>${escapeHtml(r.title)}</h3>${content}${reviewFooter(r)}</article>`;
 }
 if(context==='own')return `<article class="review own-review ${r.published===false?'is-pending':''}"><div class="review-top">${reviewPerson(r,'div')}${reviewRating}</div><h3>${escapeHtml(r.title)}</h3>${content}${r.published===false?'':reviewFooter(r)}</article>`;
 if(context==='home')return `<article class="review review-home ${isLong&&expanded?'review-home-expanded':'review-home-compact'}"><div class="review-home-top">${reviewPerson(r)}${reviewRating}</div><button class="review-film-mini" data-movie="${movie.id}"><img src="${movie.poster}" alt=""><span><strong>${escapeHtml(movie.title)}</strong><em>${filmMeta}</em></span></button><h3>${escapeHtml(r.title)}</h3>${content}${reviewFooter(r)}</article>`;
 return `<article class="review ${context==='feed'?'review-feed':''}"><div class="review-top">${reviewPerson(r)}${reviewRating}</div>${context==='feed'?`<button class="review-film-context" data-movie="${movie.id}"><img src="${movie.poster}" alt=""><span><strong>${escapeHtml(movie.title)}</strong><em>${filmMeta}</em></span></button>`:''}<h3>${escapeHtml(r.title)}</h3>${content}${reviewFooter(r)}</article>`
}

function authorCommentCard(movie){
 const c=movie.authorComment;if(!c)return'';
 const filmMeta=[movie.year,movie.director].filter(Boolean).map(escapeHtml).join(' · ');
 return `<article class="comment-card"><button class="comment-film" data-movie="${movie.id}"><img src="${movie.poster}" alt=""><span><strong>${escapeHtml(movie.title)}</strong><em>${filmMeta}</em></span></button><button class="person author-comment-person" data-author-name="${escapeHtml(c.author)}">${avatarImg(c.avatar||getAuthorProfile(c.author).image,c.author)}<div><strong>${escapeHtml(c.author)}</strong><span style="color:var(--amber)">${escapeHtml(c.role||'Автор')}</span></div></button><h3>${escapeHtml(c.title||'Комментарий автора')}</h3><p>«${escapeHtml(c.text)}»</p><footer>${commentLike(movie.id,c.likes)}<span>${escapeHtml(c.date||'')}</span></footer></article>`
}

/* ===== v10.32: мобильная версия — шапка, нижняя панель, «назад» (см. MOBILE.md) ===== */
const ICON={
 home:'<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3.5 10.5 12 3.8l8.5 6.7V20a1 1 0 0 1-1 1h-5v-6h-5v6h-5a1 1 0 0 1-1-1z"/></svg>',
 catalog:'<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3.5" y="3.5" width="7" height="9" rx="1.6"/><rect x="13.5" y="3.5" width="7" height="9" rx="1.6"/><rect x="3.5" y="15.5" width="7" height="5" rx="1.4"/><rect x="13.5" y="15.5" width="7" height="5" rx="1.4"/></svg>',
 search:'<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.8" cy="10.8" r="6.3"/><path d="m15.6 15.6 4.9 4.9"/></svg>',
 user:'<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8.3" r="4"/><path d="M4.5 20.5c1.1-3.9 4-5.8 7.5-5.8s6.4 1.9 7.5 5.8"/></svg>',
 studio:'<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7.5h16v11a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5z"/><path d="m4 7.5 2.4-4 3.6 4 2.4-4 3.6 4 2.4-4 1.6 4"/></svg>',
 back:'<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 4.5 7.5 12l7.5 7.5"/></svg>',
 overview:'<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 19.5h16"/><path d="M6.5 16V11M11 16V6.5M15.5 16v-6M20 16V8.5"/></svg>',
 film:'<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3.5" y="5" width="17" height="14" rx="2"/><path d="M7.5 5v14M16.5 5v14M3.5 9.5h4M3.5 14.5h4M16.5 9.5h4M16.5 14.5h4"/></svg>',
 chat:'<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4.5 5.5h15v10h-8l-4.5 4v-4h-2.5z"/></svg>',
 pen:'<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m14.5 5.5 4 4L9 19H5v-4z"/><path d="m12.5 7.5 4 4"/></svg>',
 star:'<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m12 3.8 2.5 5.3 5.8.7-4.3 4 1.1 5.7L12 16.7l-5.1 2.8L8 13.8l-4.3-4 5.8-.7z"/></svg>',
 out:'<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M14 4.5h4.5a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H14"/><path d="M10 8 6 12l4 4M6 12h10"/></svg>'
};
const ROOT_PAGES=['home','catalog','search','profile'];
function mobileTitle(){
 const p=state.page;
 if(p==='movie')return MOVIES[state.movieId]?.title||'Фильм';
 if(p==='authorProfile')return state.authorName||'Автор';
 if(p==='otherProfile')return state.otherUser||'Профиль';
 if(p==='editProfile')return state.editSection==='preferences'?'Предпочтения':'Редактирование';
 return '';
}
function mobileTop(){
 const deep=!ROOT_PAGES.includes(state.page);
 if(!deep)return `<header class="m-top m-only"><button class="m-brand" data-page="home" aria-label="Эхо — на главную"><span class="brand-mark"><img src="${echoMark}" alt="" aria-hidden="true"></span><span>Эхо</span></button></header>`;
 return `<header class="m-top m-top--deep m-only"><button class="m-back" data-m-back aria-label="Назад">${ICON.back}</button><strong class="m-title">${escapeHtml(mobileTitle())}</strong><span class="m-top-spacer" aria-hidden="true"></span></header>`;
}
function mobileNav(){
 const p=state.page,cur=k=>k===p||(k==='profile'&&p==='editProfile')?'aria-current="page" class="is-active"':'';
 const profile=state.authorLogged
  ?`<button data-open-own-author ${p==='authorProfile'&&state.authorName===state.authorAccount?'aria-current="page" class="is-active"':''}>${avatarImg(getAuthorProfile(state.authorAccount).image,state.authorAccount,'class="m-nav-avatar" alt=""')}<span>Профиль</span></button>`
  :state.userLogged
   ?`<button data-page="profile" ${cur('profile')}><i class="m-nav-avatar-wrap">${avatarImg(state.user.avatar,state.user.nickname,'class="m-nav-avatar" alt=""')}${firefly(currentFly(),12,false,'i')}</i><span>Профиль</span></button>`
   :`<button data-view="userAuth">${ICON.user}<span>Войти</span></button>`;
 return `<nav class="m-nav m-only" aria-label="Основная навигация"><button data-page="home" ${cur('home')}>${ICON.home}<span>Главная</span></button><button data-page="catalog" ${cur('catalog')}>${ICON.catalog}<span>Каталог</span></button><button data-m-search ${cur('search')}>${ICON.search}<span>Поиск</span></button>${state.authorLogged?`<button class="m-nav-studio" data-open-studio="dashboard">${ICON.studio}<span>Studio</span></button>`:''}${profile}</nav>`;
}
function studioMobileNav(){
 const items=[['dashboard','Обзор',ICON.overview],['works','Работы',ICON.film],['audience','Зрители',ICON.chat],['comments','Комментарии',ICON.pen],['profile','Профиль',ICON.user]];
 return `<nav class="m-nav m-nav--studio m-only" aria-label="Разделы Эхо Studio">${items.map(([id,l,ic])=>`<button data-studio="${id}" ${state.studioPage===id?'aria-current="page" class="is-active"':''}>${ic}<span>${l}</span></button>`).join('')}</nav>`;
}

/* История: каждый экран — запись в истории браузера, поэтому «назад» (кнопка и жест) остаётся внутри сайта. */
let fabObserver=null;
let navKey=null,navSnap=null,navIndex=0,navRestoring=false,lastScroll=0,leaveScroll=null;
addEventListener('scroll',()=>{lastScroll=scrollY},{passive:true});
function navSnapshot(){return {view:state.view,page:state.page,movieId:state.movieId,authorName:state.authorName,otherUser:state.otherUser,studioPage:state.studioPage,editSection:state.editSection,authorReturn:state.authorReturn,userAuth:state.userAuth,authorAuth:state.authorAuth}}
function snapKey(s){
 if(s.view!=='site')return s.view==='studio'?`studio|${s.studioPage}`:s.view;
 return [s.page,s.page==='movie'?s.movieId:'',s.page==='authorProfile'?s.authorName:'',s.page==='otherProfile'?s.otherUser:''].join('|');
}
function syncHistory(){
 const snap=navSnapshot(),key=snapKey(snap);
 if(key===navKey)return;
 const fromAuth=navSnap&&(navSnap.view==='userAuth'||navSnap.view==='authorAuth')&&!(snap.view==='userAuth'||snap.view==='authorAuth');
 try{
  if(navKey===null||navRestoring||fromAuth)history.replaceState({echo:snap,i:navIndex,scroll:0},'');
  else{history.replaceState({...(history.state||{}),echo:navSnap,i:navIndex,scroll:leaveScroll??lastScroll},'');navIndex++;history.pushState({echo:snap,i:navIndex,scroll:0},'')}
 }catch(_){}
 navKey=key;navSnap=snap;leaveScroll=null;
}
function goBack(){
 if(navIndex>0){history.back();return}
 if(state.view==='studio'){state.view='site';go('home');return}
 go('home');
}
addEventListener('popstate',e=>{
 const s=e.state?.echo;if(!s)return;
 document.querySelectorAll('.overlay').forEach(o=>o.remove());
 Object.assign(state,{view:s.view,page:s.page,movieId:s.movieId,authorName:s.authorName,otherUser:s.otherUser,studioPage:s.studioPage,editSection:s.editSection,authorReturn:s.authorReturn,userAuth:s.userAuth||state.userAuth,authorAuth:s.authorAuth||state.authorAuth,openFilter:null,openAuthorSpecialty:false,openStudioMovieSelect:false});
 if(s.view==='studio'&&!state.authorLogged)Object.assign(state,{view:'site',page:'home'});
 if(s.view==='site'&&s.page==='profile'&&!state.userLogged)state.page='home';
 if(s.view==='site'&&s.page==='editProfile'&&!state.userLogged)state.page='home';
 navIndex=e.state.i||0;navKey=snapKey(navSnapshot());navSnap=navSnapshot();
 const y=e.state.scroll||0;
 if(state.view==='site'&&state.page==='movie'&&MOVIES[state.movieId]&&!MOVIES[state.movieId].detailLoaded&&!USE_MOCK){openMovie(state.movieId).then(()=>scrollTo(0,y));return}
 navRestoring=true;render();navRestoring=false;
 requestAnimationFrame(()=>scrollTo(0,y));
});

function siteShell(content){
 const f=currentFly();
  const account=state.authorLogged?authorAccountControls():state.userLogged?`<button class="account-btn" data-page="profile" aria-label="Профиль · статус «${f.name}»">${firefly(f,22)}<span>Профиль</span></button>`:`<button class="account-btn" data-view="userAuth"><span class="account-dot" aria-hidden="true"></span><span>Войти</span></button>`;
 return `<div class="app"><header class="topbar"><button class="brand" data-page="home"><span class="brand-mark"><img src="${echoMark}" alt="" aria-hidden="true"></span><span>Эхо</span></button><div class="navlinks"><button data-page="home" class="${state.page==='home'?'active':''}">Главная</button><button data-page="catalog" class="${state.page==='catalog'?'active':''}">Каталог</button></div><label class="nav-search ${state.page==='search'?'active':''}"><span aria-hidden="true">⌕</span><input id="navSearchInput" value="${escapeHtml(state.searchQuery)}" placeholder="Поиск фильмов, людей, авторов" autocomplete="off" aria-label="Поиск фильмов, людей, авторов"></label>${account}</header>${mobileTop()}${content}<footer class="site-footer"><span class="footer-brand">Эхо · кино как разговор</span><span class="footer-links">Рецензии · оценки · предпочтения · авторы</span><span class="footer-source">Данные о фильмах — TMDB</span></footer>${mobileNav()}</div>`
}

function countBadge(n){return n?` <span class="count-badge m-only">${fmt(n)}</span>`:''}
/* Главная для вошедшего автора: вместо светлячка — карточка автора и его работы в разговоре. */
function authorHero(){
 const name=state.authorAccount,a=getAuthorProfile(name);
 const works=authorMovieIds(name),reviews=reviewsOnAuthorWorks(name),last=reviews[0];
 const lastMovie=last?MOVIES[last.movieId]:null;
 const stats=[['Работ',Math.max(works.length,(a.works||[]).length)],['Рецензий на работы',reviews.length],['Лайков автора',a.authorLikes||0]];
 return `<section class="hero hero-author"><div class="hero-copy"><span class="eyebrow author">Эхо Studio</span><h1>Ваши работы<br><em>в разговоре.</em></h1><p>Смотрите, что зрители и медиа пишут о ваших фильмах, отмечайте сильные рецензии лайком автора и оставляйте комментарии к своим работам.</p><div class="hero-actions"><button class="btn author" data-open-studio="dashboard">Открыть Эхо Studio</button><button class="btn ghost" data-open-own-author><span class="d-only">Мой профиль автора</span><span class="m-only">Профиль</span></button></div></div>
 <div class="hero-author-card"><div class="hac-person">${avatarImg(a.image,name,'alt=""')}<div><strong>${escapeHtml(name)}</strong><span>${escapeHtml(authorRole(a))}${a.verified?' · <b>✓ Верифицирован</b>':''}</span></div></div>
 <div class="hac-stats">${stats.map(([l,v])=>`<div><b>${fmt(v)}</b><span>${l}</span></div>`).join('')}</div>
 ${last&&lastMovie?`<button class="hac-latest" data-open-studio="audience"><small>Новая рецензия на вашу работу</small><strong>«${escapeHtml(last.title)}»</strong><span>${escapeHtml(last.user)} · ${escapeHtml(lastMovie.title)}</span></button>`:`<p class="hac-empty">${works.length?'Рецензий на ваши работы пока нет. Они появятся здесь и в Эхо Studio.':'Работы появятся в профиле после проверки заявки модератором.'}</p>`}</div></section>`;
}
function homePage(){
 const heroFly=state.userLogged?currentFly():FIREFLIES[0];
 const heroCaption=state.userLogged?`Ваш статус — «${heroFly.name}». Светлячок растёт вместе с уровнем`:'Светлячок растёт вместе с вашим уровнем';
 const discussed=REVIEWS.slice(0,3).map(r=>reviewCard(r,'home')).join('');
 return siteShell(`<main class="page">${state.authorLogged?authorHero():`<section class="hero"><div class="hero-copy"><span class="eyebrow">Кино как разговор</span><h1>Смотри внимательнее.<br><em>Говори точнее.</em></h1><p>Рецензии, оценки и предпочтения превращают просмотр в разговор — между зрителями, авторами и критиками.</p><div class="hero-actions"><button class="btn primary" data-page="catalog">Исследовать фильмы</button><button class="btn ghost" data-page="${state.userLogged||state.authorLogged?'profile':'userAuth'}" ${state.userLogged||state.authorLogged?'':'data-view="userAuth"'}>${state.userLogged||state.authorLogged?'<span class="d-only">Мой профиль</span><span class="m-only">Профиль</span>':'Войти'}</button></div></div><div class="hero-visual" style="--glow:${heroFly.glow}"><div class="orbit o1"></div><div class="orbit o2"></div>${firefly(heroFly,54)}<span class="hero-caption">${heroCaption}</span></div></section>`}
${state.userLogged?`<button class="m-status-card m-only" data-page="profile">${firefly(heroFly,30,false,'i')}<span><small>Ваш статус</small><strong>${heroFly.name} · ${fmt(state.user.points||0)} баллов</strong></span><b aria-hidden="true">→</b></button>`:''}
<section class="section"><div class="section-head"><h2>Сейчас обсуждают${countBadge(REVIEWS.length)}</h2></div>${discussed?`<div class="reviews-grid">${discussed}</div>`:'<div class="panel muted empty-state">Рецензий пока нет — станьте первым, кто напишет о фильме.</div>'}</section>
<section class="section"><div class="section-head"><h2>Популярное сейчас</h2><button class="quiet" data-page="catalog"><span class="d-only">Весь каталог →</span><span class="m-only">Все →</span></button></div><div class="poster-row">${MOVIES.slice(0,16).map(m=>`<button class="poster popular-poster" data-movie="${m.id}"><div class="poster-image"><img src="${m.poster}" alt=""><div class="poster-scores poster-total-strip"><span class="poster-total-pill rating-reviews" data-score-label="По рецензиям">${scoreSet(m,'reviews').total ?? '—'}</span><span class="poster-total-pill rating-ratings" data-score-label="Без рецензии">${scoreSet(m,'ratings').total ?? '—'}</span><span class="poster-total-pill rating-critics" data-score-label="Медиа / критики">${scoreSet(m,'critics').total ?? '—'}</span></div></div><strong>${escapeHtml(m.title)}</strong><span>${[m.year,m.director].filter(Boolean).map(escapeHtml).join(' · ')}</span></button>`).join('')}</div></section>
<section class="section"><div class="section-head"><h2>Последние авторские комментарии${countBadge(MOVIES.filter(m=>m.authorComment).length)}</h2></div><div class="comment-grid">${MOVIES.filter(m=>m.authorComment).slice(0,3).map(authorCommentCard).join('')||'<p class="muted">Комментариев пока нет.</p>'}</div></section></main>`)
}

/* Выпадающий фильтр. options: строки или пары [значение, подпись]. Длинные списки прокручиваются внутри меню. */
function filterControl(key,label,options){
 const open=state.openFilter===key;
 const items=options.map(o=>Array.isArray(o)?o:[o,o]);
 const current=state.filters[key];
 const shown=(items.find(([v])=>String(v)===String(current))||[current,current])[1];
 return `<div class="filter ${open?'open':''}" data-filter="${key}"><span id="filterLabel-${key}">${label}</span><button type="button" class="filter-button ${String(current)!==String(items[0][0])?'is-set':''}" data-filter-toggle="${key}" aria-haspopup="listbox" aria-expanded="${open}" aria-labelledby="filterLabel-${key}"><span class="filter-chip-label m-only">${label}</span><b>${escapeHtml(shown)}</b><i class="chev" aria-hidden="true">⌄</i></button>${open?`<div class="filter-backdrop m-only" aria-hidden="true"></div><div class="filter-menu ${items.length>8?'is-scroll':''}" role="listbox" aria-label="${label}"><div class="sheet-head m-only"><strong>${label}</strong><button type="button" data-filter-toggle="${key}" aria-label="Закрыть">×</button></div>${items.map(([v,l])=>`<button type="button" role="option" aria-selected="${String(v)===String(current)}" class="filter-option ${String(v)===String(current)?'is-selected':''}" data-filter-option="${key}" data-value="${escapeHtml(v)}">${escapeHtml(l)}</button>`).join('')}</div>`:''}</div>`;
}
const COUNTRY_OPTIONS=[['RU','Россия'],['SU','СССР'],['US','США'],['GB','Великобритания'],['FR','Франция'],['DE','Германия'],['IT','Италия'],['ES','Испания'],['JP','Япония'],['KR','Южная Корея'],['CN','Китай'],['HK','Гонконг'],['IN','Индия'],['CA','Канада'],['AU','Австралия'],['NZ','Новая Зеландия'],['SE','Швеция'],['DK','Дания'],['NO','Норвегия'],['FI','Финляндия'],['PL','Польша'],['CZ','Чехия'],['BE','Бельгия'],['NL','Нидерланды'],['IE','Ирландия'],['AT','Австрия'],['CH','Швейцария'],['BR','Бразилия'],['MX','Мексика'],['AR','Аргентина'],['IR','Иран'],['TR','Турция'],['KZ','Казахстан'],['BY','Беларусь'],['UA','Украина'],['GE','Грузия'],['AM','Армения']];
function yearOptions(){
 const now=new Date().getFullYear(),years=[];
 for(let y=now;y>=1900;y--)years.push(String(y));
 return years;
}
function catalogPage(){
 const f=state.filters;
 let list=USE_MOCK?MOVIES.filter(m=>(f.genre==='Все'||m.genre===f.genre)&&(f.year==='Все'||String(m.year)===f.year)&&(f.country==='Все'||String(m.country).split(', ').includes((COUNTRY_OPTIONS.find(([c])=>c===f.country)||[])[1]))):state.catalog.ids.map(id=>MOVIES[id]).filter(Boolean);
 if(USE_MOCK)list=[...list].sort((a,b)=>f.sort==='Лучшие'?(scoreSet(b,'ratings').total??-1)-(scoreSet(a,'ratings').total??-1):f.sort==='Старые'?a.year-b.year:b.year-a.year);
 const genres=USE_MOCK?[...new Set(MOVIES.map(m=>m.genre))]:Object.keys(GENRE_IDS);
 const activeFilters=['genre','year','country'].filter(k=>f[k]!=='Все').length;
 const filters=`${activeFilters?`<button type="button" class="filter-reset m-only" data-filter-reset>Сбросить · ${activeFilters}</button>`:''}${filterControl('genre','Жанр',['Все',...genres])}${filterControl('year','Год',[['Все','Любой'],...yearOptions()])}${filterControl('country','Страна',[['Все','Любая'],...COUNTRY_OPTIONS])}${filterControl('sort','Сортировка',USE_MOCK?['Лучшие','Новые','Старые']:['Популярные','Лучшие','Новые','Старые'])}`;
 const total=USE_MOCK?list.length:state.catalog.totalResults;
 const firstLoad=!USE_MOCK&&state.catalog.loading&&!list.length;
 const count=firstLoad?'':`Найдено: ${fmt(total)}`;
 const body=list.length?list.map(echoMovieRow).join(''):firstLoad?`<div class="panel list-loading">${loader('Загружаем фильмы…')}</div>`:state.catalog.error?'':'<div class="panel muted empty-state">Фильмов по выбранным фильтрам нет. Попробуйте изменить жанр, год или страну.</div>';
 const more=!USE_MOCK&&list.length&&state.catalog.page<state.catalog.totalPages&&state.catalog.page<1000?`<div class="catalog-more"><button class="btn ghost" id="catalogMore" ${state.catalog.loading?'disabled':''}>${state.catalog.loading?'<span class="echo-spinner small" aria-hidden="true"></span>Загрузка…':'Показать ещё фильмы'}</button></div>`:'';
 return siteShell(`<main class="page"><section class="catalog-head"><div><h1>Каталог</h1></div><div class="filters">${filters}</div></section><div class="count">${count}</div><div class="movie-list">${body}</div>${state.catalog.error?`<div class="panel muted error-state" role="alert">${escapeHtml(state.catalog.error)} <button class="quiet" id="catalogRetry">Повторить</button></div>`:''}${more}</main>`);
}

function searchPage(){
 const raw=state.searchQuery,q=raw.toLowerCase().trim();
 const has=s=>String(s||'').toLowerCase().includes(q);
 const films=q?(USE_MOCK?MOVIES.filter(m=>has(m.title)||has(m.director)||has(m.genre)||has(m.country)):state.searchResults.ids.map(id=>MOVIES[id]).filter(Boolean)):[];
 const people=q?Object.values(USER_PROFILES).map(u=>({name:u.nickname,role:u.kind==='media'?'Медиа':`Пользователь · ${levelName(u.points)}`,img:avatarOf(u.avatar,u.nickname),type:u.kind==='media'?'media':'user'})).filter(p=>has(p.name)||has(p.role)):[];
 const authorNames=[...new Set([...Object.keys(AUTHOR_PROFILES),...MOVIES.map(m=>m.director).filter(Boolean)])];
 const authors=q?authorNames.map(n=>{const a=getAuthorProfile(n);return {name:n,role:`${a.verified?'Автор':'Из титров'} · ${a.specialty}`,img:avatarOf(a.image,n),type:'author'}}).filter(p=>has(p.name)||has(p.role)):[];
 const TABS=[['all','Все'],['films','Фильмы'],['people','Люди'],['authors','Авторы']];
 const tab=TABS.some(([k])=>k===state.searchTab)?state.searchTab:'all';
 const show={films:tab==='all'||tab==='films',people:tab==='all'||tab==='people',authors:tab==='all'||tab==='authors'};
 const total=(show.films?(USE_MOCK?films.length:state.searchResults.totalResults):0)+(show.people?people.length:0)+(show.authors?authors.length:0);
 const row=p=>`<button class="search-person-row" data-search-profile="${p.type}" ${p.type==='author'?`data-author-name="${escapeHtml(p.name)}"`:`data-user-name="${escapeHtml(p.name)}"`}><img src="${p.img}" data-name="${escapeHtml(p.name)}" alt=""><div><strong>${escapeHtml(p.name)}</strong><span>${escapeHtml(p.role)}</span></div><b>Открыть →</b></button>`;
 const where={all:'',films:' среди фильмов',people:' среди людей',authors:' среди авторов'}[tab];
 const visibleResults=(show.films?films.length:0)+(show.people?people.length:0)+(show.authors?authors.length:0);
 const results=visibleResults?`<div class="search-catalog-results">${show.films?films.map(echoMovieRow).join(''):''}${show.people?people.map(row).join(''):''}${show.authors?authors.map(row).join(''):''}</div>`:state.searchResults.loading&&show.films?`<div class="search-empty panel list-loading">${loader('Ищем фильмы…')}</div>`:`<div class="search-empty panel"><strong>Ничего не найдено${where}.</strong></div>`;
 const more=!USE_MOCK&&show.films&&state.searchResults.page<state.searchResults.totalPages?`<div class="catalog-more"><button class="btn ghost" id="searchMore" ${state.searchResults.loading?'disabled':''}>${state.searchResults.loading?'<span class="echo-spinner small" aria-hidden="true"></span>Загрузка…':'Показать ещё результаты'}</button></div>`:'';
 return siteShell(`<main class="page search-results-page"><label class="m-search m-only">${ICON.search}<input id="mSearchInput" type="search" enterkeyhint="search" value="${escapeHtml(state.searchQuery)}" placeholder="Фильмы, люди, авторы" autocomplete="off" aria-label="Поиск фильмов, людей, авторов">${state.searchQuery?'<button type="button" class="m-search-clear" data-search-clear aria-label="Очистить">×</button>':''}</label><section class="catalog-head"><div><span class="eyebrow">Поиск</span><h1>${q?`Результаты для «${escapeHtml(raw)}»`:'Начните вводить запрос'}</h1></div><div class="search-tabs">${TABS.map(([k,l])=>`<button data-search-tab="${k}" class="${tab===k?'active':''}">${l}</button>`).join('')}</div></section>${q.length>=2?`<div class="count">${state.searchResults.loading&&!visibleResults?'':`Найдено: ${fmt(total)}`}</div>${results}${state.searchResults.error?`<div class="panel muted" role="alert">${escapeHtml(state.searchResults.error)} <button class="quiet" id="searchRetry">Повторить</button></div>`:''}${more}`:'<div class="search-empty panel"><strong>Введите минимум два символа названия фильма или имени.</strong></div>'}</main>`);
}

/* Пустой топ-5: та же сетка и высота, что у заполненного (призрачные карточки + подпись поверх). */
function prefGhostRow(kind,text,action=''){
 const ghost=kind==='movies'
  ?'<div class="pref-card pref-card--ghost"><span class="pref-ghost-poster"></span><span class="pref-ghost-line"></span></div>'
  :'<div class="person-card person-card--ghost"><span class="pref-ghost-circle"></span><span class="pref-ghost-line"></span><span class="pref-ghost-line short"></span></div>';
 return `<div class="pref-ghost pref-ghost--${kind}"><div class="${kind==='movies'?'pref-row':'people-row'} pref-ghost-row" aria-hidden="true">${ghost.repeat(5)}</div><div class="pref-ghost-msg"><strong>Пока ничего не выбрано</strong><span>${text}</span>${action}</div></div>`;
}
function profilePage(own=true){
 const u=own?state.user:otherUserProfile(state.otherUser);
 const points=Number(u.points)||0;
 const lvl=levelFor(points),f=lvl;const next=FIREFLIES.find(x=>x.threshold>points);const progress=next?((points-lvl.threshold)/(next.threshold-lvl.threshold))*100:100;
 let userReviews=REVIEWS.filter(r=>own?isOwnReview(r):r.user===u.nickname);if(own)userReviews.push(...PENDING_REVIEWS.filter(r=>!userReviews.includes(r)));userReviews=reviewSort(userReviews,state.profileSort);
 const userRatings=(u.ratings||[]).map(r=>({m:MOVIES[r.movieId],order:r.order,score:r.score,parts:r.parts||[]})).filter(r=>r.m).sort((a,b)=>state.profileSort==='Популярные'?b.m.ratings-a.m.ratings:state.profileSort==='Старые'?b.order-a.order:a.order-b.order);
 const editPrefs=own?'<button class="quiet pref-ghost-action" data-edit-section="preferences">Выбрать →</button>':'';
 const kindLabel=own?'Ваш профиль':u.kind==='media'?'Профиль медиа':'Профиль пользователя';
 const stats=[['Рецензии',u.stats?.reviews||0],['Оценки',u.stats?.ratings||0],['Получено лайков',u.stats?.receivedLikes||0],['Поставлено лайков',u.stats?.givenLikes||0],['Авторские лайки',u.stats?.authorLikesReceived||0]];
 const movieRow=u.prefMovies.length?`<div class="pref-row">${u.prefMovies.filter(id=>MOVIES[id]).map(id=>`<button class="pref-card" data-movie="${id}"><img src="${MOVIES[id].poster}" alt=""><strong>${escapeHtml(MOVIES[id].title)}</strong></button>`).join('')}</div>`:prefGhostRow('movies',own?'Добавьте фильмы, которые лучше всего описывают ваш вкус.':'Пользователь пока не выбрал любимые фильмы.',editPrefs);
 const personRow=(ids,list,role,emptyOwn,emptyOther)=>ids.length?`<div class="people-row">${ids.filter(id=>list[id]).map(id=>`<button class="person-card" data-author-name="${escapeHtml(list[id][0])}">${avatarImg(list[id][1],list[id][0])}<strong>${escapeHtml(list[id][0])}</strong><span>${role}</span></button>`).join('')}</div>`:prefGhostRow('people',own?emptyOwn:emptyOther,editPrefs);
 const reviewsBody=userReviews.length?userReviews.map(r=>reviewCard(r,'profile')).join(''):`<div class="panel muted empty-state">${own?'У вас пока нет рецензий. Откройте фильм и расскажите, что в нём увидели.':'Пользователь пока не написал рецензий.'}${own?' <button class="quiet" data-page="catalog">Перейти в каталог →</button>':''}</div>`;
 const ratingsBody=userRatings.length?userRatings.map(({m,score,parts})=>`<button class="rating-row" data-movie="${m.id}"><img src="${m.poster}" alt=""><div><strong>${escapeHtml(m.title)}</strong><span>${escapeHtml([m.year,m.director].filter(Boolean).join(' · '))}</span></div>${rating(score,parts,'ratings')}</button>`).join(''):`<div class="panel muted empty-state">${own?'Вы пока не ставили оценок без рецензии. Оценить фильм можно на его странице — достаточно выставить пять параметров.':'Пользователь пока не ставил оценок без рецензии.'}${own?' <button class="quiet" data-page="catalog">Перейти в каталог →</button>':''}</div>`;
 return siteShell(`<main class="page"><section class="profile-grid"><div class="panel profile-card"><div class="aura" style="--glow:${f.glow}"></div><div class="profile-card-head"><span class="eyebrow">${kindLabel}</span><div class="profile-actions">${own?`<button class="quiet" data-edit-section="profile"><span class="d-only">Редактировать профиль</span><span class="m-only">Изменить</span></button><button class="quiet logout-danger" id="logout">Выйти</button>`:''}</div></div><div class="profile-top"><div class="avatar-wrap">${avatarImg(u.avatar,u.nickname,`alt="Аватар ${escapeHtml(u.nickname)}"`)}${firefly(f,34)}</div></div><h1>${escapeHtml(u.nickname)}</h1>${u.bio?`<p class="bio">${escapeHtml(u.bio)}</p>`:own?'<p class="bio muted">Расскажите о себе в редактировании профиля.</p>':''}<span class="muted small">На платформе с ${escapeHtml(u.joined||'—')}</span><button class="level" id="levels" data-level-points="${points}" style="--fly:${f.color};--glow:${f.glow}"><div>${firefly(f,30)}<span><small>СТАТУС</small><strong>${lvl.name}</strong></span></div><b>${fmt(points)} баллов</b><span class="sub">Лестница статусов и баллы →</span></button><div class="progress"><i style="width:${progress}%;background:${f.color}"></i><span>${next?`${fmt(next.threshold-points)} до уровня «${next.name}»`:'Максимальный уровень'}</span></div><div class="stats">${stats.map(([n,v])=>`<div><b>${fmt(v)}</b><span>${n}</span></div>`).join('')}</div></div><div class="panel taste"><div class="taste-head"><span class="eyebrow">Предпочтения</span>${own?'<button class="quiet" data-edit-section="preferences"><span class="d-only">Изменить предпочтения</span><span class="m-only">Изменить</span></button>':''}</div><h3>Топ-5 фильмов</h3>${movieRow}<h3>Топ-5 режиссёров</h3>${personRow(u.prefDirectors,PEOPLE.directors,'Режиссёр','Добавьте режиссёров, чьи фильмы вам близки.','Пользователь пока не выбрал любимых режиссёров.')}<h3>Топ-5 актёров</h3>${personRow(u.prefActors,PEOPLE.actors,'Актёр','Добавьте актёров, за чьей игрой вы следите.','Пользователь пока не выбрал любимых актёров.')}</div></section><section class="profile-content"><div class="toolbar"><div class="tabs"><button data-profile-tab="reviews" class="${state.profileTab==='reviews'?'active':''}">Рецензии</button><button data-profile-tab="ratings" class="${state.profileTab==='ratings'?'active':''}">Оценки</button></div><div class="profile-sort"><span>Сортировка</span><select id="profileSort" aria-label="Сортировка"><option ${state.profileSort==='Новые'?'selected':''}>Новые</option><option ${state.profileSort==='Популярные'?'selected':''}>Популярные</option><option ${state.profileSort==='Старые'?'selected':''}>Старые</option></select></div></div>${state.profileTab==='reviews'?`<div class="review-list">${reviewsBody}</div>`:`<div class="rating-list">${ratingsBody}</div>`}</section></main>`)
}

function editProfilePage(){const isProfile=state.editSection==='profile';const catalog=state.editPrefType==='movies'?MOVIES:state.editPrefType==='directors'?PEOPLE.directors.map((p,i)=>({id:i,title:p[0],img:p[1]})):PEOPLE.actors.map((p,i)=>({id:i,title:p[0],img:p[1]}));const selected=state.editPrefType==='movies'?state.user.prefMovies:state.editPrefType==='directors'?state.user.prefDirectors:state.user.prefActors;const q=state.editQuery.toLowerCase();const list=catalog.filter(x=>(x.title||'').toLowerCase().includes(q));return siteShell(`<main class="page-narrow"><div class="section-head"><div><span class="eyebrow">Редактирование</span><h2>Профиль</h2></div><button class="quiet" data-page="profile">← Вернуться в профиль</button></div><section class="panel edit-shell"><div class="edit-nav"><button data-edit-tab="profile" class="${isProfile?'active':''}">Профиль</button><button data-edit-tab="preferences" class="${!isProfile?'active':''}">Предпочтения</button></div>${isProfile?`<div class="edit-grid"><div class="avatar-upload"><img class="avatar-preview" id="avatarPreview" src="${avatarOf(state.editAvatar,state.user.nickname)}" alt="Предпросмотр аватара"><label class="upload-box"><strong>Изменить аватар</strong><span>PNG или JPG. Предпросмотр появится сразу.</span><input id="avatarFile" type="file" accept="image/*"></label></div><div class="form-stack"><label><span>Никнейм</span><input id="editNick" value="${escapeHtml(state.user.nickname)}" maxlength="30"></label><label><span>Био</span><textarea id="editBio" rows="6" maxlength="320">${escapeHtml(state.user.bio)}</textarea></label><div><button class="btn primary" id="saveProfile">Сохранить изменения</button></div></div></div>`:`<div><div class="edit-pref-category"><button data-pref-type="movies" class="${state.editPrefType==='movies'?'active':''}">Фильмы</button><button data-pref-type="directors" class="${state.editPrefType==='directors'?'active':''}">Режиссёры</button><button data-pref-type="actors" class="${state.editPrefType==='actors'?'active':''}">Актёры</button></div><div class="edit-search"><span aria-hidden="true">⌕</span> <input id="prefSearch" value="${escapeHtml(state.editQuery)}" placeholder="Найти..." aria-label="Найти в списке"></div><div class="edit-pref-count">Выбрано ${selected.length} из 5</div><div class="choice-grid">${list.length?'':'<div class="panel muted empty-state">Ничего не найдено. Измените запрос.</div>'}${list.map(x=>{const id=x.id;const title=x.title;const img=x.poster||x.img;const sel=selected.includes(id);return `<button class="choice ${state.editPrefType!=='movies'?'person-choice':''} ${sel?'selected':''}" data-choice="${id}" aria-pressed="${sel}">${state.editPrefType==='movies'?`<img src="${img}" alt="">`:avatarImg(img,title)}<div><strong>${escapeHtml(title)}</strong><span>${sel?'Добавлено в топ':'Добавить в топ-5'}</span></div><b>${sel?'✓':'+'}</b></button>`}).join('')}</div></div>`}</section></main>`)}

function fireflyModal(points=state.user?.points||0){
 const cur=levelFor(points);
 return `<div class="overlay" id="overlay"><div class="modal status-modal" role="dialog" aria-modal="true" aria-labelledby="statusTitle"><div class="modal-head"><div><span class="eyebrow">Статус на платформе</span><h2 id="statusTitle">Лестница статусов</h2></div><button id="closeModal" aria-label="Закрыть">×</button></div><p class="status-modal-note">Светлячок показывает ваш текущий уровень и меняется сам, когда вы набираете баллы.</p><div class="level-ladder">${FIREFLIES.map(f=>{const unlocked=points>=f.threshold;const current=f.level===cur.level;return `<div class="fly-card ${current?'active':''} ${!unlocked?'locked':''}">${firefly(f,46,!unlocked)}<div><strong>Уровень ${f.level} · ${f.name}</strong><span class="threshold"><b>${fmt(f.threshold)} баллов</b></span></div><b class="fly-state">${current?'Текущий':unlocked?'Пройден':'<span aria-label="Закрыт">🔒</span>'}</b></div>`}).join('')}</div><div class="points-guide"><div><span class="eyebrow">Как получать баллы</span></div>${STATUS_ACTIONS.map(([name,value,top])=>`<div class="point-action ${top?'top':''}"><span>${name}</span><strong>${value}</strong></div>`).join('')}</div></div></div>`
}

function ownReviewFor(movieId){
 if(!state.userLogged||!state.user)return null;
 return PENDING_REVIEWS.find(r=>r.movieId===movieId&&isOwnReview(r))||REVIEWS.find(r=>r.movieId===movieId&&isOwnReview(r))||null;
}
function ownRatingFor(movieId){return state.userLogged?(state.user?.ratings||[]).find(r=>r.movieId===movieId)||null:null}
/* После отправки на странице фильма показывается сама рецензия и её статус проверки. */
function ownReviewPanel(r){
 const pending=r.published===false;
 const text=pending
  ?(r.moderation==='REJECTED'?'Модератор не опубликовал рецензию. Она видна только вам.':'Модератор проверит текст, после этого рецензия появится в общем списке. Пока её видите только вы — здесь и в профиле.')
  :'Рецензия прошла проверку и видна всем в списке рецензий ниже.';
 return `<section class="panel own-review-panel ${pending?'is-pending':'is-published'}" id="ownReview"><div class="own-review-head"><h2>Ваша рецензия</h2>${moderationBadge(r)}</div><p class="own-review-note">${text}</p>${reviewCard(r,'own')}</section>`;
}
function authorComposerNote(){
 return `<section class="panel composer composer-author-note"><span class="eyebrow author">Эхо Studio</span><h2>Рецензии пишут зрители и медиа</h2><p class="muted">Как автор вы можете оставить авторский комментарий к своей работе и отметить рецензии лайком автора.</p><button class="btn author" data-open-studio="comments">Открыть Эхо Studio</button></section>`;
}
function moviePage(){
 const m=MOVIES[state.movieId];const list=reviewSort(REVIEWS.filter(r=>r.movieId===m.id),state.movieSort);
 const own=ownReviewFor(m.id);
 const c=m.authorComment;
 return siteShell(`<main class="page">${echoMovieSummary(m)}
<section class="crew-section"><div class="section-head"><div class="crew-heading"><span class="eyebrow">Съёмочная группа</span><div class="tabs" role="tablist" aria-label="Съёмочная группа"><button role="tab" aria-selected="${state.crewTab==='actors'}" data-crew-tab="actors" class="${state.crewTab==='actors'?'active':''}">Актёры</button><button role="tab" aria-selected="${state.crewTab==='team'}" data-crew-tab="team" class="${state.crewTab==='team'?'active':''}">Команда</button></div></div><div class="crew-controls"><button class="slider-btn" data-crew-move="-1" aria-label="Прокрутить назад">←</button><button class="slider-btn" data-crew-move="1" aria-label="Прокрутить вперёд">→</button></div></div><div class="crew-track" id="crewTrack">${(m.crew?.[state.crewTab]||[]).map(([name,role,img])=>`<button class="crew-card clickable-author" data-author-name="${escapeHtml(name)}">${avatarImg(img,name)}<strong>${escapeHtml(name)}</strong><span>${escapeHtml(state.crewTab==='team'?ruRole(role):role||'')}</span><em>Открыть профиль →</em></button>`).join('')||'<p class="muted crew-empty">Состав пока не добавлен.</p>'}</div></section>
${c?`<section class="author-note"><div class="author-note-head"><div><span>АВТОРСКИЙ КОММЕНТАРИЙ</span><h3>${escapeHtml(c.title||'Комментарий к фильму')}</h3><p class="author-by"><button class="author-inline-link author-inline-link--amber" data-author-name="${escapeHtml(c.author)}">${escapeHtml(c.author)}</button> · ${escapeHtml(c.role||'Автор')}</p></div></div><p>«${escapeHtml(c.text)}»</p><footer>${commentLike(m.id,c.likes)}<span>${escapeHtml(c.date||'')}</span></footer></section>`:''}
${state.authorLogged?authorComposerNote():own?ownReviewPanel(own):reviewComposer(m)}
${state.authorLogged?'':`<button class="m-fab m-only" data-m-fab="${own?'ownReview':'reviewComposer'}">${own?ICON.chat:ICON.star}<span>${own?'Ваша рецензия':ownRatingFor(m.id)?'Дописать рецензию':'Оценить фильм'}</span></button>`}
<section class="section reading review-reading"><div class="toolbar"><h2>Рецензии</h2><div class="profile-sort"><span>Сортировка</span><select id="movieSort" aria-label="Сортировка рецензий"><option ${state.movieSort==='Новые'?'selected':''}>Новые</option><option ${state.movieSort==='Популярные'?'selected':''}>Популярные</option><option ${state.movieSort==='Старые'?'selected':''}>Старые</option></select></div></div><div class="review-list">${list.map(r=>reviewCard(r)).join('')||'<div class="panel muted empty-state">Рецензий пока нет. Станьте первым, кто напишет о фильме.</div>'}</div></section></main>`)
}

/* Кнопка отправки отражает режим: без текста — «Сохранить оценку», с текстом — «Отправить рецензию». */
function composerMode(text){
 const hasText=!!String(text||'').trim();
 return hasText
  ?{cls:'primary',label:'Отправить рецензию',hint:'Рецензия и оценка уйдут на модерацию. Минимум 150 символов.'}
  :{cls:'rating-only',label:'Сохранить оценку',hint:'Текста нет — сохранится только оценка по пяти параметрам.'};
}
function reviewComposer(m){
 const names=ECHO_PARAMETER_NAMES;
 const saved=ownRatingFor(m.id);
 const draft=readReviewDraft(m.id)||{title:'',text:'',parts:saved?.parts?.length===5?[...saved.parts]:[5,5,5,5,5],spoiler:false};
 const parts=draft.parts;
 const hasDraft=!!(draft.title||draft.text||parts.some(n=>n!==5));
 const len=draft.text.length;
 const mode=composerMode(draft.text);
 const savedNote=saved?`<p class="composer-saved">Ваша оценка сохранена: <b>${saved.score??'—'}/100</b>. Можно дописать к ней рецензию.</p>`:'';
 return `<section class="panel composer" id="reviewComposer"><div class="composer-head"><div><span class="eyebrow">Ваше мнение</span><h2>Написать рецензию</h2>${savedNote}</div><div class="rules"><strong>Правила публикации</strong><ul><li class="rule-limit">От 150 до 10 000 символов</li><li class="rule-language">Без оскорблений и нецензурной лексики</li><li class="rule-spoiler">Спойлеры обязательно отмечаются</li></ul></div></div><div class="parameters">${names.map((n,i)=>`<div class="parameter ${i===4?'impression':''}"><span class="parameter-name">${n}<button type="button" class="param-info" data-tooltip="${PARAM_INFO[i]}" aria-label="${n}. ${PARAM_INFO[i]}">?</button></span><b id="pv${i}">${parts[i]}</b><input data-param="${i}" type="range" min="1" max="10" value="${parts[i]}" aria-label="${n}"></div>`).join('')}</div><div class="score-preview"><span>Итог при текущих значениях</span><strong id="scorePreview">${calcReviewTotal(parts)}/100</strong></div><label class="field"><span>Заголовок</span><input id="reviewTitle" maxlength="140" placeholder="Основная мысль" value="${escapeHtml(draft.title)}"></label><label class="field"><span>Содержание</span><textarea id="reviewText" maxlength="10000" placeholder="Напишите рецензию...">${escapeHtml(draft.text)}</textarea><small id="reviewCount" class="${len>0&&len<150?'bad':''}" aria-live="polite">${fmt(len)} / 10 000 · минимум 150 для рецензии${hasDraft?' · Черновик сохранён':''}</small></label><div class="composer-foot"><span class="muted small" id="composerHint">${mode.hint}</span><button class="btn ${mode.cls}" id="publishReview">${mode.label}</button></div></section>`
}

function userAuthPage(){
 const login=state.userAuth==='login';
 const pending=state.pendingReviewMovieId;
 const returnInfo=validMovieId(pending)?`<p class="auth-draft-note" role="status"><strong>Черновик сохранён.</strong> После входа или регистрации вы вернётесь к фильму «${escapeHtml(MOVIES[pending].title)}» и продолжите с того же места.</p>`:'';
 return `<main class="auth-page"><button class="auth-back" data-view="site">← ${validMovieId(pending)?'К рецензии':'На главную'}</button><section class="auth-shell"><div class="auth-info"><span class="eyebrow">Эхо</span><h1>${login?'С возвращением.':'Ваш голос о кино.'}</h1><p>${login?'Продолжайте собирать предпочтения, писать рецензии и развивать свой статус.':'Создайте аккаунт по логину и паролю. Никнейм и аватар можно оформить уже в профиле.'}</p></div><div class="auth-form"><div class="auth-tabs"><button data-user-auth="login" class="${login?'active':''}">Вход</button><button data-user-auth="register" class="${!login?'active':''}">Регистрация</button></div><h2>${login?'Войти':'Создать аккаунт'}</h2>${returnInfo}<div class="auth-fields"><label><span>Логин</span><input id="authLogin" placeholder="Ваш логин" autocomplete="username"></label><label><span>Пароль</span><input id="authPassword" type="password" placeholder="••••••••" autocomplete="${login?'current-password':'new-password'}"></label></div><button class="btn primary auth-submit" id="userSubmit">${login?'Войти':'Зарегистрироваться'}</button><p class="auth-note">Без почты. Данные профиля можно заполнить после входа.</p>${login?'<button class="auth-role-switch" data-view="authorAuth">Войти как автор →</button>':''}<div class="auth-spacer"></div></div></section></main>`
}

function authorAuthPage(){
 const login=state.authorAuth==='login';const specialtyOptions=['Режиссёр','Актёр','Актриса','Сценарист','Продюсер','Композитор','Оператор','Монтажёр','Художник-постановщик'];
 const fv=state.authorForm;
 const info=`<div class="auth-info"><span class="eyebrow author">Эхо Studio</span><h1>${login?'Вход для авторов.':'Новый автор в Эхо.'}</h1><p>Отдельный кабинет для режиссёров, актёров, сценаристов и других подтверждённых участников производства.</p></div>`;
 if(!login&&state.authorApplied){
  const a=state.authorApplied;
  return `<main class="auth-page"><button class="auth-back" data-view="site">← На главную</button><section class="auth-shell">${info}<div class="auth-form author author-applied" role="status"><span class="applied-mark" aria-hidden="true">✓</span><span class="eyebrow author">Заявка отправлена</span><h2>Спасибо, ${escapeHtml(a.name)}!</h2><p class="applied-text">Мы проверим, что вы участвовали в создании фильмов как ${escapeHtml(a.specialty.toLowerCase())}, и откроем доступ к кабинету Эхо Studio.</p><ol class="applied-steps"><li>Модератор подтверждает роль автора.</li><li>После подтверждения войдите на вкладке «Вход» с логином <b>${escapeHtml(a.login)}</b>.</li><li>В Эхо Studio можно оставить авторский комментарий и отмечать рецензии лайком автора.</li></ol><div class="applied-actions"><button class="btn author" data-view="site">Перейти на сайт Эхо</button><button class="quiet" id="appliedToLogin">Ко входу автора</button></div><div class="auth-spacer"></div></div></section></main>`;
 }
 return `<main class="auth-page"><button class="auth-back" data-view="site">← На главную</button><section class="auth-shell">${info}<div class="auth-form author"><div class="auth-tabs"><button data-author-auth="login" class="${login?'active':''}">Вход</button><button data-author-auth="register" class="${!login?'active':''}">Регистрация</button></div><h2>${login?'Войти как автор':'Регистрация автора'}</h2><div class="auth-fields">${!login?`<label><span>Имя / Публичное имя</span><input id="authorPublicName" placeholder="Имя, как в титрах" autocomplete="name" value="${escapeHtml(fv.name)}"></label><div class="auth-field"><span id="authorSpecialtyLabel">Специальность</span><div class="auth-specialty filter ${state.openAuthorSpecialty?'open':''}"><button type="button" class="filter-button auth-specialty-button" id="authorSpecialtyToggle" aria-haspopup="listbox" aria-expanded="${state.openAuthorSpecialty}" aria-labelledby="authorSpecialtyLabel"><b>${escapeHtml(state.authorSpecialty)}</b><i class="chev" aria-hidden="true">⌄</i></button>${state.openAuthorSpecialty?`<div class="filter-backdrop m-only" data-specialty-close aria-hidden="true"></div><div class="filter-menu auth-specialty-menu" role="listbox"><div class="sheet-head m-only"><strong>Специальность</strong><button type="button" data-specialty-close aria-label="Закрыть">×</button></div>${specialtyOptions.map(o=>`<button type="button" role="option" aria-selected="${o===state.authorSpecialty}" data-author-specialty="${o}" class="filter-option ${o===state.authorSpecialty?'is-selected':''}">${o}</button>`).join('')}</div>`:''}</div></div>`:''}<label><span>Логин</span><input id="authorLogin" placeholder="Логин автора" autocomplete="username" value="${escapeHtml(fv.login)}"></label><label><span>Пароль</span><input id="authorPassword" type="password" placeholder="••••••••" autocomplete="${login?'current-password':'new-password'}" value="${escapeHtml(fv.password)}"></label></div><button class="btn author auth-submit" id="authorSubmit">${login?'Войти как автор':'Подать заявку'}</button><p class="auth-note author-note-auth">${login?'Вход открывается после того, как модератор подтвердит заявку.':'После регистрации профиль проходит подтверждение роли и участия в проектах.'}</p>${login?'<button class="auth-role-switch" data-view="userAuth">Обычный вход →</button>':''}<div class="auth-spacer"></div></div></section></main>`
}


function getAuthorProfile(name){
 if(AUTHOR_PROFILES[name])return AUTHOR_PROFILES[name];

 const directed=MOVIES.filter(m=>m.director===name);
 const works=directed.map(m=>({
   id:`dir-${m.id}`,title:m.title,year:m.year,genre:m.genre,director:m.director,
   role:'Режиссёр',poster:m.poster,reviews:m.reviews,ratings:m.ratings,
   popularity:m.preferred,
   scores:{
     reviews:scoreSet(m,'reviews').total,
     ratings:scoreSet(m,'ratings').total,
     critics:scoreSet(m,'critics').total
   }
 }));
 const avg=kind=>{
   const vals=directed.map(m=>scoreSet(m,kind).total).filter(v=>v!==null&&v!==undefined);
   return vals.length?Math.round(vals.reduce((a,b)=>a+b,0)/vals.length):null
 };
 return {
   image:letterAvatar(name),
   specialty:PEOPLE.actors.some(p=>p[0]===name)&&!directed.length?'Актёр':'Режиссёр',
   verified:false,
   authorRatings:0,
   comments:0,
   authorLikes:0,
   preferred:0,
   bio:'',
   scores:{reviews:avg('reviews'),ratings:avg('ratings'),critics:avg('critics')},
   works
 };
}

/* Комментарии автора с фильмами, к которым они написаны. */
function authorCommentMovies(name){return MOVIES.filter(m=>m.authorComment&&m.authorComment.author===name)}

/* Мини-карточка работы автора: тот же вид, что в каталоге. Используется в профиле автора и в Эхо Studio. */
function workRow(w,i,name,role){
 const m=MOVIES.find(x=>x.title===w.title);
 const poster=w.poster||(m&&m.poster)||posters[i%posters.length];
 const reviews=w.reviews??m?.reviews??0,ratings=w.ratings??m?.ratings??0;
 const target=m?`data-public-movie="${m.id}"`:'';
 return `<button class="movie-row author-filmography-row" ${target}><img src="${poster}" alt="Постер: ${escapeHtml(w.title)}"><div class="movie-copy"><div class="movie-copy-top"><h3>${escapeHtml(w.title)}</h3><p>${escapeHtml([w.year,w.genre||m?.genre].filter(Boolean).join(' · '))}</p>${filmCredit(ruRole(w.role)||role,w.director||m?.director,name)}</div><span class="movie-counts">${fmt(reviews)} рецензий · ${fmt(ratings)} оценок</span></div><div class="movie-scores compact-triplet echo-score-grid">${scoreTiles({reviews:w.scores?.reviews??(m?scoreSet(m,'reviews').total:null)??null,ratings:w.scores?.ratings??(m?scoreSet(m,'ratings').total:null)??null,critics:w.scores?.critics??(m?scoreSet(m,'critics').total:null)??null})}</div></button>`;
}
/* Роль автора в фильме и режиссёр фильма — разные строки и разный вид, чтобы их не путали. */
function filmCredit(role,director,name){
 const showDirector=director&&director!==name;
 return `<div class="film-credit"><span class="film-credit-role"><small>Роль автора</small><b>${escapeHtml(role||'Участник')}</b></span>${showDirector?`<span class="film-credit-director"><small>Режиссёр фильма</small>${escapeHtml(director)}</span>`:''}</div>`;
}
function authorPublicProfileContent(name,{studio=false}={}){
 const a=getAuthorProfile(name);
 const ownPublic=!studio&&state.authorLogged&&state.authorAccount===name;
 const staticFav=studio||ownPublic;
 const verified=!!a.verified||studio;
 const preferredCount=(a.preferred||0)+(PREF_DELTA[name]||0);
 const role=authorRole(a);
 let works=[...(a.works||[])];
 works.sort((x,y)=>state.authorFilmSort==='Старые'?x.year-y.year:state.authorFilmSort==='Популярные'?(y.popularity||0)-(x.popularity||0):y.year-x.year);

 const header=studio
   ? `<header class="studio-author-header"><div><span class="eyebrow author">Эхо Studio</span><h1>Мой профиль автора</h1></div><button class="quiet" data-open-author-public>Открыть публичную страницу ↗</button></header>`
   : `<div class="public-author-backline"><button class="quiet" data-author-back>${escapeHtml(state.authorReturn?.label||'← На главную')}</button>${ownPublic?'<span class="author-own-actions"><button class="quiet author-studio-link" data-open-studio="profile">Открыть в Эхо Studio →</button><button class="quiet logout-danger" data-author-logout>Выйти</button></span>':''}</div>`;

 const filmography=works.map((w,i)=>workRow(w,i,name,role)).join('');
 const sortControl=`<label class="author-film-sort"><span>Сортировка</span><select id="authorFilmSort" aria-label="Сортировка фильмографии">${['Новые','Старые','Популярные'].map(v=>`<option ${state.authorFilmSort===v?'selected':''}>${v}</option>`).join('')}</select></label>`;
 const filmList=`<div class="movie-list author-filmography-list">${filmography||'<div class="panel muted empty-state">Работы пока не добавлены.</div>'}</div>`;
 const workMovies=(a.works||[]).map(w=>MOVIES.find(x=>x.title===w.title)).filter(Boolean);
 const averages=`<div class="author-score-section"><span class="eyebrow author">Средние оценки работ</span>${scoreBlocks(averageScoreSets(workMovies,a.scores||{}),{cls:'author-score-blocks'})}</div>`;

 /* Автор из титров, ещё не зарегистрированный в Эхо: без авторских лайков, оценок и комментариев. */
 if(!verified){
   return `${header}<section class="panel author-unverified">
     <div class="author-unverified-main">
       <div class="author-unverified-avatar">${avatarImg(a.image,name,`alt="${escapeHtml(name)}"`)}</div>
       <div class="author-unverified-copy">
         <span class="author-unverified-badge">Профиль из титров</span>
         <h1>${escapeHtml(name)}</h1>
         <span class="author-role">${escapeHtml(a.specialty||role)}</span>
         <p class="author-unverified-text">Страница собрана по титрам фильмов. ${escapeHtml(name)} ещё не присоединился к Эхо, поэтому авторских комментариев и лайков автора здесь нет.</p>
         <button class="quiet author-claim" data-author-claim>Это вы? Подать заявку автора →</button>
       </div>
       ${prefCounter('person:'+name,preferredCount,{interactive:!staticFav})}
     </div>
     ${averages}
   </section>
   <section class="section author-public-content"><div class="author-content-toolbar"><h2 class="author-section-title">Фильмография</h2>${sortControl}</div>${filmList}</section>`;
 }

 const comments=authorCommentMovies(name);
 const liked=REVIEWS.filter(r=>(r.authorLikes||[]).includes(name));
 const tab=['works','comments','likes'].includes(state.authorProfileTab)?state.authorProfileTab:'works';
 const tabBody=tab==='works'?filmList
   :tab==='comments'?`<div class="comment-list author-materials-list">${comments.length?comments.map(authorCommentCard).join(''):'<div class="panel muted empty-state">Автор пока не публиковал авторских комментариев.</div>'}</div>`
   :`<div class="review-list author-materials-list">${liked.length?liked.map(r=>reviewCard(r,'feed')).join(''):'<div class="panel muted empty-state">Автор пока не отмечал рецензии лайком автора.</div>'}</div>`;

 return `${header}<section class="profile-grid author-profile-layout">
   <div class="panel profile-card author-public">
     <div class="author-public-hero">
       <div class="author-public-identity">
         <div class="avatar-wrap">${avatarImg(a.image,name,`alt="${escapeHtml(name)}"`)}</div>
         <div class="author-public-details">
           <div class="author-name-line">
             <h1>${escapeHtml(name)}</h1>
             <span class="author-verified">✓ Верифицирован</span>
           </div>
           <span class="author-role">${escapeHtml(a.specialty||role)}</span>
           ${a.bio?`<p class="bio">${escapeHtml(a.bio)}</p>`:''}
         </div>
       </div>
       ${prefCounter('person:'+name,preferredCount,{interactive:!staticFav})}
     </div>
     <div class="author-public-data">
       <div class="author-public-stats">
         <div><b>${fmt(comments.length||a.comments||0)}</b><span>Авторских комментариев</span></div>
         <div><b>${fmt(liked.length||a.authorLikes||0)}</b><span>Авторских лайков</span></div>
         <div><b>${fmt(works.length)}</b><span>Работ в Эхо</span></div>
       </div>
       ${averages}
     </div>
   </div>
 </section>

 <section class="section author-public-content">
   <div class="author-content-toolbar">
     <div class="tabs" role="tablist" aria-label="Материалы автора">
       <button role="tab" aria-selected="${tab==='works'}" data-author-profile-tab="works" class="${tab==='works'?'active':''}">Фильмография</button>
       <button role="tab" aria-selected="${tab==='comments'}" data-author-profile-tab="comments" class="${tab==='comments'?'active':''}">Авторские комментарии</button>
       <button role="tab" aria-selected="${tab==='likes'}" data-author-profile-tab="likes" class="${tab==='likes'?'active':''}">Лайки автора</button>
     </div>
     ${tab==='works'?sortControl:''}
   </div>
   ${tabBody}
 </section>`;
}

function authorProfilePage(){
 const name=state.authorName||'Дени Вильнёв';
 return siteShell(`<main class="page studio-public-profile">${authorPublicProfileContent(name,{studio:false})}</main>`)
}

function studioShell(){const acc=getAuthorProfile(state.authorAccount);const nav=[['dashboard','Обзор'],['works','Фильмография'],['audience','Мнения зрителей'],['comments','Авторские комментарии'],['profile','Мой профиль']];const body=state.studioPage==='dashboard'?studioDashboard():state.studioPage==='works'?studioWorks():state.studioPage==='audience'?studioAudience():state.studioPage==='comments'?studioComments():studioProfile();return `<div class="studio-shell"><aside class="studio-side"><div class="studio-logo"><span><img src="${echoMark}" alt="" aria-hidden="true"></span><div><strong>Эхо</strong><small>STUDIO</small></div></div><nav>${nav.map(([id,l])=>`<button data-studio="${id}" class="${state.studioPage===id?'active':''}">${l}</button>`).join('')}</nav><button class="studio-site-link" data-studio-to-site title="Перейти на сайт Эхо"><span class="ssl-icon" aria-hidden="true">↗</span><span class="ssl-text">Перейти на сайт Эхо</span><span class="ssl-short">Сайт Эхо</span></button><div class="studio-side-foot"><button class="studio-account-id" data-studio="profile" title="Мой профиль">${avatarImg(acc.image,state.authorAccount)}<span><strong>${escapeHtml(state.authorAccount)}</strong><small>Автор · ${escapeHtml(authorRole(acc))}</small></span></button><button class="studio-logout" data-author-logout>Выйти</button></div></aside><div class="studio-main"><header class="m-top m-top--studio m-only"><div class="m-brand m-brand--studio"><span class="brand-mark"><img src="${echoMark}" alt="" aria-hidden="true"></span><span>Эхо <b>Studio</b></span></div><button class="m-top-link" data-studio-to-site>На сайт ↗</button></header>${body}</div>${studioMobileNav()}</div>`}
function studioDashboard(){
 const profile=getAuthorProfile(state.authorAccount);
 const works=authorMovieIds(state.authorAccount).map(id=>MOVIES[id]);
 const m=works[0];
 const scores=works.map(movie=>scoreSet(movie,'ratings').total).filter(value=>value!==null&&value!==undefined);
 const average=scores.length?Math.round(scores.reduce((sum,value)=>sum+value,0)/scores.length):'—';
 return `<main class="studio-content"><header><div><span class="eyebrow author">Обзор</span><h1>Что происходит вокруг <em>ваших работ</em>.</h1></div></header><section class="studio-metrics">${metric('В предпочтениях',fmt(profile.preferred||0),'по вашим работам')}${metric('Рецензий',fmt(works.reduce((sum,movie)=>sum+movie.reviews,0)),'по вашим работам')}${metric('Авторских лайков',fmt(profile.authorLikes||0),'поставлено зрителям')}${metric('Средняя оценка',average,'по вашим работам')}</section>${m?`<section class="studio-block"><div class="studio-head"><h2>${escapeHtml(m.title)}</h2><button class="quiet" data-studio="works">Все работы →</button></div><div class="focus"><img src="${m.poster}"><div><div class="film-metrics">${metric('Рецензий',fmt(m.reviews),'')}${metric('Оценок',fmt(m.ratings),'')}${metric('В предпочтениях',fmt(m.preferred),'')}</div>${filmAssessment(m,'studio-film-assessment')}</div></div></section>`:''}<section class="studio-block"><div class="studio-head"><h2>Последние мнения</h2><button class="quiet" data-studio="audience">Смотреть все →</button></div><div class="studio-audience">${reviewsOnAuthorWorks(state.authorAccount).slice(0,3).map(r=>studioReviewCard(r)).join('')||'<div class="panel muted">Рецензий на ваши работы пока нет.</div>'}</div></section></main>`
}

function metric(label,value,delta){return `<div class="metric"><span>${label}</span><strong>${value}</strong><small>${delta}</small></div>`}
function studioWorks(){
 const name=state.authorAccount,a=getAuthorProfile(name);
 const works=[...(a.works||[])];
 return `<main class="studio-content"><header><div><span class="eyebrow author">Фильмография</span><h1>Ваши работы</h1></div></header><section class="studio-block"><div class="movie-list author-filmography-list studio-works-list">${works.map((w,i)=>workRow(w,i,name,authorRole(a))).join('')||'<div class="panel muted empty-state">Подтверждённые работы пока не добавлены. Модератор привяжет их к профилю после проверки заявки.</div>'}</div></section></main>`;
}

function studioReviewCard(r){
 const movie=MOVIES[r.movieId];const isLong=r.text.length>210;const expanded=state.expanded.has(r.id);const spoilerOpen=state.spoilers.has(r.id);
 const body=`<div class="review-copy ${isLong&&!expanded?'is-collapsed':''}"><p class="review-text">${escapeHtml(r.text)}</p></div>${isLong?`<button class="expand-btn" data-expand="${r.id}" aria-expanded="${expanded}">${expanded?'Свернуть':'Читать полностью'}</button>`:''}`;
 const content=r.spoiler&&!spoilerOpen?`<div class="spoiler-cover"><div><strong>⚠ Рецензия содержит спойлеры</strong></div><button data-spoiler="${r.id}">Показать</button></div>`:`${r.spoiler?'<span class="spoiler-label">СПОЙЛЕРЫ</span>':''}${body}`;
 const mine=(r.authorLikes||[]).includes(state.authorAccount);
 return `<article class="review studio-review-card"><div class="review-top">${reviewPerson(r,'div')}${rating(r.total,r.parts,'reviews')}</div><button class="review-film-context" data-public-movie="${movie.id}"><img src="${movie.poster}" alt=""><span><strong>${escapeHtml(movie.title)}</strong><em>${movie.year} · ${escapeHtml(movie.director)}</em></span></button><h3>${escapeHtml(r.title)}</h3>${content}<div class="review-footer"><span class="review-likes"><button class="review-like ${state.likedReviews.has(r.id)?'liked':''}" data-like-review="${r.id}" aria-label="Нравится" aria-pressed="${state.likedReviews.has(r.id)}"><span class="like-heart" aria-hidden="true">♥</span><span class="like-count">${fmt(r.likes)}</span></button><button class="author-like ${mine?'':'is-empty'}" data-author-like="${r.id}" aria-pressed="${mine}">${mine?'★ Лайк автора':'☆ Поставить лайк автора'}</button></span><span>${r.date}</span></div></article>`;
}

function studioAudience(){
 return `<main class="studio-content studio-reading"><header><div><span class="eyebrow author">Мнения зрителей</span><h1>Рецензии на ваши работы.</h1></div></header><section class="studio-block studio-review-surface"><div class="review-list">${(()=>{const list=reviewsOnAuthorWorks(state.authorAccount);return list.length?list.map(r=>studioReviewCard(r)).join(''):'<div class="panel muted">Рецензий на ваши работы пока нет.</div>'})()}</div></section></main>`
}

function studioComments(){
 const ids=authorMovieIds(state.authorAccount);
 if(!ids.length)return `<main class="studio-content"><header><div><span class="eyebrow author">Авторские комментарии</span><h1>Комментарий к работе.</h1></div></header><section class="studio-block"><div class="panel muted empty-state">Комментарий можно оставить только к своей работе. Подтверждённые работы появятся здесь после проверки заявки.</div></section></main>`;
 const m=MOVIES[ids.includes(state.studioCommentMovie)?state.studioCommentMovie:ids[0]];
 const acc=getAuthorProfile(state.authorAccount);
 const c=m.authorComment&&m.authorComment.author===state.authorAccount?m.authorComment:null;
 const title=c?.title||'',text=c?.text||'';
 return `<main class="studio-content"><header><div><span class="eyebrow author">Авторские комментарии</span><h1>Комментарий к работе.</h1></div></header><section class="studio-comment-grid"><div class="studio-block studio-editor"><label><span>Фильм</span><div class="studio-movie-select ${state.openStudioMovieSelect?'open':''}"><button type="button" class="filter-button" id="studioMovieToggle"><b>${escapeHtml(m.title)}</b><i class="chev">⌄</i></button>${state.openStudioMovieSelect?`<div class="studio-movie-menu">${ids.map(id=>`<button type="button" class="filter-option" data-studio-movie="${id}">${escapeHtml(MOVIES[id].title)}</button>`).join('')}</div>`:''}</div></label><label><span>Заголовок</span><input id="studioCommentTitle" placeholder="Комментарий автора" value="${escapeHtml(title)}"></label><label><span>Комментарий</span><textarea id="studioComment" rows="11" placeholder="Что зрителю важно знать о работе над фильмом?">${escapeHtml(text)}</textarea></label><div class="studio-editor-foot"><span class="muted small">${c?`Опубликован ${c.date}. Новая публикация заменит его.`:'После публикации комментарий появится на странице фильма.'}</span><div class="studio-editor-actions">${c?`<button class="quiet" data-public-movie="${m.id}">Открыть на странице фильма</button>`:''}<button class="btn author" id="publishStudioComment" data-movie="${m.id}">Опубликовать</button></div></div></div><div class="studio-block"><span class="eyebrow author">Предпросмотр</span><div class="public-comment"><h3 id="studioCommentTitlePreview">${escapeHtml(title||'Комментарий автора')}</h3><p class="muted">${escapeHtml(state.authorAccount)} · ${escapeHtml(authorRole(acc))}</p><p id="studioCommentPreview" class="${text?'':'muted'}">${text?`«${escapeHtml(text)}»`:'Текст комментария появится здесь.'}</p><div class="review-footer"><span class="comment-like is-static"><span class="like-heart" aria-hidden="true">♥</span><span class="like-count">${fmt(c?.likes||0)}</span></span><span>${c?.date||'Черновик'}</span></div></div></div></section></main>`;
}

function studioProfile(){
 return `<main class="studio-content">${authorPublicProfileContent(state.authorAccount,{studio:true})}</main>`
}


function submitConfirmModal(payload){
 const ratingOnly=!payload.text;
 return `<div class="overlay" id="submitOverlay"><div class="modal submit-modal" role="dialog" aria-modal="true"><div class="modal-head"><div><span class="eyebrow">${ratingOnly?'Оценка без рецензии':'Публикация'}</span><h2>${ratingOnly?'Сохранить только оценку?':'Опубликовать рецензию?'}</h2></div><button id="closeSubmitModal" aria-label="Закрыть">×</button></div>${ratingOnly?`<div class="submit-score">${rating(payload.total,payload.parts,'ratings',true)}</div><p>Текст рецензии пуст. На странице фильма останутся только итоговый балл и значения параметров.</p>`:`<div class="submit-review-preview"><strong>${escapeHtml(payload.title||'Без заголовка')}</strong><p>${escapeHtml(payload.text.slice(0,220))}${payload.text.length>220?'…':''}</p></div>`}${ratingOnly?'':`<label class="submit-spoiler-toggle" for="confirmSpoilerToggle"><input id="confirmSpoilerToggle" type="checkbox" ${payload.spoiler?'checked':''}><span>В рецензии есть спойлеры</span></label><p class="muted small submit-moderation-note">После отправки рецензия пройдёт модерацию и появится в общем списке.</p>`}<div class="submit-actions"><button class="btn ghost" id="cancelSubmit">Отмена</button><button class="btn ${ratingOnly?'rating-only':'primary'}" id="confirmSubmit">${ratingOnly?'Сохранить оценку':'Отправить рецензию'}</button></div></div></div>`
}
let noticeTimer=null;
/* Отзывчивость: плавное раскрытие текста и короткий отклик на нажатие. */
function reduceMotion(){return matchMedia('(prefers-reduced-motion: reduce)').matches}
function pulse(el){if(!el||reduceMotion())return;el.classList.remove('is-pulse');void el.offsetWidth;el.classList.add('is-pulse');setTimeout(()=>el.classList.remove('is-pulse'),420)}
/* setCollapsed(true|false) переключает классы свёрнутого вида. Высота меняется плавно (CSS-переход),
   инлайновые !important нужны, чтобы перебить фиксированные высоты карточек на время анимации. */
function animateHeight(el,open,setCollapsed,done){
 if(!el||reduceMotion()){setCollapsed(!open);done&&done();return}
 const st=el.style,set=(k,v)=>st.setProperty(k,v,'important');
 const from=el.getBoundingClientRect().height;
 setCollapsed(!open);
 const to=el.getBoundingClientRect().height;
 if(Math.abs(to-from)<2){done&&done();return}
 if(!open)setCollapsed(false);
 set('max-height','none');set('overflow','hidden');set('height',from+'px');set('transition','none');
 void el.offsetHeight;
 const ms=Math.round(Math.min(480,Math.max(260,Math.abs(to-from)*.5)));
 set('transition',`height ${ms}ms cubic-bezier(.22,.7,.2,1)`);set('height',to+'px');
 let ended=false;
 const finish=()=>{if(ended)return;ended=true;el.removeEventListener('transitionend',onEnd);['max-height','overflow','height','transition'].forEach(k=>st.removeProperty(k));if(!open)setCollapsed(true);done&&done()};
 const onEnd=e=>{if(e.target===el&&e.propertyName==='height')finish()};
 el.addEventListener('transitionend',onEnd);setTimeout(finish,ms+80);
}
function toggleReviewText(btn){
 const id=+btn.dataset.expand,open=!state.expanded.has(id);
 open?state.expanded.add(id):state.expanded.delete(id);
 const card=btn.closest('article'),copy=card?.querySelector('.review-copy');
 if(!card||!copy){render();return}
 btn.textContent=open?'Свернуть':'Читать полностью';btn.setAttribute('aria-expanded',String(open));
 animateHeight(copy,open,collapsed=>{
  copy.classList.toggle('is-collapsed',collapsed);
  if(card.classList.contains('review-home')){card.classList.toggle('review-home-expanded',!collapsed);card.classList.toggle('review-home-compact',collapsed)}
 },()=>{
  if(open)return;
  const top=card.getBoundingClientRect().top,offset=isPhone()?64:90;
  if(top<offset)scrollTo({top:scrollY+top-offset,behavior:reduceMotion()?'auto':'smooth'});
 });
}
function showNotice(message,ms=2200){
 clearTimeout(noticeTimer);
 document.getElementById('echoNotice')?.remove();
 document.body.insertAdjacentHTML('beforeend',`<div class="echo-notice" id="echoNotice" role="status">${message}</div>`);
 noticeTimer=setTimeout(()=>document.getElementById('echoNotice')?.remove(),ms);
}

function pageHtml(){let html;if(state.view==='site'){html=state.page==='home'?homePage():state.page==='catalog'?catalogPage():state.page==='search'?searchPage():state.page==='profile'?profilePage(true):state.page==='otherProfile'?profilePage(false):state.page==='editProfile'?editProfilePage():state.page==='authorProfile'?authorProfilePage():moviePage()}else if(state.view==='userAuth'){html=userAuthPage()}else if(state.view==='authorAuth'){html=authorAuthPage()}else{html=studioShell()}return html}
function render(){document.getElementById('root').innerHTML=pageHtml();bind();syncHistory()}
/* Частичное обновление: меняются только указанные блоки, прокрутка и позиция взгляда не сбиваются. */
function patch(...selectors){
 const tmp=document.createElement('div');tmp.innerHTML=pageHtml();
 const y=scrollY;
 for(const sel of selectors){const now=document.querySelector(sel),next=tmp.querySelector(sel);if(!now||!next){render();return}now.replaceWith(next)}
 bind();
 if(scrollY!==y)scrollTo(0,y);
}

function bind(){
 document.querySelectorAll('[data-page]').forEach(b=>b.onclick=()=>{const p=b.dataset.page;if(p==='userAuth'){state.view='userAuth';render();return}if(p==='profile'&&state.authorLogged&&!state.userLogged){openAuthorProfile(state.authorAccount);return}if(p==='profile'&&!state.userLogged){state.view='userAuth';render();scrollTo(0,0);return}go(p)});
 document.querySelectorAll('[data-view]').forEach(b=>b.onclick=()=>{const next=b.dataset.view;if(next==='site')state.pendingReturn=null;if(next==='site'&&validMovieId(state.pendingReviewMovieId)){returnToReview(state.pendingReviewMovieId);return}state.view=next;if(state.view==='site')state.page='home';render();scrollTo(0,0)});
 document.querySelectorAll('[data-movie]').forEach(b=>b.onclick=()=>openMovie(+b.dataset.movie));
 document.querySelectorAll('[data-public-movie]').forEach(b=>b.onclick=()=>openMovie(+b.dataset.publicMovie));
 document.querySelectorAll('[data-profile-user]').forEach(b=>b.onclick=()=>{const n=b.dataset.profileUser;if(state.userLogged&&(n===state.user.nickname||n==='NADYA'))go('profile');else{state.otherUser=n;go('otherProfile')}});
 document.querySelectorAll('[data-author-name]').forEach(b=>b.onclick=e=>{e.stopPropagation();openAuthorProfile(b.dataset.authorName)});
 document.querySelectorAll('[data-search-profile]').forEach(b=>b.onclick=()=>{if(b.dataset.searchProfile==='author'){openAuthorProfile(b.dataset.authorName||state.authorAccount)}else{const n=b.dataset.userName||'frames.by.ira';if(state.userLogged&&(n===state.user.nickname||n==='NADYA'))go('profile');else{state.otherUser=n;go('otherProfile')}}});
 document.querySelectorAll('[data-profile-tab]').forEach(b=>b.onclick=()=>{state.profileTab=b.dataset.profileTab;render()});
 const ps=document.getElementById('profileSort');if(ps)ps.onchange=()=>{state.profileSort=ps.value;render()};
 const ms=document.getElementById('movieSort');if(ms)ms.onchange=()=>{state.movieSort=ms.value;render()};
 document.querySelectorAll('[data-expand]').forEach(b=>b.onclick=()=>toggleReviewText(b));
 /* Если текст целиком помещается в свёрнутый вид, кнопка «Читать полностью» не нужна. */
 document.querySelectorAll('.review-copy.is-collapsed').forEach(c=>{if(c.scrollHeight<=c.clientHeight+4){c.classList.remove('is-collapsed');c.closest('article')?.querySelector('[data-expand]')?.remove()}});
 document.querySelectorAll('[data-spoiler]').forEach(b=>b.onclick=()=>{state.spoilers.add(+b.dataset.spoiler);render()});
 document.querySelectorAll('[data-like-review]').forEach(b=>b.onclick=e=>{
  e.preventDefault();e.stopPropagation();
  if(!state.authorLogged&&requireViewerLogin())return;
  const id=+b.dataset.likeReview,r=REVIEWS.find(x=>x.id===id);if(!r)return;
  const apply=liked=>{
   if(liked){state.likedReviews.add(id);r.likes++;r.popular++}
   else{state.likedReviews.delete(id);r.likes=Math.max(0,r.likes-1);r.popular=Math.max(0,r.popular-1)}
   document.querySelectorAll('[data-like-review="'+id+'"]').forEach(btn=>{
    btn.classList.toggle('liked',liked);
    btn.setAttribute('aria-pressed',String(liked));
    const count=btn.querySelector('.like-count');if(count)count.textContent=fmt(r.likes);
    if(liked)pulse(btn);
   });
  };
  const wasLiked=state.likedReviews.has(id);
  apply(!wasLiked);
  api.setReviewLike(id,!wasLiked).catch(err=>{apply(wasLiked);apiFail(err)});
});
 document.querySelectorAll('[data-filter-toggle]').forEach(b=>b.onclick=()=>{const k=b.dataset.filterToggle;state.openFilter=state.openFilter===k?null:k;render()});
 document.querySelectorAll('.filter-backdrop').forEach(b=>b.onclick=e=>{e.stopPropagation();state.openFilter=null;render()});
 document.querySelectorAll('[data-filter-reset]').forEach(b=>b.onclick=()=>{Object.assign(state.filters,{genre:'Все',year:'Все',country:'Все'});state.openFilter=null;render();if(state.page==='catalog'&&!USE_MOCK)loadCatalog(true)});
 document.querySelectorAll('[data-filter-option]').forEach(b=>b.onclick=()=>{state.filters[b.dataset.filterOption]=b.dataset.value;state.openFilter=null;render();if(state.page==='catalog'&&!USE_MOCK)loadCatalog(true)});

 const openMenu=document.querySelector('.filter.open .filter-menu');
 if(openMenu){const sel=openMenu.querySelector('.is-selected');if(sel&&openMenu.classList.contains('is-scroll'))openMenu.scrollTop=Math.max(0,sel.offsetTop-openMenu.clientHeight/2+sel.offsetHeight/2);(sel||openMenu.querySelector('button'))?.focus({preventScroll:true})}
 const catalogMore=document.getElementById('catalogMore');if(catalogMore)catalogMore.onclick=()=>loadCatalog();
 const catalogRetry=document.getElementById('catalogRetry');if(catalogRetry)catalogRetry.onclick=()=>loadCatalog(state.catalog.page===0);

 ['navSearchInput','mSearchInput'].forEach(inputId=>{const nsi=document.getElementById(inputId);if(nsi)nsi.oninput=()=>{
  state.searchQuery=nsi.value;state.view='site';state.page='search';clearTimeout(searchTimer);
  state.searchResults={ids:[],page:0,totalPages:1,totalResults:0,loading:state.searchQuery.trim().length>=2,error:''};
  render();focusSearch(inputId);
  if(!USE_MOCK&&state.searchQuery.trim().length>=2)searchTimer=setTimeout(()=>loadSearch(true),350);
 }});
 document.querySelectorAll('[data-search-clear]').forEach(b=>b.onclick=()=>{state.searchQuery='';state.searchResults={ids:[],page:0,totalPages:1,totalResults:0,loading:false,error:''};render();focusSearch('mSearchInput')});
 document.querySelectorAll('[data-m-search]').forEach(b=>b.onclick=()=>{go('search');focusSearch('mSearchInput')});
 document.querySelectorAll('[data-m-back]').forEach(b=>b.onclick=()=>goBack());
 document.querySelectorAll('[data-search-tab]').forEach(b=>b.onclick=()=>{state.searchTab=b.dataset.searchTab;render()});
 const searchMore=document.getElementById('searchMore');if(searchMore)searchMore.onclick=()=>loadSearch();
 const searchRetry=document.getElementById('searchRetry');if(searchRetry)searchRetry.onclick=()=>loadSearch(state.searchResults.page===0);

 const levels=document.getElementById('levels');if(levels)levels.onclick=()=>{document.body.insertAdjacentHTML('beforeend',fireflyModal(Number(levels.dataset.levelPoints)||0));const ov=document.getElementById('overlay');const close=()=>{ov.remove();document.removeEventListener('keydown',esc);levels.focus()};const esc=e=>{if(e.key==='Escape')close()};document.addEventListener('keydown',esc);document.getElementById('closeModal').onclick=close;ov.onclick=e=>{if(e.target===ov)close()};document.getElementById('closeModal').focus()};
 const logout=document.getElementById('logout');if(logout)logout.onclick=()=>{api.logout().catch(()=>{});state.userLogged=false;state.user=null;state.likedReviews.clear();state.likedAuthorComments.clear();go('home')};

 document.querySelectorAll('[data-edit-section]').forEach(b=>b.onclick=()=>{state.editSection=b.dataset.editSection;go('editProfile')});
 document.querySelectorAll('[data-edit-tab]').forEach(b=>b.onclick=()=>{state.editSection=b.dataset.editTab;render()});
 document.querySelectorAll('[data-pref-type]').forEach(b=>b.onclick=()=>{state.editPrefType=b.dataset.prefType;state.editQuery='';render()});
 const prefSearch=document.getElementById('prefSearch');if(prefSearch)prefSearch.oninput=()=>{state.editQuery=prefSearch.value;render();const n=document.getElementById('prefSearch');if(n){n.focus();n.setSelectionRange(n.value.length,n.value.length)}};
 document.querySelectorAll('[data-choice]').forEach(b=>b.onclick=()=>{const id=+b.dataset.choice;const type=state.editPrefType;const key=type==='movies'?'prefMovies':type==='directors'?'prefDirectors':'prefActors';const before=state.user[key];const after=before.includes(id)?before.filter(x=>x!==id):[...before,id].slice(-5);state.user[key]=after;const tgt=x=>type==='movies'?{type:'movie',id:x}:{type:'person',name:PEOPLE[type][x][0]};before.filter(x=>!after.includes(x)).forEach(x=>adjustPref(tgt(x),-1));after.filter(x=>!before.includes(x)).forEach(x=>adjustPref(tgt(x),1));savePrefs();render()});
 const av=document.getElementById('avatarFile');if(av)av.onchange=e=>{const file=e.target.files&&e.target.files[0];if(!file)return;const reader=new FileReader();reader.onload=()=>{state.editAvatar=reader.result;document.getElementById('avatarPreview').src=reader.result};reader.readAsDataURL(file)};
 const saveProfile=document.getElementById('saveProfile');if(saveProfile)saveProfile.onclick=async()=>{
  const nickname=document.getElementById('editNick').value||state.user.nickname;
  const bio=document.getElementById('editBio').value||state.user.bio;
  try{
   const avatar=await api.updateProfile({nickname,bio,avatar:state.editAvatar});
   state.user.nickname=nickname;state.user.bio=bio;state.user.avatar=avatar||state.editAvatar;
   state.editAvatar=state.user.avatar;go('profile');showNotice('Профиль сохранён.');
  }catch(error){apiFail(error)}
 };

 document.querySelectorAll('[data-m-fab]').forEach(b=>{
  const target=document.getElementById(b.dataset.mFab);
  b.onclick=()=>{target?.scrollIntoView({block:'start',behavior:'smooth'});setTimeout(()=>{if(b.dataset.mFab==='reviewComposer')document.querySelector('[data-param="0"]')?.focus({preventScroll:true})},450)};
  if(target&&'IntersectionObserver' in window){fabObserver?.disconnect();fabObserver=new IntersectionObserver(([en])=>b.classList.toggle('is-hidden',en.isIntersecting),{threshold:0});fabObserver.observe(target)}
 });
 document.querySelectorAll('[data-desc-toggle]').forEach(b=>b.onclick=()=>{
  const id=+b.dataset.descToggle,open=state.descOpen!==id,box=b.closest('.m-film-desc');
  state.descOpen=open?id:null;
  if(!box){render();return}
  b.textContent=open?'Свернуть':'Читать полностью';b.setAttribute('aria-expanded',String(open));
  animateHeight(box.querySelector('p'),open,collapsed=>box.classList.toggle('is-open',!collapsed));
 });
 document.querySelectorAll('[data-crew-tab]').forEach(b=>b.onclick=()=>{if(state.crewTab===b.dataset.crewTab)return;state.crewTab=b.dataset.crewTab;patch('.crew-section')});
 document.querySelectorAll('[data-crew-move]').forEach(b=>b.onclick=()=>{const t=document.getElementById('crewTrack');if(t)t.scrollBy({left:+b.dataset.crewMove*320,behavior:'smooth'})});
 document.querySelectorAll('[data-like-comment]').forEach(b=>b.onclick=e=>{
  e.preventDefault();e.stopPropagation();
  if(!state.authorLogged&&requireViewerLogin())return;
  const id=+b.dataset.likeComment,m=MOVIES[id];if(!m?.authorComment)return;
  if(state.authorLogged&&m.authorComment.author===state.authorAccount)return;
  const apply=liked=>{
   if(liked){state.likedAuthorComments.add(id);m.authorComment.likes++}
   else{state.likedAuthorComments.delete(id);m.authorComment.likes=Math.max(0,m.authorComment.likes-1)}
   document.querySelectorAll('[data-like-comment="'+id+'"]').forEach(btn=>{
    btn.classList.toggle('liked',liked);
    btn.setAttribute('aria-pressed',String(liked));
    const count=btn.querySelector('.like-count');if(count)count.textContent=fmt(m.authorComment.likes);
    if(liked)pulse(btn);
   });
  };
  const wasLiked=state.likedAuthorComments.has(id);
  apply(!wasLiked);
  api.setAuthorCommentLike(id,!wasLiked).catch(err=>{apply(wasLiked);apiFail(err)});
});

 document.querySelectorAll('[data-param]').forEach(inp=>inp.oninput=()=>{
  document.getElementById('pv'+inp.dataset.param).textContent=inp.value;
  const parts=[0,1,2,3,4].map(i=>+document.querySelector(`[data-param="${i}"]`).value);
  document.getElementById('scorePreview').textContent=calcReviewTotal(parts)+'/100';
  saveCurrentReviewDraft();
 });
 const rt=document.getElementById('reviewText');
 if(rt)rt.oninput=()=>{
  const c=document.getElementById('reviewCount');
  c.textContent=`${fmt(rt.value.length)} / 10 000 · минимум 150 для рецензии · Черновик сохранён`;
  c.classList.toggle('bad',rt.value.length>0&&rt.value.length<150);
  const mode=composerMode(rt.value),btn=document.getElementById('publishReview'),hint=document.getElementById('composerHint');
  if(btn){btn.textContent=mode.label;btn.classList.toggle('primary',mode.cls==='primary');btn.classList.toggle('rating-only',mode.cls==='rating-only')}
  if(hint)hint.textContent=mode.hint;
  saveCurrentReviewDraft();
 };
 const titleInput=document.getElementById('reviewTitle');
 if(titleInput)titleInput.oninput=()=>{
  saveCurrentReviewDraft();
  const c=document.getElementById('reviewCount');
  if(c&&!c.textContent.includes('Черновик сохранён'))c.textContent+=' · Черновик сохранён';
 };

 const pub=document.getElementById('publishReview');if(pub)pub.onclick=()=>{
  const draft=saveCurrentReviewDraft();if(!draft)return;
  const body=draft.text.trim(),title=draft.title.trim(),parts=draft.parts,total=calcReviewTotal(parts);
  if(body&&body.length<150){showNotice('Для рецензии нужно минимум 150 символов. Черновик сохранён.');return}
  if(!state.userLogged&&!state.authorLogged){
   setPendingReviewMovie(draft.movieId);
   state.view='userAuth';
   render();scrollTo(0,0);
   return;
  }
  const payload={text:body,title,spoiler:draft.spoiler,parts,total};
  document.body.insertAdjacentHTML('beforeend',submitConfirmModal(payload));
  const ov=document.getElementById('submitOverlay');
  const close=()=>ov?.remove();
  document.getElementById('closeSubmitModal').onclick=close;
  document.getElementById('cancelSubmit').onclick=close;
  ov.onclick=e=>{if(e.target===ov)close()};
  const spoilerToggle=document.getElementById('confirmSpoilerToggle');
  if(spoilerToggle)spoilerToggle.onchange=e=>{
   writeReviewDraft(draft.movieId,{...draft,spoiler:e.target.checked});
  };
  const confirm=document.getElementById('confirmSubmit');
  confirm.onclick=()=>{
   const confirmedSpoiler=!!document.getElementById('confirmSpoilerToggle')?.checked;
   if(!body){
    confirm.disabled=true;
    api.saveRating({movieId:draft.movieId,parts}).then(res=>{
     const score=res?.finalScore??total;
     if(state.user){
      const list=state.user.ratings||(state.user.ratings=[]);
      const known=list.find(r=>r.movieId===draft.movieId);
      if(known)Object.assign(known,{score,parts});
      else{list.unshift({movieId:draft.movieId,score,parts,order:Math.min(0,...list.map(r=>r.order??0))-1});if(state.user.stats)state.user.stats.ratings=(state.user.stats.ratings||0)+1}
     }
     clearReviewDraft(draft.movieId);
     close();render();showNotice(`Оценка ${score}/100 сохранена без рецензии.`);
    }).catch(e=>{confirm.disabled=false;apiFail(e)});
    return;
   }
   confirm.disabled=true;
   api.publishReview({movieId:draft.movieId,title:title||'Без заголовка',text:body,parts,spoiler:confirmedSpoiler}).then(saved=>{
    const review=saved||{id:Date.now(),movieId:draft.movieId,...reviewerIdentity(),total,parts,title:title||'Без заголовка',text:body,likes:0,date:todayRu(),spoiler:confirmedSpoiler,popular:0,authorLikes:[],published:false,moderation:'PENDING_MODERATION'};
    if(state.user){review.userId=review.userId||state.user.id;review.user=review.user||state.user.nickname;review.avatar=review.avatar||state.user.avatar}
    if(review.published)REVIEWS.unshift(review);else PENDING_REVIEWS.unshift(review);
    clearReviewDraft(draft.movieId);
    close();render();
    document.getElementById('ownReview')?.scrollIntoView({block:'start',behavior:'smooth'});
    showNotice(review.published?'Рецензия опубликована.':'Рецензия отправлена на модерацию.',4200);
   }).catch(e=>{confirm.disabled=false;apiFail(e)});
  };
 };

 document.querySelectorAll('[data-user-auth]').forEach(b=>b.onclick=()=>{state.userAuth=b.dataset.userAuth;render()});
 const us=document.getElementById('userSubmit');if(us)us.onclick=async()=>{
   let res;
   try{res=await api.userAuth(state.userAuth,{login:document.getElementById('authLogin')?.value.trim()||'',password:document.getElementById('authPassword')?.value||''})}
   catch(e){apiFail(e);return}
   if(res?.user)state.user=res.user;
   state.likedReviews.clear();state.likedAuthorComments.clear();
   if(state.user)state.editAvatar=state.user.avatar;
   state.userLogged=true;state.authorLogged=false;
   if(validMovieId(state.pendingReviewMovieId)){
   const movieId=state.pendingReviewMovieId;
   returnToReview(movieId);
   showNotice('Вы вернулись к своей рецензии. Черновик сохранён.');
  }else if(state.pendingReturn){
   const r=state.pendingReturn;state.pendingReturn=null;
   state.movieId=r.movieId;state.authorName=r.authorName;state.authorReturn=r.authorReturn;state.otherUser=r.otherUser;
   go(r.page||'home');showNotice('Вы вошли. Теперь можно добавить в предпочтения.',3200);
  }else{
   state.view='site';state.page='profile';render();scrollTo(0,0);
  }
 };
 document.querySelectorAll('[data-author-auth]').forEach(b=>b.onclick=()=>{state.authorAuth=b.dataset.authorAuth;state.openAuthorSpecialty=false;if(state.authorAuth==='login')state.authorApplied=null;render()});
 [['authorPublicName','name'],['authorLogin','login'],['authorPassword','password']].forEach(([id,key])=>{const el=document.getElementById(id);if(el)el.oninput=()=>{state.authorForm[key]=el.value}});
 const ast=document.getElementById('authorSpecialtyToggle');if(ast)ast.onclick=()=>{state.openAuthorSpecialty=!state.openAuthorSpecialty;render();if(state.openAuthorSpecialty)document.querySelector('[data-author-specialty].is-selected')?.focus()};
 document.querySelectorAll('[data-author-specialty]').forEach(b=>b.onclick=()=>{state.authorSpecialty=b.dataset.authorSpecialty;state.openAuthorSpecialty=false;render();document.getElementById('authorSpecialtyToggle')?.focus()});
 document.querySelectorAll('[data-specialty-close]').forEach(b=>b.onclick=e=>{e.stopPropagation();state.openAuthorSpecialty=false;render()});
 const atl=document.getElementById('appliedToLogin');if(atl)atl.onclick=()=>{state.authorApplied=null;state.authorAuth='login';state.authorForm.password='';render()};
 document.querySelectorAll('[data-author-claim]').forEach(b=>b.onclick=()=>{state.view='authorAuth';state.authorAuth='register';state.authorApplied=null;if(!state.authorForm.name)state.authorForm.name=state.authorName||'';render();scrollTo(0,0)});
 const afs=document.getElementById('authorFilmSort');if(afs)afs.onchange=()=>{state.authorFilmSort=afs.value;render()};
 const as=document.getElementById('authorSubmit');if(as)as.onclick=async()=>{
  const register=state.authorAuth==='register';
  const form={login:document.getElementById('authorLogin')?.value.trim()||'',password:document.getElementById('authorPassword')?.value||'',name:document.getElementById('authorPublicName')?.value.trim()||'',specialty:state.authorSpecialty};
  if(register&&!form.name){showNotice('Укажите имя автора, как в титрах.');document.getElementById('authorPublicName')?.focus();return}
  if(!form.login||!form.password){showNotice('Введите логин и пароль.');document.getElementById(form.login?'authorPassword':'authorLogin')?.focus();return}
  let res;
  as.disabled=true;
  try{res=await api.authorAuth(state.authorAuth,form)}
  catch(e){as.disabled=false;apiFail(e);return}
  if(register){
   state.authorApplied={name:form.name,login:form.login,specialty:form.specialty};
   state.authorForm={name:'',login:form.login,password:''};
   render();scrollTo(0,0);return;
  }
  if(res?.author?.name)state.authorAccount=res.author.name;
  state.authorForm={name:'',login:'',password:''};
  state.authorLogged=true;state.userLogged=false;state.view='studio';state.studioPage='dashboard';render();scrollTo(0,0);
 };

 document.querySelectorAll('[data-studio]').forEach(b=>b.onclick=()=>{state.studioPage=b.dataset.studio;render();scrollTo(0,0)});
 const smt=document.getElementById('studioMovieToggle');if(smt)smt.onclick=()=>{state.openStudioMovieSelect=!state.openStudioMovieSelect;render()};
 document.querySelectorAll('[data-studio-movie]').forEach(b=>b.onclick=()=>{state.studioCommentMovie=+b.dataset.studioMovie;state.openStudioMovieSelect=false;render()});
 
 
 
 
 document.querySelectorAll('[data-author-profile-tab]').forEach(b=>b.onclick=()=>{
  if(state.authorProfileTab===b.dataset.authorProfileTab)return;
  const bar=b.parentElement,left=bar?.scrollLeft||0,sec=document.querySelector('.author-public-content'),h=sec?.offsetHeight||0;
  state.authorProfileTab=b.dataset.authorProfileTab;
  patch('.author-public-content');
  const nsec=document.querySelector('.author-public-content');
  if(nsec&&nsec.offsetHeight<h)nsec.style.minHeight=h+'px';
  nsec?.classList.add('is-switching');
  const nb=document.querySelector('[data-author-profile-tab="'+state.authorProfileTab+'"]'),nbar=nb?.parentElement;
  if(nbar&&nbar.scrollWidth>nbar.clientWidth){nbar.scrollLeft=left;const target=nb.offsetLeft-(nbar.clientWidth-nb.offsetWidth)/2;nbar.scrollTo({left:Math.max(0,target),behavior:reduceMotion()?'auto':'smooth'})}
 });
 const authorSort=document.getElementById('authorFilmSort');
 if(authorSort)authorSort.onchange=()=>{state.authorFilmSort=authorSort.value;render()};
 document.querySelectorAll('[data-pref-toggle]').forEach(b=>b.onclick=e=>{e.stopPropagation();togglePreference(b.dataset.prefToggle)});
 document.querySelectorAll('[data-open-author-public]').forEach(b=>b.onclick=()=>{
    openAuthorProfile(state.authorAccount);
  });
  bindAuthorSession();
}

/* v10.29: сессия автора, лайки автора в Studio, публикация авторского комментария. */
function bindAuthorSession(){
 document.querySelectorAll('[data-author-back]').forEach(b=>b.onclick=()=>goBackFromAuthor());
 document.querySelectorAll('[data-open-studio]').forEach(b=>b.onclick=()=>{state.view='studio';state.studioPage=b.dataset.openStudio||'dashboard';render();scrollTo(0,0)});
 document.querySelectorAll('[data-open-own-author]').forEach(b=>b.onclick=()=>openAuthorProfile(state.authorAccount));
 document.querySelectorAll('[data-studio-to-site]').forEach(b=>b.onclick=()=>{state.view='site';state.page='home';render();scrollTo(0,0)});
 document.querySelectorAll('[data-author-logout]').forEach(b=>b.onclick=()=>{api.logout().catch(()=>{});state.authorLogged=false;go('home');showNotice('Вы вышли из аккаунта автора.')});
 document.querySelectorAll('[data-author-like]').forEach(b=>b.onclick=e=>{
  e.preventDefault();e.stopPropagation();
  const r=REVIEWS.find(x=>x.id===+b.dataset.authorLike);if(!r)return;
  const name=state.authorAccount;r.authorLikes=r.authorLikes||[];
  const had=r.authorLikes.includes(name);
  r.authorLikes=had?r.authorLikes.filter(x=>x!==name):[...r.authorLikes,name];
  api.setAuthorLike(r.id,!had).catch(apiFail);
  if(AUTHOR_PROFILES[name])AUTHOR_PROFILES[name].authorLikes=Math.max(0,(AUTHOR_PROFILES[name].authorLikes||0)+(had?-1:1));
  render();showNotice(had?'Лайк автора снят.':'Лайк автора поставлен. Он виден рядом с обычными лайками рецензии.',5000);
 });
 const sct=document.getElementById('studioCommentTitle'),scx=document.getElementById('studioComment');
 if(sct&&scx){
  const sync=()=>{
   const tp=document.getElementById('studioCommentTitlePreview'),pp=document.getElementById('studioCommentPreview');
   if(tp)tp.textContent=sct.value.trim()||'Комментарий автора';
   if(pp){const v=scx.value.trim();pp.textContent=v?'«'+v+'»':'Текст комментария появится здесь.';pp.classList.toggle('muted',!v)}
  };
  sct.oninput=sync;scx.oninput=sync;
 }
 const pubC=document.getElementById('publishStudioComment');
 if(pubC)pubC.onclick=()=>{
  const text=scx.value.trim(),title=sct.value.trim()||'Комментарий автора';
  if(!text){showNotice('Добавьте текст комментария перед публикацией.');scx.focus();return}
  const id=+pubC.dataset.movie,m=MOVIES[id];if(!m)return;
  const acc=getAuthorProfile(state.authorAccount);
  document.body.insertAdjacentHTML('beforeend',commentConfirmModal({title,text,movie:m.title,replace:!!m.authorComment}));
  const ov=document.getElementById('commentOverlay');const close=()=>ov?.remove();
  document.getElementById('closeCommentModal').onclick=close;
  document.getElementById('cancelComment').onclick=close;
  ov.onclick=e=>{if(e.target===ov)close()};
  document.getElementById('confirmComment').onclick=()=>{
   api.publishAuthorComment({movieId:id,title,text}).then(saved=>{
    m.authorComment=saved||{title,author:state.authorAccount,role:authorRole(acc),avatar:acc.image,text,likes:0,date:todayRu()};
    state.likedAuthorComments.delete(id);
    close();render();showNotice('Авторский комментарий опубликован на странице фильма.');
   }).catch(apiFail);
  };
 };
}

document.addEventListener('error',function(e){
  const img=e.target;
  if(!img || img.tagName!=='IMG' || img.dataset.fallbackApplied)return;
  img.dataset.fallbackApplied='1';
  if(img.dataset.name!==undefined&&img.dataset.name!==''){img.src=letterAvatar(img.dataset.name);return}
  const isRound = getComputedStyle(img).borderRadius==='50%' || img.closest('.person,.avatar-wrap,.author-identity,.crew-card');
  const w=isRound?160:240, h=isRound?160:360;
  const label=isRound?'Э':'Эхо';
  const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">
    <defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#242a40"/><stop offset="1" stop-color="#0d1019"/></linearGradient></defs>
    <rect width="100%" height="100%" rx="${isRound?80:18}" fill="url(#g)"/>
    <circle cx="${w/2}" cy="${h/2}" r="${Math.min(w,h)*.14}" fill="#a98cff" opacity=".20"/>
    <text x="50%" y="52%" text-anchor="middle" dominant-baseline="middle" fill="#c9c0ff" font-family="Arial,sans-serif" font-size="${isRound?44:34}" font-weight="700">${label}</text>
  </svg>`;
  img.src='data:image/svg+xml;charset=UTF-8,'+encodeURIComponent(svg);
},true);


/* Открытые выпадающие списки закрываются по клику вне их и по Escape. */
document.addEventListener('click',e=>{
 if(!e.target.isConnected)return;
 if(state.openFilter&&!e.target.closest('.filter.open')){state.openFilter=null;render()}
 else if(state.openAuthorSpecialty&&!e.target.closest('.auth-specialty')){state.openAuthorSpecialty=false;render()}
 else if(state.openStudioMovieSelect&&!e.target.closest('.studio-movie-select')){state.openStudioMovieSelect=false;render()}
});
document.addEventListener('keydown',e=>{
 if((e.key==='ArrowDown'||e.key==='ArrowUp')&&e.target.closest?.('.filter-menu,.auth-specialty-menu,.studio-movie-menu')){
  const items=[...e.target.closest('.filter-menu,.auth-specialty-menu,.studio-movie-menu').querySelectorAll('button')];
  const i=items.indexOf(e.target);if(i<0)return;e.preventDefault();
  items[Math.max(0,Math.min(items.length-1,i+(e.key==='ArrowDown'?1:-1)))].focus();return;
 }
 if(e.key!=='Escape')return;
 if(state.openFilter){const k=state.openFilter;state.openFilter=null;render();document.querySelector(`[data-filter-toggle="${k}"]`)?.focus()}
 else if(state.openAuthorSpecialty){state.openAuthorSpecialty=false;render();document.getElementById('authorSpecialtyToggle')?.focus()}
 else if(state.openStudioMovieSelect){state.openStudioMovieSelect=false;render()}
});
/* Пока открыта клавиатура (фокус в поле ввода), нижняя панель и плавающая кнопка скрыты, чтобы не закрывать поле. */
const TEXT_INPUT='input:not([type=checkbox]):not([type=radio]):not([type=range]):not([type=file]),textarea';
document.addEventListener('focusin',e=>{if(e.target.matches?.(TEXT_INPUT))document.body.classList.add('kb-open')});
document.addEventListener('focusout',e=>{if(e.target.matches?.(TEXT_INPUT))setTimeout(()=>{if(!document.activeElement?.matches?.(TEXT_INPUT))document.body.classList.remove('kb-open')},60)});
/* Enter в полях входа отправляет форму. */
document.addEventListener('keydown',e=>{
 if(e.key!=='Enter'||e.target.tagName!=='INPUT')return;
 const id=e.target.id;
 if(['authLogin','authPassword'].includes(id))document.getElementById('userSubmit')?.click();
 if(['authorLogin','authorPassword','authorPublicName'].includes(id))document.getElementById('authorSubmit')?.click();
});

/* ===== Запуск: загрузить данные (демо или сервер) и отрисовать интерфейс ===== */
async function boot(){
 const root=document.getElementById('root');
 root.innerHTML=`<div class="app-loading">${loader('Загружаем Эхо…')}</div>`;
 try{
  const data=await api.loadInitialData();
  MOVIES=data.movies;REVIEWS=data.reviews;PENDING_REVIEWS=data.pendingReviews||[];AUTHOR_PROFILES=data.authors;PEOPLE=data.people;USER_PROFILES=data.users;DEMO_USER=data.demoUser;
  if(data.demoAuthor)state.authorAccount=data.demoAuthor;
  state.user=DEMO_USER?structuredClone(DEMO_USER):null;
  if(data.session?.role==='user'){state.user=data.session.user;state.userLogged=true}
  if(data.session?.role==='author'&&data.session.author){state.authorAccount=data.session.author.name||data.session.author;state.authorLogged=true}
  if(state.user)state.editAvatar=state.user.avatar;
  REVIEWS.forEach(r=>{if(r.likedByMe)state.likedReviews.add(r.id)});
  MOVIES.forEach(m=>{if(m.authorComment?.likedByMe)state.likedAuthorComments.add(m.id)});
  state.pendingReviewMovieId=readPendingReviewMovie();
  if(state.pendingReviewMovieId!==null&&!state.userLogged)state.view='userAuth';
  render();
 }catch(e){
  root.innerHTML=`<main class="page"><div class="panel"><h2>Не удалось загрузить данные</h2><p class="muted">${escapeHtml(e?.message||'')}</p><button class="btn primary" onclick="location.reload()">Обновить</button></div></main>`;
 }
}
// Для отладки и автотестов: откройте сайт с ?debug — состояние будет доступно как window.__echo.
if(new URLSearchParams(location.search).has('debug'))window.__echo={get state(){return state},render,openAuthorProfile,get MOVIES(){return MOVIES},get REVIEWS(){return REVIEWS}};
boot();
