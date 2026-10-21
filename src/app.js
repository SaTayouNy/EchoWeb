import './styles.css';
import {api} from './api.js';
import {avatars,posters} from './placeholders.js';
import echoMark from './assets/echo-mark.png';

/* Данные приходят из api.loadInitialData() (демо-данные или сервер) при запуске — см. boot() в конце файла. */
let MOVIES=[],REVIEWS=[],AUTHOR_PROFILES={},PEOPLE={directors:[],actors:[]},USER_PROFILES={};
let DEMO_USER=null;

const FIREFLIES=[
 {level:1,name:'Искра',threshold:0,color:'#ffd166',glow:'rgba(255,209,102,.55)'},
 {level:2,name:'Мята',threshold:500,color:'#8cf4b1',glow:'rgba(140,244,177,.55)'},
 {level:3,name:'Лазурь',threshold:1500,color:'#79d8ff',glow:'rgba(121,216,255,.55)'},
 {level:4,name:'Аметист',threshold:3000,color:'#b993ff',glow:'rgba(185,147,255,.60)'},
 {level:5,name:'Рубин',threshold:5000,color:'#ff7d9c',glow:'rgba(255,125,156,.58)'},
 {level:6,name:'Полярный',threshold:8000,color:'#f4fbff',glow:'rgba(244,251,255,.72)'}
];
const STATUS_ACTIONS=[
  ['Полноценная рецензия','+100'],
  ['Оценка без рецензии','+10'],
  ['Полученный лайк на рецензию','+3'],
  ['Лайк автора','+500']
];

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
 activeFly:3,profileTab:'reviews',profileSort:'Новые',movieSort:'Новые',crewTab:'actors',searchQuery:'',searchTab:'all',
 openFilter:null,filters:{genre:'Все',year:'Все',country:'Все',sort:'Лучшие'},expanded:new Set(),spoilers:new Set(),likedReviews:new Set(),likedAuthorComments:new Set(),helperOpen:false,helperDone:new Set(),
 editSection:'profile',editPrefType:'movies',editQuery:'',editAvatar:avatars[0],user:null,
 authorProfileTab:'works',authorSource:'all',authorLogged:false,authorAccount:'Дени Вильнёв',authorReturn:null,otherUser:'frames.by.ira',pendingReturn:null,authorName:'Дени Вильнёв',authorSpecialty:'Режиссёр',openAuthorSpecialty:false,authorFilmSort:'Новые',openStatusInfo:false,studioCommentMovie:0,openStudioMovieSelect:false
};

function firefly(f,size=26,locked=false){return `<span class="firefly" style="--fly:${f.color};--glow:${f.glow};width:${size}px;height:${size}px;opacity:${locked?.28:1}"><span class="wing l"></span><span class="wing r"></span><span class="body"></span></span>`}
function rating(total,parts=[],kind='ratings',large=false){
 const labels=ECHO_PARAMETER_NAMES;
 if(total===null||total===undefined)return `<div class="rating-badge rating-${kind} rating-empty ${large?'rating-large':''}"><strong class="rating-total">—</strong><span class="rating-empty-text">Нет оценок</span></div>`;
 return `<div class="rating-badge rating-${kind} ${large?'rating-large':''}"><strong class="rating-total">${total}</strong>${parts.length?`<span class="rating-parts">${parts.map((v,i)=>`<i class="${i===4?'impression':''}" data-param-label="${labels[i]}" tabindex="0" aria-label="${labels[i]}: ${v}">${v}</i>`).join('')}</span>`:''}</div>`
}
function compactRating(total,kind,label){
 const empty=total===null||total===undefined;
 return `<div class="compact-score echo-score-tile rating-${kind} ${empty?'empty':''}" aria-label="${label}: ${empty?'нет оценок':total}"><strong>${empty?'—':total}</strong><span class="echo-score-label">${label}</span></div>`;
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
function levelName(points){return ([...FIREFLIES].reverse().find(f=>points>=f.threshold)||FIREFLIES[0]).name}
function statusColor(r){return r.kind==='media'?'var(--cyan)':r.kind==='author'?'var(--amber)':'var(--violet)'}
function personTarget(r){return r.kind==='author'?`data-author-name="${escapeHtml(r.user)}"`:`data-profile-user="${escapeHtml(r.user)}"`}
function authorLikeBadge(r){
 const names=r.authorLikes||[];if(!names.length)return'';
 const who=escapeHtml(names.join(', '));
 return `<span class="author-like" title="Лайк автора: ${who}" aria-label="Лайк автора: ${who}">★ ${names.length>1?`Лайки авторов · ${names.length}`:'Лайк автора'}</span>`;
}
function reviewFooter(r){
 const liked=state.likedReviews.has(r.id);
 const like=state.authorLogged
  ?`<span class="review-like is-static" aria-label="Лайков: ${r.likes}"><span class="like-heart" aria-hidden="true">♥</span><span class="like-count">${fmt(r.likes)}</span></span>`
  :`<button class="review-like ${liked?'liked':''}" data-like-review="${r.id}" aria-label="Нравится" aria-pressed="${liked}"><span class="like-heart" aria-hidden="true">♥</span><span class="like-count">${fmt(r.likes)}</span></button>`;
 return `<div class="review-footer"><span class="review-likes">${like}${authorLikeBadge(r)}</span><span>${r.date}</span></div>`;
}
/* Авторы отмечают рецензии только лайком автора, обычные лайки им недоступны. */
function commentLike(id,likes){
 if(state.authorLogged)return `<span class="comment-like is-static" aria-label="Лайков: ${likes}"><span class="like-heart" aria-hidden="true">♥</span><span class="like-count">${fmt(likes)}</span></span>`;
 const liked=state.likedAuthorComments.has(id);
 return `<button class="comment-like ${liked?'liked':''}" data-like-comment="${id}" aria-pressed="${liked}"><span class="like-heart" aria-hidden="true">♥</span><span class="like-count">${fmt(likes)}</span></button>`;
}
function authorRole(a){return (a.specialty||'Автор').split('·')[0].trim()}
function authorMovieIds(name){const a=getAuthorProfile(name);return MOVIES.filter(m=>m.director===name||(a.works||[]).some(w=>w.title===m.title)).map(m=>m.id)}
function reviewsOnAuthorWorks(name){const ids=authorMovieIds(name);return REVIEWS.filter(r=>ids.includes(r.movieId)&&r.user!==name)}
function reviewerIdentity(){
 if(state.authorLogged){const a=getAuthorProfile(state.authorAccount);return {user:state.authorAccount,avatar:a.image,status:authorRole(a),kind:'author'}}
 return {user:state.user.nickname,avatar:state.user.avatar,status:levelName(state.user.points),kind:'user'};
}
function otherUserProfile(name){
 if(USER_PROFILES[name])return USER_PROFILES[name];
 const r=REVIEWS.find(x=>x.user===name);
 return {nickname:name||'Пользователь',kind:r?.kind,bio:'',avatar:r?.avatar||avatars[0],joined:'—',points:0,prefMovies:[],prefDirectors:[],prefActors:[]};
}
function authorAccountControls(){
 const a=getAuthorProfile(state.authorAccount);
 return `<div class="topbar-account"><button class="authors-btn" data-open-studio="dashboard"><span class="author-dot"></span><span>Эхо Studio</span></button><button class="account-btn author-account-btn" data-open-own-author aria-label="Профиль автора: ${escapeHtml(state.authorAccount)}"><img class="account-avatar" src="${a.image}" alt=""><span>Профиль</span></button></div>`;
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
 return `<button class="movie-row echo-movie-row" data-movie="${m.id}"><img class="echo-movie-row-poster" src="${m.poster}" alt="Постер фильма ${escapeHtml(m.title)}"><div class="movie-copy"><div class="movie-copy-top"><h3>${escapeHtml(m.title)}</h3><p>${m.year} · ${escapeHtml(m.genre)} · <span class="inline-author-name" data-author-name="${escapeHtml(m.director)}">${escapeHtml(m.director)}</span></p></div><p class="movie-copy-desc">${escapeHtml(m.desc)}</p><span class="movie-counts">${fmt(m.reviews)} рецензий · ${fmt(m.ratings)} оценок</span></div><div class="movie-scores compact-triplet echo-score-grid">${scoreTriplet(m)}</div></button>`;
}
function filmAssessment(m,extraClass=''){
 const main=scoreSet(m,'reviews');
 const entries=[['reviews','По рецензиям'],['ratings','Без рецензий'],['critics','Медиа / критики']];
 const parameters=entries.map(([kind,label])=>{
   const s=scoreSet(m,kind);
   return `<div class="echo-parameter-group rating-${kind}"><strong>${label}</strong><div class="echo-parameter-chips">${s.parts.length?s.parts.map((v,i)=>`<span class="echo-parameter-chip ${i===4?'impression':''}" data-param-label="${ECHO_PARAMETER_NAMES[i]}" tabindex="0" aria-label="${ECHO_PARAMETER_NAMES[i]}: ${v}">${v}</span>`).join(''):'<span class="muted">Нет оценок</span>'}</div></div>`;
 }).join('');
 return `<div class="echo-film-assessment ${extraClass}"><div class="echo-overall"><strong class="echo-overall-number">${main.total??'—'}</strong><div><strong>Итоговая оценка</strong><span>Средний итог по рецензиям</span></div></div><div class="echo-score-grid echo-film-score-grid">${scoreTriplet(m)}</div>
       <details class="echo-parameters"><summary><span>Пять параметров оценки</span><span>Шкала 1–10 <b aria-hidden="true">⌄</b></span></summary><div class="echo-parameters-content">${parameters}</div></details>
     </div>`;
}
function echoMovieSummary(m){
 const main=scoreSet(m,'reviews');
 const entries=[['reviews','По рецензиям'],['ratings','Без рецензий'],['critics','Медиа / критики']];
 const parameters=entries.map(([kind,label])=>{
   const s=scoreSet(m,kind);
   return `<div class="echo-parameter-group rating-${kind}"><strong>${label}</strong><div class="echo-parameter-chips">${s.parts.length?s.parts.map((v,i)=>`<span class="echo-parameter-chip ${i===4?'impression':''}" data-param-label="${ECHO_PARAMETER_NAMES[i]}" tabindex="0" aria-label="${ECHO_PARAMETER_NAMES[i]}: ${v}">${v}</span>`).join(''):'<span class="muted">Нет оценок</span>'}</div></div>`;
 }).join('');
 return `<section class="echo-film-summary">
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
function go(page){state.view='site';state.page=page;render();scrollTo(0,0)}
function currentFly(){return FIREFLIES[state.activeFly-1]}
function reviewSort(list,sort){return [...list].sort((a,b)=>sort==='Популярные'?b.popular-a.popular:sort==='Старые'?a.id-b.id:b.id-a.id)}

function reviewCard(r,context='release'){
 const movie=MOVIES[r.movieId];const isLong=r.text.length>160;const expanded=state.expanded.has(r.id);const spoilerOpen=state.spoilers.has(r.id);
 const body=`<div class="review-copy ${isLong&&!expanded?'is-collapsed':''}"><p class="review-text">${escapeHtml(r.text)}</p></div>${isLong?`<button class="expand-btn" data-expand="${r.id}">${expanded?'Свернуть':'Читать полностью'}</button>`:''}`;
 const reviewRating=rating(r.total,r.parts,'reviews');
 if(context==='profile')return `<article class="review profile-review"><div class="profile-review-head"><button class="profile-review-film" data-movie="${r.movieId}"><img src="${movie.poster}"><span><strong>${movie.title}</strong><em>${movie.director}</em></span></button>${reviewRating}</div><h3>${escapeHtml(r.title)}</h3>${r.spoiler&&!spoilerOpen?`<div class="spoiler-cover"><div><strong>⚠ Рецензия содержит спойлеры</strong></div><button data-spoiler="${r.id}">Показать</button></div>`:`${r.spoiler?'<span class="spoiler-label">СПОЙЛЕРЫ</span>':''}${body}`}${reviewFooter(r)}</article>`;
 if(context==='home')return `<article class="review review-home ${isLong&&expanded?'review-home-expanded':'review-home-compact'}"><div class="review-home-top"><button class="person" ${personTarget(r)}><img src="${r.avatar}" alt=""><div><strong>${escapeHtml(r.user)}</strong><span style="color:${statusColor(r)}">${escapeHtml(r.status)}</span></div></button>${reviewRating}</div><button class="review-film-mini" data-movie="${movie.id}"><img src="${movie.poster}"><span><strong>${movie.title}</strong><em>${movie.year} · ${movie.director}</em></span></button><h3>${escapeHtml(r.title)}</h3>${r.spoiler&&!spoilerOpen?`<div class="spoiler-cover"><div><strong>⚠ Рецензия содержит спойлеры</strong></div><button data-spoiler="${r.id}">Показать</button></div>`:`${r.spoiler?'<span class="spoiler-label">СПОЙЛЕРЫ</span>':''}${body}`}${reviewFooter(r)}</article>`;
 return `<article class="review ${context==='feed'?'review-feed':''}"><div class="review-top"><button class="person" ${personTarget(r)}><img src="${r.avatar}" alt=""><div><strong>${escapeHtml(r.user)}</strong><span style="color:${statusColor(r)}">${escapeHtml(r.status)}</span></div></button>${reviewRating}</div>${context==='feed'?`<button class="review-film-context" data-movie="${movie.id}"><img src="${movie.poster}" alt=""><span><strong>${escapeHtml(movie.title)}</strong><em>${movie.year} · ${escapeHtml(movie.director)}</em></span></button>`:''}<h3>${escapeHtml(r.title)}</h3>${r.spoiler&&!spoilerOpen?`<div class="spoiler-cover"><div><strong>⚠ Рецензия содержит спойлеры</strong></div><button data-spoiler="${r.id}">Показать</button></div>`:`${r.spoiler?'<span class="spoiler-label">СПОЙЛЕРЫ</span>':''}${body}`}${reviewFooter(r)}</article>`
}

function authorCommentCard(movie){
 const c=movie.authorComment;if(!c)return'';
 return `<article class="comment-card"><button class="comment-film" data-movie="${movie.id}"><img src="${movie.poster}"><span><strong>${movie.title}</strong><em>${movie.year} · ${movie.director}</em></span></button><button class="person author-comment-person" data-author-name="${c.author}"><img src="${c.avatar}"><div><strong>${c.author}</strong><span style="color:var(--amber)">${c.role}</span></div></button><h3>${c.title||'Комментарий автора'}</h3><p>«${c.text}»</p><footer>${commentLike(movie.id,c.likes)}<span>${c.date}</span></footer></article>`
}

function siteShell(content){
 const f=currentFly();
  const account=state.authorLogged?authorAccountControls():state.userLogged?`<button class="account-btn" data-page="profile">${firefly(f,22)}<span>Профиль</span></button>`:`<button class="account-btn" data-view="userAuth"><span style="width:8px;height:8px;border-radius:50%;background:var(--violet);box-shadow:0 0 12px rgba(169,140,255,.55)"></span><span>Войти</span></button>`;
 return `<div class="app"><header class="topbar"><button class="brand" data-page="home"><span class="brand-mark"><img src="${echoMark}" alt="" aria-hidden="true"></span><span>Эхо</span></button><div class="navlinks"><button data-page="home" class="${state.page==='home'?'active':''}">Главная</button><button data-page="catalog" class="${state.page==='catalog'?'active':''}">Каталог</button></div><label class="nav-search ${state.page==='search'?'active':''}"><span>⌕</span><input id="navSearchInput" value="${escapeHtml(state.searchQuery)}" placeholder="Поиск фильмов, людей, авторов" autocomplete="off"></label>${account}</header>${content}<footer class="site-footer"><span class="footer-brand">Эхо · кино как разговор</span><span class="footer-team">Иван Зуев <span aria-hidden="true">·</span> Киракосян Анна <span aria-hidden="true">·</span> Роман Стяжкин</span><span class="footer-links">Рецензии · оценки · предпочтения · авторы</span></footer></div>`
}

function homePage(){
 return siteShell(`<main class="page"><section class="hero"><div class="hero-copy"><span class="eyebrow">Кино как разговор</span><h1>Смотри внимательнее.<br><em>Говори точнее.</em></h1><p>Рецензии, оценки и предпочтения превращают просмотр в разговор — между зрителями, авторами и критиками.</p><div class="hero-actions"><button class="btn primary" data-page="catalog">Исследовать фильмы</button><button class="btn ghost" data-page="${state.userLogged||state.authorLogged?'profile':'userAuth'}" ${state.userLogged||state.authorLogged?'':'data-view="userAuth"'}>${state.userLogged||state.authorLogged?'Мой профиль':'Войти'}</button></div></div><div class="hero-visual"><div class="orbit o1"></div><div class="orbit o2"></div>${firefly(FIREFLIES[2],54)}<span class="hero-caption">Светлячок растёт вместе с вашим уровнем</span></div></section>
<section class="section"><div class="section-head"><h2>Сейчас обсуждают</h2></div><div class="reviews-grid">${REVIEWS.slice(0,3).map(r=>reviewCard(r,'home')).join('')}</div></section>
<section class="section"><div class="section-head"><h2>Популярное</h2><button class="quiet" data-page="catalog">Весь каталог →</button></div><div class="poster-row">${MOVIES.map(m=>`<button class="poster popular-poster" data-movie="${m.id}"><div class="poster-image"><img src="${m.poster}"><div class="poster-scores poster-total-strip"><span class="poster-total-pill rating-reviews" data-score-label="По рецензиям">${scoreSet(m,'reviews').total ?? '—'}</span><span class="poster-total-pill rating-ratings" data-score-label="Без рецензии">${scoreSet(m,'ratings').total ?? '—'}</span><span class="poster-total-pill rating-critics" data-score-label="Медиа / критики">${scoreSet(m,'critics').total ?? '—'}</span></div></div><strong>${m.title}</strong><span>${m.year} · ${m.director}</span></button>`).join('')}</div></section>
<section class="section"><div class="section-head"><h2>Последние авторские комментарии</h2></div><div class="comment-grid">${[MOVIES[0],MOVIES[3],MOVIES[4]].map(authorCommentCard).join('')}</div></section></main>`)
}

function filterControl(key,label,options){const open=state.openFilter===key;return `<div class="filter ${open?'open':''}"><span>${label}</span><button class="filter-button" data-filter-toggle="${key}"><b>${state.filters[key]}</b><i class="chev">⌄</i></button>${open?`<div class="filter-menu">${options.map(o=>`<button class="filter-option" data-filter-option="${key}" data-value="${o}">${o}</button>`).join('')}</div>`:''}</div>`}
function catalogPage(){
 let list=MOVIES.filter(m=>(state.filters.genre==='Все'||m.genre===state.filters.genre)&&(state.filters.year==='Все'||String(m.year)===state.filters.year)&&(state.filters.country==='Все'||m.country.split(', ').includes(state.filters.country)));
 list=[...list].sort((a,b)=>state.filters.sort==='Лучшие'?(scoreSet(b,'ratings').total??-1)-(scoreSet(a,'ratings').total??-1):b.year-a.year);
 return siteShell(`<main class="page"><section class="catalog-head"><div><span class="eyebrow">Каталог</span><h1>Фильмы</h1></div><div class="filters">${filterControl('genre','Жанр',['Все',...new Set(MOVIES.map(m=>m.genre))])}${filterControl('year','Год',['Все',...new Set(MOVIES.map(m=>String(m.year)))])}${filterControl('country','Страна',['Все',...new Set(MOVIES.flatMap(m=>m.country.split(', ')))])}${filterControl('sort','Сортировка',['Лучшие','Новые'])}</div></section><div class="count">Найдено: ${list.length}</div><div class="movie-list">${list.map(echoMovieRow).join('')||'<div class="panel muted">Фильмов по выбранным фильтрам нет.</div>'}</div></main>`);
}

function searchPage(){
 const raw=state.searchQuery,q=raw.toLowerCase().trim();
 const has=s=>String(s||'').toLowerCase().includes(q);
 const films=q?MOVIES.filter(m=>has(m.title)||has(m.director)||has(m.genre)||has(m.country)):[];
 const people=q?Object.values(USER_PROFILES).map(u=>({name:u.nickname,role:u.kind==='media'?'Медиа':`Пользователь · ${levelName(u.points)}`,img:u.avatar,type:u.kind==='media'?'media':'user'})).filter(p=>has(p.name)||has(p.role)):[];
 const authorNames=[...new Set([...Object.keys(AUTHOR_PROFILES),...MOVIES.map(m=>m.director)])];
 const authors=q?authorNames.map(n=>{const a=getAuthorProfile(n);return {name:n,role:`Автор · ${a.specialty}`,img:a.image,type:'author'}}).filter(p=>has(p.name)||has(p.role)):[];
 const TABS=[['all','Все'],['films','Фильмы'],['people','Люди'],['authors','Авторы']];
 const tab=TABS.some(([k])=>k===state.searchTab)?state.searchTab:'all';
 const show={films:tab==='all'||tab==='films',people:tab==='all'||tab==='people',authors:tab==='all'||tab==='authors'};
 const total=(show.films?films.length:0)+(show.people?people.length:0)+(show.authors?authors.length:0);
 const row=p=>`<button class="search-person-row" data-search-profile="${p.type}" ${p.type==='author'?`data-author-name="${escapeHtml(p.name)}"`:`data-user-name="${escapeHtml(p.name)}"`}><img src="${p.img}" alt=""><div><strong>${escapeHtml(p.name)}</strong><span>${escapeHtml(p.role)}</span></div><b>Открыть →</b></button>`;
 const where={all:'',films:' среди фильмов',people:' среди людей',authors:' среди авторов'}[tab];
 const results=total?`<div class="search-catalog-results">${show.films?films.map(echoMovieRow).join(''):''}${show.people?people.map(row).join(''):''}${show.authors?authors.map(row).join(''):''}</div>`:`<div class="search-empty panel"><strong>Ничего не найдено${where}.</strong></div>`;
 return siteShell(`<main class="page search-results-page"><section class="catalog-head"><div><span class="eyebrow">Поиск</span><h1>${q?`Результаты для «${escapeHtml(raw)}»`:'Начните вводить запрос'}</h1></div><div class="search-tabs">${TABS.map(([k,l])=>`<button data-search-tab="${k}" class="${tab===k?'active':''}">${l}</button>`).join('')}</div></section>${q?`<div class="count">Найдено: ${total}</div>${results}`:'<div class="search-empty panel"><strong>Введите название фильма, режиссёра, автора или пользователя в строке поиска сверху.</strong></div>'}</main>`);
}

function profilePage(own=true){
 const u=own?state.user:otherUserProfile(state.otherUser);
 const lvl=[...FIREFLIES].reverse().find(f=>u.points>=f.threshold)||FIREFLIES[0];const f=own?currentFly():lvl;const next=FIREFLIES.find(x=>x.threshold>u.points);const progress=next?((u.points-lvl.threshold)/(next.threshold-lvl.threshold))*100:100;
 let userReviews=REVIEWS.filter(r=>own?(r.user==='NADYA'||r.user===state.user.nickname):r.user===u.nickname);if(!userReviews.length&&own)userReviews=[REVIEWS[2]];userReviews=reviewSort(userReviews,state.profileSort);
 const userRatings=MOVIES.slice(0,5).map((m,i)=>({m,order:i,score:[92,86,78,90,94][i],parts:[9,9,10,10,9].map((v,j)=>Math.max(1,Math.min(10,v-((i+j)%2))))})).sort((a,b)=>state.profileSort==='Популярные'?b.m.ratings-a.m.ratings:state.profileSort==='Старые'?b.order-a.order:a.order-b.order);
 const emptyPref=t=>`<div class="pref-empty"><strong>Пока ничего не выбрано</strong><span>${t}</span></div>`;
 return siteShell(`<main class="page"><section class="profile-grid"><div class="panel profile-card"><div class="aura" style="--glow:${f.glow}"></div><div class="profile-top"><div class="avatar-wrap"><img src="${u.avatar}">${firefly(f,34)}</div><div class="profile-actions">${own?`<button class="quiet" data-edit-section="profile">Редактировать профиль</button><button class="quiet logout-danger" id="logout">Выйти</button>`:''}</div></div><span class="eyebrow">${own?'Ваш профиль':u.kind==='media'?'Профиль медиа':'Профиль пользователя'}</span><h1>${u.nickname}</h1><p class="bio">${u.bio}</p><span class="muted small">На платформе с ${u.joined}</span><button class="level" id="levels" data-level-points="${u.points}" style="--fly:${f.color};--glow:${f.glow}"><div>${firefly(f,30)}<span><small>СТАТУС</small><strong>${lvl.name}</strong></span></div><b>${fmt(u.points)} баллов</b><span class="sub">Лестница статусов и баллы →</span></button><div class="progress"><i style="width:${progress}%;background:${f.color}"></i><span>${next?`${fmt(next.threshold-u.points)} до уровня «${next.name}»`:'Максимальный уровень'}</span></div><div class="stats">${[['Рецензии',own?42:31],['Оценки',own?138:94],['Получено лайков',own?4821:2908],['Поставлено лайков',own?731:506],['Авторские лайки',own?18:9]].map(([n,v])=>`<div><b>${fmt(v)}</b><span>${n}</span></div>`).join('')}</div></div><div class="panel taste"><div class="taste-head"><div><span class="eyebrow">Предпочтения</span><h2>Вкус пользователя</h2></div>${own?'<button class="quiet" data-edit-section="preferences">Изменить предпочтения</button>':''}</div><h3>Топ-5 фильмов</h3>${u.prefMovies.length?`<div class="pref-row">${u.prefMovies.map(id=>`<button class="pref-card" data-movie="${id}"><img src="${MOVIES[id].poster}"><strong>${MOVIES[id].title}</strong></button>`).join('')}</div>`:emptyPref('Добавьте фильмы, которые лучше всего описывают ваш вкус.')}<h3>Топ-5 режиссёров</h3>${u.prefDirectors.length?`<div class="people-row">${u.prefDirectors.map(id=>`<button class="person-card" data-author-name="${PEOPLE.directors[id][0]}"><img src="${PEOPLE.directors[id][1]}"><strong>${PEOPLE.directors[id][0]}</strong><span>Режиссёр</span></button>`).join('')}</div>`:emptyPref('Пользователь пока не выбрал любимых режиссёров.')}<h3>Топ-5 актёров</h3>${u.prefActors.length?`<div class="people-row">${u.prefActors.map(id=>`<button class="person-card" data-author-name="${PEOPLE.actors[id][0]}"><img src="${PEOPLE.actors[id][1]}"><strong>${PEOPLE.actors[id][0]}</strong><span>Актёр</span></button>`).join('')}</div>`:emptyPref('Пользователь пока не выбрал любимых актёров.')}</div></section><section class="profile-content"><div class="toolbar"><div class="tabs"><button data-profile-tab="reviews" class="${state.profileTab==='reviews'?'active':''}">Рецензии</button><button data-profile-tab="ratings" class="${state.profileTab==='ratings'?'active':''}">Оценки</button></div>${`<div class="profile-sort"><span>Сортировка</span><select id="profileSort"><option ${state.profileSort==='Новые'?'selected':''}>Новые</option><option ${state.profileSort==='Популярные'?'selected':''}>Популярные</option><option ${state.profileSort==='Старые'?'selected':''}>Старые</option></select></div>`}</div>${state.profileTab==='reviews'?`<div class="review-list">${userReviews.map(r=>reviewCard(r,'profile')).join('')||'<div class="panel muted">Пока нет рецензий.</div>'}</div>`:`<div class="rating-list">${userRatings.map(({m,score,parts})=>`<button class="rating-row" data-movie="${m.id}"><img src="${m.poster}"><div><strong>${m.title}</strong><span>${m.director}</span></div>${rating(score,parts,'ratings')}</button>`).join('')}</div>`}</section></main>`)
}

function editProfilePage(){const isProfile=state.editSection==='profile';const catalog=state.editPrefType==='movies'?MOVIES:state.editPrefType==='directors'?PEOPLE.directors.map((p,i)=>({id:i,title:p[0],img:p[1]})):PEOPLE.actors.map((p,i)=>({id:i,title:p[0],img:p[1]}));const selected=state.editPrefType==='movies'?state.user.prefMovies:state.editPrefType==='directors'?state.user.prefDirectors:state.user.prefActors;const q=state.editQuery.toLowerCase();const list=catalog.filter(x=>(x.title||'').toLowerCase().includes(q));return siteShell(`<main class="page-narrow"><div class="section-head"><div><span class="eyebrow">Редактирование</span><h2>Профиль</h2></div><button class="quiet" data-page="profile">← Вернуться в профиль</button></div><section class="panel edit-shell"><div class="edit-nav"><button data-edit-tab="profile" class="${isProfile?'active':''}">Профиль</button><button data-edit-tab="preferences" class="${!isProfile?'active':''}">Предпочтения</button></div>${isProfile?`<div class="edit-grid"><div class="avatar-upload"><img class="avatar-preview" id="avatarPreview" src="${state.editAvatar}"><label class="upload-box"><strong>Изменить аватар</strong><span>PNG или JPG. Предпросмотр появится сразу.</span><input id="avatarFile" type="file" accept="image/*"></label></div><div class="form-stack"><label><span>Никнейм</span><input id="editNick" value="${state.user.nickname}" maxlength="30"></label><label><span>Био</span><textarea id="editBio" rows="6" maxlength="320">${state.user.bio}</textarea></label><div><button class="btn primary" id="saveProfile">Сохранить изменения</button></div></div></div>`:`<div><div class="edit-pref-category"><button data-pref-type="movies" class="${state.editPrefType==='movies'?'active':''}">Фильмы</button><button data-pref-type="directors" class="${state.editPrefType==='directors'?'active':''}">Режиссёры</button><button data-pref-type="actors" class="${state.editPrefType==='actors'?'active':''}">Актёры</button></div><div class="edit-search">⌕ <input id="prefSearch" value="${state.editQuery}" placeholder="Найти..."></div><div class="choice-grid">${list.map(x=>{const id=x.id;const title=x.title;const img=x.poster||x.img;const sel=selected.includes(id);return `<button class="choice ${state.editPrefType!=='movies'?'person-choice':''} ${sel?'selected':''}" data-choice="${id}"><img src="${img}"><div><strong>${title}</strong><span>${sel?'Добавлено в топ':'Добавить в топ-5'}</span></div><b>${sel?'✓':'+'}</b></button>`}).join('')}</div></div>`}</section></main>`)}

function fireflyModal(points=state.user.points){
 return `<div class="overlay" id="overlay"><div class="modal status-modal"><div class="modal-head"><div><span class="eyebrow">Статус на платформе</span><h2>Лестница статусов</h2></div><button id="closeModal">×</button></div><div class="level-ladder">${FIREFLIES.map(f=>{const unlocked=points>=f.threshold;return `<button class="fly-card ${state.activeFly===f.level?'active':''} ${!unlocked?'locked':''}" data-fly="${f.level}" ${!unlocked?'disabled':''}>${firefly(f,46,!unlocked)}<div><strong>Уровень ${f.level} · ${f.name}</strong><span class="threshold"><b>${fmt(f.threshold)} баллов</b></span></div><b>${unlocked?(state.activeFly===f.level?'Активен':'Выбрать'):'🔒'}</b></button>`}).join('')}</div><div class="points-guide"><div><span class="eyebrow">Как получать баллы</span></div>${STATUS_ACTIONS.map(([name,value])=>`<div class="point-action"><span>${name}</span><strong>${value}</strong></div>`).join('')}</div></div></div>`
}

function moviePage(){
 const m=MOVIES[state.movieId];const list=reviewSort(REVIEWS.filter(r=>r.movieId===m.id),state.movieSort);
 return siteShell(`<main class="page">${echoMovieSummary(m)}
<section class="crew-section"><div class="section-head"><div><span class="eyebrow">Съёмочная группа</span><div class="tabs"><button data-crew-tab="actors" class="${state.crewTab==='actors'?'active':''}">Актёры</button><button data-crew-tab="team" class="${state.crewTab==='team'?'active':''}">Команда</button></div></div><div class="crew-controls"><button class="slider-btn" data-crew-move="-1">←</button><button class="slider-btn" data-crew-move="1">→</button></div></div><div class="crew-track" id="crewTrack">${(m.crew?.[state.crewTab]||[]).map(([name,role,img])=>`<button class="crew-card clickable-author" data-author-name="${name}"><img src="${img}"><strong>${name}</strong><span>${role}</span><em>Открыть профиль →</em></button>`).join('')||'<p class="muted crew-empty">Состав пока не добавлен.</p>'}</div></section>
${m.authorComment?`<section class="author-note"><div class="author-note-head"><div><span>АВТОРСКИЙ КОММЕНТАРИЙ</span><h3>${m.authorComment.title||'Комментарий к фильму'}</h3><p class="author-by"><button class="author-inline-link author-inline-link--amber" data-author-name="${m.authorComment.author}">${m.authorComment.author}</button> · ${m.authorComment.role}</p></div></div><p>«${m.authorComment.text}»</p><footer>${commentLike(m.id,m.authorComment.likes)}<span>${m.authorComment.date}</span></footer></section>`:''}
${reviewComposer(m)}
<section class="section reading review-reading"><div class="toolbar"><h2>Рецензии</h2><div class="profile-sort"><span>Сортировка</span><select id="movieSort"><option ${state.movieSort==='Новые'?'selected':''}>Новые</option><option ${state.movieSort==='Популярные'?'selected':''}>Популярные</option><option ${state.movieSort==='Старые'?'selected':''}>Старые</option></select></div></div><div class="review-list">${list.map(r=>reviewCard(r)).join('')||'<div class="panel muted">Рецензий пока нет.</div>'}</div></section></main>`)
}

function reviewComposer(m){
 const names=['Драматургия и структура','Персонажи и исполнение','Визуальный язык','Звук и музыка','Впечатление'];
 const draft=readReviewDraft(m.id)||{title:'',text:'',parts:[5,5,5,5,5],spoiler:false};
 const parts=draft.parts;
 const hasDraft=!!(draft.title||draft.text||parts.some(n=>n!==5));
 return `<section class="panel composer" id="reviewComposer"><div class="composer-head"><div><span class="eyebrow">Ваше мнение</span><h2>Написать рецензию</h2></div><div class="rules"><strong>Правила публикации</strong><ul><li class="rule-limit">От 150 до 10 000 символов</li><li class="rule-language">Без оскорблений и нецензурной лексики</li><li class="rule-spoiler">Спойлеры обязательно отмечаются</li></ul></div></div><div class="parameters">${names.map((n,i)=>`<div class="parameter ${i===4?'impression':''}"><span class="parameter-name">${n}<button type="button" class="param-info" data-tooltip="${PARAM_INFO[i]}" aria-label="${n}. ${PARAM_INFO[i]}">?</button></span><b id="pv${i}">${parts[i]}</b><input data-param="${i}" type="range" min="1" max="10" value="${parts[i]}" aria-label="${n}"></div>`).join('')}</div><div class="score-preview"><span>Итог при текущих значениях</span><strong id="scorePreview">${calcReviewTotal(parts)}/100</strong></div><label class="field"><span>Заголовок</span><input id="reviewTitle" maxlength="140" placeholder="Основная мысль" value="${escapeHtml(draft.title)}"></label><label class="field"><span>Содержание</span><textarea id="reviewText" maxlength="10000" placeholder="Напишите рецензию...">${escapeHtml(draft.text)}</textarea><small id="reviewCount">${draft.text.length} / 10 000 · минимум 150 для рецензии${hasDraft?' · Черновик сохранён':''}</small></label><div class="composer-foot"><span class="muted small">Если текста нет, будет сохранена только параметрическая оценка.</span><button class="btn primary" id="publishReview">Отправить</button></div></section>`
}

function userAuthPage(){
 const login=state.userAuth==='login';
 const pending=state.pendingReviewMovieId;
 const returnInfo=validMovieId(pending)?`<p class="auth-draft-note" role="status"><strong>Черновик сохранён.</strong> После входа или регистрации вы вернётесь к фильму «${escapeHtml(MOVIES[pending].title)}» и продолжите с того же места.</p>`:'';
 return `<main class="auth-page"><button class="auth-back" data-view="site">← ${validMovieId(pending)?'К рецензии':'На главную'}</button><section class="auth-shell"><div class="auth-info"><span class="eyebrow">Эхо</span><h1>${login?'С возвращением.':'Ваш голос о кино.'}</h1><p>${login?'Продолжайте собирать предпочтения, писать рецензии и развивать свой статус.':'Создайте аккаунт по логину и паролю. Никнейм и аватар можно оформить уже в профиле.'}</p></div><div class="auth-form"><div class="auth-tabs"><button data-user-auth="login" class="${login?'active':''}">Вход</button><button data-user-auth="register" class="${!login?'active':''}">Регистрация</button></div><h2>${login?'Войти':'Создать аккаунт'}</h2>${returnInfo}<div class="auth-fields"><label><span>Логин</span><input id="authLogin" placeholder="Ваш логин" autocomplete="username"></label><label><span>Пароль</span><input id="authPassword" type="password" placeholder="••••••••" autocomplete="${login?'current-password':'new-password'}"></label></div><button class="btn primary auth-submit" id="userSubmit">${login?'Войти':'Зарегистрироваться'}</button><p class="auth-note">Без почты. Данные профиля можно заполнить после входа.</p>${login?'<button class="auth-role-switch" data-view="authorAuth">Войти как автор →</button>':''}<div class="auth-spacer"></div></div></section></main>`
}

function authorAuthPage(){
 const login=state.authorAuth==='login';const specialtyOptions=['Режиссёр','Актёр','Сценарист','Продюсер','Композитор','Оператор'];
 return `<main class="auth-page"><button class="auth-back" data-view="site">← На главную</button><section class="auth-shell"><div class="auth-info"><span class="eyebrow author">Эхо Studio</span><h1>${login?'Вход для авторов.':'Новый автор в Эхо.'}</h1><p>Отдельный кабинет для режиссёров, актёров, сценаристов и других подтверждённых участников производства.</p></div><div class="auth-form author"><div class="auth-tabs"><button data-author-auth="login" class="${login?'active':''}">Вход</button><button data-author-auth="register" class="${!login?'active':''}">Регистрация</button></div><h2>${login?'Войти как автор':'Регистрация автора'}</h2><div class="auth-fields">${!login?`<label><span>Имя / Публичное имя</span><input id="authorPublicName" placeholder="Имя автора"></label><label><span>Специальность</span><div class="auth-specialty filter ${state.openAuthorSpecialty?'open':''}"><button type="button" class="filter-button auth-specialty-button" id="authorSpecialtyToggle"><b>${state.authorSpecialty}</b><i class="chev">⌄</i></button>${state.openAuthorSpecialty?`<div class="filter-menu auth-specialty-menu">${specialtyOptions.map(o=>`<button type="button" data-author-specialty="${o}" class="filter-option">${o}</button>`).join('')}</div>`:''}</div></label>`:''}<label><span>Логин</span><input id="authorLogin" placeholder="Логин автора" autocomplete="username"></label><label><span>Пароль</span><input id="authorPassword" type="password" placeholder="••••••••"></label></div><button class="btn author auth-submit" id="authorSubmit">${login?'Войти как автор':'Подать заявку'}</button><p class="auth-note author-note-auth">${login?'':'После регистрации профиль проходит подтверждение роли и участия в проектах.'}</p>${login?'<button class="auth-role-switch" data-view="userAuth">Обычный вход →</button>':''}<div class="auth-spacer"></div></div></section></main>`
}


function getAuthorProfile(name){
 if(AUTHOR_PROFILES[name])return AUTHOR_PROFILES[name];

 const directed=MOVIES.filter(m=>m.director===name);
 const seed=[...name].reduce((a,c)=>a+c.charCodeAt(0),0);
 const av=avatars[seed%avatars.length];
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
   image:av,
   specialty:'Режиссёр',
   verified:true,
   authorRatings:Math.max(4,directed.length*7),
   comments:Math.max(1,directed.length*3),
   authorLikes:Math.max(12,directed.length*28),
   preferred:directed.reduce((a,m)=>a+m.preferred,0)||8200,
   bio:'',
   scores:{reviews:avg('reviews'),ratings:avg('ratings'),critics:avg('critics')},
   works
 };
}

function getAuthorPreferenceMovies(profile){
 const ids=[];
 (profile.works||[]).forEach(w=>{
   const m=MOVIES.find(x=>x.title===w.title);
   if(m&&!ids.includes(m.id))ids.push(m.id);
 });
 for(const id of [0,1,2,3,4])if(ids.length<3&&!ids.includes(id))ids.push(id);
 return ids.slice(0,3);
}

function authorPublicProfileContent(name,{studio=false}={}){
 const a=getAuthorProfile(name);
  const ownPublic=!studio&&state.authorLogged&&state.authorAccount===name;
  const staticFav=studio||ownPublic;
 const preferredCount=(a.preferred||0)+(PREF_DELTA[name]||0);
 let works=[...(a.works||[])];
 works.sort((x,y)=>state.authorFilmSort==='Старые'?x.year-y.year:state.authorFilmSort==='Популярные'?(y.popularity||0)-(x.popularity||0):y.year-x.year);
 const sourceMovies=state.authorSource==='works'
   ? MOVIES.filter(m=>(a.works||[]).some(w=>w.title===m.title))
   : MOVIES;

 const header=studio
   ? `<header class="studio-author-header"><div><span class="eyebrow author">Эхо Studio</span><h1>Мой профиль автора</h1></div><button class="quiet" data-open-author-public>Открыть публичную страницу ↗</button></header>`
   : `<div class="public-author-backline"><button class="quiet" data-author-back>${escapeHtml(state.authorReturn?.label||'← На главную')}</button>${ownPublic?'<span class="author-own-actions"><button class="quiet author-studio-link" data-open-studio="profile">Открыть в Эхо Studio →</button><button class="quiet logout-danger" data-author-logout>Выйти</button></span>':''}</div>`;

 const reviewTitle=name==='Дени Вильнёв'
   ? 'Почему мне важен ритм большого кино'
   : 'О работе внутри фильма';
 const reviewText=name==='Дени Вильнёв'
   ? 'Масштаб работает только тогда, когда помогает почувствовать внутреннее состояние персонажа.'
   : 'Участие в фильме — это работа внутри общего языка картины. Важно, чтобы каждый элемент усиливал ритм, атмосферу и замысел всей команды.';
 const firstWork=works[0];
 const authorRating=firstWork?.scores
   ? rating(firstWork.scores.reviews,[8,9,9,9,9],'reviews')
   : rating(a.scores.reviews||88,[8,9,9,9,9],'reviews');

 const filmography=works.map((w,i)=>{
   const m=MOVIES.find(x=>x.title===w.title);
   const poster=w.poster||(m&&m.poster)||posters[i%posters.length];
   const reviews=w.reviews??m?.reviews??0;
   const ratings=w.ratings??m?.ratings??0;
   const target=m?`data-public-movie="${m.id}"`:'';
   return `<button class="movie-row author-filmography-row" ${target}>
     <img src="${poster}" alt="Постер: ${escapeHtml(w.title)}">
     <div class="movie-copy">
       <div class="movie-copy-top">
         <h3>${escapeHtml(w.title)}</h3>
         <p>${w.year} · ${escapeHtml(w.genre||m?.genre||'Фильм')}</p>
         <p class="author-filmography-credit">${escapeHtml(w.role||a.specialty.split('·')[0].trim())} · ${escapeHtml(w.director||m?.director||'')}</p>
       </div>
       <span class="movie-counts">${fmt(reviews)} рецензий · ${fmt(ratings)} оценок</span>
     </div>
     <div class="movie-scores compact-triplet echo-score-grid">${scoreTiles({reviews:w.scores?.reviews??a.scores.reviews,ratings:w.scores?.ratings??a.scores.ratings,critics:w.scores?.critics??a.scores.critics})}</div>
   </button>`;
 }).join('');

 return `${header}<section class="profile-grid author-profile-layout">
   <div class="panel profile-card author-public">
     <div class="author-public-hero">
       <div class="author-public-identity">
         <div class="avatar-wrap"><img src="${a.image}" alt="${escapeHtml(name)}"></div>
         <div class="author-public-details">
           <div class="author-name-line">
             <h1>${escapeHtml(name)}</h1>
             ${a.verified?'<span class="author-verified">✓ Верифицирован</span>':''}
           </div>
           <span class="author-role">${escapeHtml(a.specialty)}</span>
           ${a.bio?`<p class="bio">${escapeHtml(a.bio)}</p>`:''}
         </div>
       </div>
       ${prefCounter('person:'+name,preferredCount,{interactive:!staticFav})}
     </div>
     <div class="author-public-data">
       <div class="author-public-stats">
       <div><b>${fmt(a.comments||0)}</b><span>Авторских комментариев</span></div>
       <div><b>${fmt(a.authorRatings||0)}</b><span>Оценок</span></div>
       <div><b>${fmt(a.authorLikes||0)}</b><span>Авторских лайков</span></div>
     </div>
     <div class="author-score-section">
       <span class="eyebrow author">Средние оценки работ</span>
       <div class="author-public-averages">
         ${compactRating(a.scores.reviews,'reviews','По рецензиям')}
         ${compactRating(a.scores.ratings,'ratings','Без рецензий')}
         ${compactRating(a.scores.critics,'critics','Медиа / критики')}
       </div>
     </div>
     </div>
   </div>
 </section>

 <section class="section author-public-content">
   <div class="author-content-toolbar">
     <div class="tabs" role="tablist" aria-label="Материалы автора">
       <button data-author-profile-tab="works" class="${state.authorProfileTab==='works'?'active':''}">Фильмография</button>
       <button data-author-profile-tab="reviews" class="${state.authorProfileTab==='reviews'?'active':''}">Рецензии</button>
       <button data-author-profile-tab="likes" class="${state.authorProfileTab==='likes'?'active':''}">Авторские лайки</button>
       <button data-author-profile-tab="ratings" class="${state.authorProfileTab==='ratings'?'active':''}">Оценки</button>
     </div>
     ${state.authorProfileTab==='works'?`<label class="author-film-sort"><span>Сортировка</span><select id="authorFilmSort" aria-label="Сортировка фильмографии">${['Новые','Старые','Популярные'].map(v=>`<option ${state.authorFilmSort===v?'selected':''}>${v}</option>`).join('')}</select></label>`:''}
   </div>

   ${state.authorProfileTab==='works'
     ? `<div class="movie-list author-filmography-list">${filmography||'<div class="panel muted">Подтверждённые работы пока не добавлены.</div>'}</div>`
     : state.authorProfileTab==='reviews'
       ? `<div class="review-list author-materials-list">
            ${REVIEWS.filter(r=>r.user===name).map(r=>reviewCard(r,'feed')).join('')}
            <article class="review author-own-review">
             <div class="review-top">
               <button class="person author-person-link" data-author-name="${escapeHtml(name)}">
                 <img src="${a.image}" alt="">
                 <div><strong>${escapeHtml(name)}</strong><span style="color:var(--amber)">${escapeHtml(a.specialty.split('·')[0].trim())}</span></div>
               </button>
               ${authorRating}
             </div>
             <h3>${reviewTitle}</h3>
             <p class="review-text">${reviewText}</p>
             <div class="review-footer"><span class="review-likes"><span class="review-like is-static"><span class="like-heart" aria-hidden="true">♥</span><span class="like-count">${fmt(1482+(name.length*11))}</span></span></span><span>18 сентября 2026</span></div>
           </article>
         </div>`
       : state.authorProfileTab==='likes'
         ? `<div class="review-list author-materials-list">${(()=>{const liked=REVIEWS.filter(r=>(r.authorLikes||[]).includes(name));return liked.length?liked.map(r=>reviewCard(r,'feed')).join(''):'<div class="panel muted">Автор пока не отмечал рецензии лайком автора.</div>'})()}</div>`
         : `<div class="rating-list author-materials-list">
             ${(works.length?works.slice(0,4):MOVIES.slice(0,4)).map((w,i)=>{
               const m=MOVIES.find(x=>x.title===w.title);
               const score=w.scores?.ratings ?? (m?scoreSet(m,'ratings').total:null);
               const tag=m?'button':'div';
               return `<${tag} class="rating-row" ${m?`data-public-movie="${m.id}"`:''}><img src="${w.poster||m?.poster||posters[i%posters.length]}" alt=""><div><strong>${escapeHtml(w.title)}</strong><span>${escapeHtml(w.director||m?.director||'')}</span></div>${rating(score,[8,9,9,9,9],'ratings')}</${tag}>`
             }).join('')}
           </div>`
   }
 </section>`;
}

function authorProfilePage(){
 const name=state.authorName||'Дени Вильнёв';
 return siteShell(`<main class="page studio-public-profile">${authorPublicProfileContent(name,{studio:false})}</main>`)
}

function studioShell(){const acc=getAuthorProfile(state.authorAccount);const nav=[['dashboard','Обзор'],['works','Фильмография'],['audience','Мнения зрителей'],['comments','Авторские комментарии'],['profile','Мой профиль']];const body=state.studioPage==='dashboard'?studioDashboard():state.studioPage==='works'?studioWorks():state.studioPage==='audience'?studioAudience():state.studioPage==='comments'?studioComments():studioProfile();return `<div class="studio-shell"><aside class="studio-side"><div class="studio-logo"><span><img src="${echoMark}" alt="" aria-hidden="true"></span><div><strong>Эхо</strong><small>STUDIO</small></div></div><nav>${nav.map(([id,l])=>`<button data-studio="${id}" class="${state.studioPage===id?'active':''}">${l}</button>`).join('')}</nav><button class="studio-site-link" data-studio-to-site title="Перейти на сайт Эхо"><span class="ssl-icon" aria-hidden="true">↗</span><span class="ssl-text">Перейти на сайт Эхо</span><span class="ssl-short">Сайт Эхо</span></button><div class="studio-side-foot"><button class="studio-account-id" data-studio="profile" title="Мой профиль"><img src="${acc.image}" alt=""><span><strong>${escapeHtml(state.authorAccount)}</strong><small>Автор · ${escapeHtml(authorRole(acc))}</small></span></button><button class="studio-logout" data-author-logout>Выйти</button></div></aside><div>${body}</div></div>`}
function studioDashboard(){
 const m=MOVIES[0];
 return `<main class="studio-content"><header><div><span class="eyebrow author">Обзор</span><h1>Что происходит вокруг <em>ваших работ</em>.</h1></div></header><section class="studio-metrics">${metric('В предпочтениях',fmt(getAuthorProfile(state.authorAccount).preferred),'+1 284 за 30 дней')}${metric('Рецензий','3 842','+218 за 30 дней')}${metric('Авторских лайков',fmt(getAuthorProfile(state.authorAccount).authorLikes||0),'поставлено зрителям')}${metric('Средняя оценка','89','по вашим работам')}</section><section class="studio-block"><div class="studio-head"><h2>Дюна: Часть вторая</h2><button class="quiet" data-studio="works">Все работы →</button></div><div class="focus"><img src="${m.poster}"><div><div class="film-metrics">${metric('Рецензий','1 284','+84 за неделю')}${metric('Оценок','18 420','+962 за неделю')}${metric('В предпочтениях',fmt(m.preferred),'+471 за неделю')}</div>${filmAssessment(m,'studio-film-assessment')}</div></div></section><section class="studio-block"><div class="studio-head"><h2>Последние мнения</h2><button class="quiet" data-studio="audience">Смотреть все →</button></div><div class="studio-audience">${reviewsOnAuthorWorks(state.authorAccount).slice(0,3).map(r=>studioReviewCard(r)).join('')||'<div class="panel muted">Рецензий на ваши работы пока нет.</div>'}</div></section></main>`
}

function metric(label,value,delta){return `<div class="metric"><span>${label}</span><strong>${value}</strong><small>${delta}</small></div>`}
function studioWorks(){
  const works=[...getAuthorProfile(state.authorAccount).works];
 return `<main class="studio-content"><header><div><span class="eyebrow author">Фильмография</span><h1>Ваши работы</h1></div></header><section class="studio-block"><div class="studio-works">${works.map(w=>{
   const m=MOVIES.find(x=>x.title===w.title);
   return `<article class="studio-work">
     <img src="${w.poster||m?.poster||posters[0]}" alt="Постер: ${escapeHtml(w.title)}">
     <div class="studio-work-copy">
       <h3>${escapeHtml(w.title)}</h3>
       <p class="muted">${w.year} · ${escapeHtml(w.genre||'Фильм')} · ${escapeHtml(w.role||'Участие подтверждено')}</p>
       <span class="muted small">${fmt(w.reviews||0)} рецензий · ${fmt(w.ratings||0)} оценок</span>
     </div>
     <div class="studio-work-scores">
        ${scoreTiles(w.scores)}
     </div>
   </article>`;
 }).join('')}</div></section></main>`;
}

function studioReviewCard(r){
 const movie=MOVIES[r.movieId];const isLong=r.text.length>210;const expanded=state.expanded.has(r.id);const spoilerOpen=state.spoilers.has(r.id);
 const body=`<div class="review-copy ${isLong&&!expanded?'is-collapsed':''}"><p class="review-text">${escapeHtml(r.text)}</p></div>${isLong?`<button class="expand-btn" data-expand="${r.id}">${expanded?'Свернуть':'Читать полностью'}</button>`:''}`;
 const content=r.spoiler&&!spoilerOpen?`<div class="spoiler-cover"><div><strong>⚠ Рецензия содержит спойлеры</strong></div><button data-spoiler="${r.id}">Показать</button></div>`:`${r.spoiler?'<span class="spoiler-label">СПОЙЛЕРЫ</span>':''}${body}`;
 const mine=(r.authorLikes||[]).includes(state.authorAccount);
 return `<article class="review studio-review-card"><div class="review-top"><div class="person"><img src="${r.avatar}" alt=""><div><strong>${escapeHtml(r.user)}</strong><span style="color:${statusColor(r)}">${escapeHtml(r.status)}</span></div></div>${rating(r.total,r.parts,'reviews')}</div><button class="review-film-context" data-public-movie="${movie.id}"><img src="${movie.poster}" alt=""><span><strong>${escapeHtml(movie.title)}</strong><em>${movie.year} · ${escapeHtml(movie.director)}</em></span></button><h3>${escapeHtml(r.title)}</h3>${content}<div class="review-footer"><span class="review-likes"><span class="review-like is-static" aria-label="Лайков: ${r.likes}"><span class="like-heart" aria-hidden="true">♥</span><span class="like-count">${fmt(r.likes)}</span></span><button class="author-like ${mine?'':'is-empty'}" data-author-like="${r.id}" aria-pressed="${mine}">${mine?'★ Лайк автора':'☆ Поставить лайк автора'}</button></span><span>${r.date}</span></div></article>`;
}

function studioAudience(){
 return `<main class="studio-content studio-reading"><header><div><span class="eyebrow author">Мнения зрителей</span><h1>Рецензии на ваши работы.</h1></div></header><section class="studio-block studio-review-surface"><div class="review-list">${(()=>{const list=reviewsOnAuthorWorks(state.authorAccount);return list.length?list.map(r=>studioReviewCard(r)).join(''):'<div class="panel muted">Рецензий на ваши работы пока нет.</div>'})()}</div></section></main>`
}

function studioComments(){
 const ids=authorMovieIds(state.authorAccount);
 const m=MOVIES[ids.includes(state.studioCommentMovie)?state.studioCommentMovie:ids[0]]||MOVIES[0];
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
 return `<div class="overlay" id="submitOverlay"><div class="modal submit-modal"><div class="modal-head"><div><span class="eyebrow">${ratingOnly?'Оценка без рецензии':'Публикация'}</span><h2>${ratingOnly?'Сохранить только оценку?':'Опубликовать рецензию?'}</h2></div><button id="closeSubmitModal">×</button></div>${ratingOnly?`<div class="submit-score">${rating(payload.total,payload.parts,'ratings',true)}</div><p>Текст рецензии пуст. На странице фильма останутся только итоговый балл и значения параметров.</p>`:`<div class="submit-review-preview"><strong>${escapeHtml(payload.title||'Без заголовка')}</strong><p>${escapeHtml(payload.text.slice(0,220))}${payload.text.length>220?'…':''}</p></div>`}<label class="submit-spoiler-toggle" for="confirmSpoilerToggle"><input id="confirmSpoilerToggle" type="checkbox" ${payload.spoiler?'checked':''}><span>В рецензии есть спойлеры</span></label><div class="submit-actions"><button class="btn ghost" id="cancelSubmit">Отмена</button><button class="btn primary" id="confirmSubmit">${ratingOnly?'Сохранить оценку':'Опубликовать'}</button></div></div></div>`
}
let noticeTimer=null;
function showNotice(message,ms=2200){
 clearTimeout(noticeTimer);
 document.getElementById('echoNotice')?.remove();
 document.body.insertAdjacentHTML('beforeend',`<div class="echo-notice" id="echoNotice" role="status">${message}</div>`);
 noticeTimer=setTimeout(()=>document.getElementById('echoNotice')?.remove(),ms);
}

function render(){let html;if(state.view==='site'){html=state.page==='home'?homePage():state.page==='catalog'?catalogPage():state.page==='search'?searchPage():state.page==='profile'?profilePage(true):state.page==='otherProfile'?profilePage(false):state.page==='editProfile'?editProfilePage():state.page==='authorProfile'?authorProfilePage():moviePage()}else if(state.view==='userAuth'){html=userAuthPage()}else if(state.view==='authorAuth'){html=authorAuthPage()}else{html=studioShell()}document.getElementById('root').innerHTML=html;bind()}

function bind(){
 document.querySelectorAll('[data-page]').forEach(b=>b.onclick=()=>{const p=b.dataset.page;if(p==='userAuth'){state.view='userAuth';render();return}if(p==='profile'&&state.authorLogged&&!state.userLogged){openAuthorProfile(state.authorAccount);return}if(p==='profile'&&!state.userLogged){state.view='userAuth';render();scrollTo(0,0);return}go(p)});
 document.querySelectorAll('[data-view]').forEach(b=>b.onclick=()=>{const next=b.dataset.view;if(next==='site')state.pendingReturn=null;if(next==='site'&&validMovieId(state.pendingReviewMovieId)){returnToReview(state.pendingReviewMovieId);return}state.view=next;if(state.view==='site')state.page='home';render();scrollTo(0,0)});
 document.querySelectorAll('[data-movie]').forEach(b=>b.onclick=()=>{state.movieId=+b.dataset.movie;go('movie')});
 document.querySelectorAll('[data-public-movie]').forEach(b=>b.onclick=()=>{state.view='site';state.movieId=+b.dataset.publicMovie;state.page='movie';render();scrollTo(0,0)});
 document.querySelectorAll('[data-profile-user]').forEach(b=>b.onclick=()=>{const n=b.dataset.profileUser;if(state.userLogged&&(n===state.user.nickname||n==='NADYA'))go('profile');else{state.otherUser=n;go('otherProfile')}});
 document.querySelectorAll('[data-author-name]').forEach(b=>b.onclick=e=>{e.stopPropagation();openAuthorProfile(b.dataset.authorName)});
 document.querySelectorAll('[data-search-profile]').forEach(b=>b.onclick=()=>{if(b.dataset.searchProfile==='author'){openAuthorProfile(b.dataset.authorName||state.authorAccount)}else{const n=b.dataset.userName||'frames.by.ira';if(state.userLogged&&(n===state.user.nickname||n==='NADYA'))go('profile');else{state.otherUser=n;go('otherProfile')}}});
 document.querySelectorAll('[data-profile-tab]').forEach(b=>b.onclick=()=>{state.profileTab=b.dataset.profileTab;render()});
 const ps=document.getElementById('profileSort');if(ps)ps.onchange=()=>{state.profileSort=ps.value;render()};
 const ms=document.getElementById('movieSort');if(ms)ms.onchange=()=>{state.movieSort=ms.value;render()};
 document.querySelectorAll('[data-expand]').forEach(b=>b.onclick=()=>{const id=+b.dataset.expand;state.expanded.has(id)?state.expanded.delete(id):state.expanded.add(id);render()});
 document.querySelectorAll('[data-spoiler]').forEach(b=>b.onclick=()=>{state.spoilers.add(+b.dataset.spoiler);render()});
 document.querySelectorAll('[data-like-review]').forEach(b=>b.onclick=e=>{
  e.preventDefault();e.stopPropagation();
  if(state.authorLogged)return;
  const id=+b.dataset.likeReview,r=REVIEWS.find(x=>x.id===id);if(!r)return;
  const wasLiked=state.likedReviews.has(id);
  if(wasLiked){state.likedReviews.delete(id);r.likes=Math.max(0,r.likes-1);r.popular=Math.max(0,r.popular-1)}
  else{state.likedReviews.add(id);r.likes++;r.popular++}
  api.setReviewLike(id,!wasLiked).catch(apiFail);
  document.querySelectorAll('[data-like-review="'+id+'"]').forEach(btn=>{
    btn.classList.toggle('liked',!wasLiked);
    btn.setAttribute('aria-pressed',String(!wasLiked));
    const count=btn.querySelector('.like-count');if(count)count.textContent=fmt(r.likes);
  });
});
 document.querySelectorAll('[data-filter-toggle]').forEach(b=>b.onclick=()=>{const k=b.dataset.filterToggle;state.openFilter=state.openFilter===k?null:k;render()});
 document.querySelectorAll('[data-filter-option]').forEach(b=>b.onclick=()=>{state.filters[b.dataset.filterOption]=b.dataset.value;state.openFilter=null;render()});

 const nsi=document.getElementById('navSearchInput');if(nsi)nsi.oninput=()=>{state.searchQuery=nsi.value;state.view='site';state.page='search';render();const n=document.getElementById('navSearchInput');if(n){n.focus();n.setSelectionRange(n.value.length,n.value.length)}};
 document.querySelectorAll('[data-search-tab]').forEach(b=>b.onclick=()=>{state.searchTab=b.dataset.searchTab;render()});

 const levels=document.getElementById('levels');if(levels)levels.onclick=()=>{document.body.insertAdjacentHTML('beforeend',fireflyModal(+levels.dataset.levelPoints||state.user.points));document.getElementById('closeModal').onclick=()=>document.getElementById('overlay').remove();document.getElementById('overlay').onclick=e=>{if(e.target.id==='overlay')e.currentTarget.remove()};document.querySelectorAll('[data-fly]').forEach(x=>x.onclick=()=>{if(x.disabled)return;state.activeFly=+x.dataset.fly;document.getElementById('overlay').remove();render()})};
 const logout=document.getElementById('logout');if(logout)logout.onclick=()=>{api.logout().catch(()=>{});state.userLogged=false;go('home')};

 document.querySelectorAll('[data-edit-section]').forEach(b=>b.onclick=()=>{state.editSection=b.dataset.editSection;go('editProfile')});
 document.querySelectorAll('[data-edit-tab]').forEach(b=>b.onclick=()=>{state.editSection=b.dataset.editTab;render()});
 document.querySelectorAll('[data-pref-type]').forEach(b=>b.onclick=()=>{state.editPrefType=b.dataset.prefType;state.editQuery='';render()});
 const prefSearch=document.getElementById('prefSearch');if(prefSearch)prefSearch.oninput=()=>{state.editQuery=prefSearch.value;render();const n=document.getElementById('prefSearch');if(n){n.focus();n.setSelectionRange(n.value.length,n.value.length)}};
 document.querySelectorAll('[data-choice]').forEach(b=>b.onclick=()=>{const id=+b.dataset.choice;const type=state.editPrefType;const key=type==='movies'?'prefMovies':type==='directors'?'prefDirectors':'prefActors';const before=state.user[key];const after=before.includes(id)?before.filter(x=>x!==id):[...before,id].slice(-5);state.user[key]=after;const tgt=x=>type==='movies'?{type:'movie',id:x}:{type:'person',name:PEOPLE[type][x][0]};before.filter(x=>!after.includes(x)).forEach(x=>adjustPref(tgt(x),-1));after.filter(x=>!before.includes(x)).forEach(x=>adjustPref(tgt(x),1));savePrefs();render()});
 const av=document.getElementById('avatarFile');if(av)av.onchange=e=>{const file=e.target.files&&e.target.files[0];if(!file)return;const reader=new FileReader();reader.onload=()=>{state.editAvatar=reader.result;document.getElementById('avatarPreview').src=reader.result};reader.readAsDataURL(file)};
 const saveProfile=document.getElementById('saveProfile');if(saveProfile)saveProfile.onclick=()=>{state.user.nickname=document.getElementById('editNick').value||state.user.nickname;state.user.bio=document.getElementById('editBio').value||state.user.bio;state.user.avatar=state.editAvatar;api.updateProfile({nickname:state.user.nickname,bio:state.user.bio,avatar:state.user.avatar}).catch(apiFail);go('profile')};

 document.querySelectorAll('[data-crew-tab]').forEach(b=>b.onclick=()=>{state.crewTab=b.dataset.crewTab;render()});
 document.querySelectorAll('[data-crew-move]').forEach(b=>b.onclick=()=>{const t=document.getElementById('crewTrack');if(t)t.scrollBy({left:+b.dataset.crewMove*320,behavior:'smooth'})});
 document.querySelectorAll('[data-like-comment]').forEach(b=>b.onclick=e=>{
  e.preventDefault();e.stopPropagation();
  if(state.authorLogged)return;
  const id=+b.dataset.likeComment,m=MOVIES[id];if(!m?.authorComment)return;
  const wasLiked=state.likedAuthorComments.has(id);
  if(wasLiked){state.likedAuthorComments.delete(id);m.authorComment.likes=Math.max(0,m.authorComment.likes-1)}
  else{state.likedAuthorComments.add(id);m.authorComment.likes++}
  api.setAuthorCommentLike(id,!wasLiked).catch(apiFail);
  document.querySelectorAll('[data-like-comment="'+id+'"]').forEach(btn=>{
    btn.classList.toggle('liked',!wasLiked);
    btn.setAttribute('aria-pressed',String(!wasLiked));
    const count=btn.querySelector('.like-count');if(count)count.textContent=fmt(m.authorComment.likes);
  });
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
  c.textContent=`${rt.value.length} / 10 000 · минимум 150 для рецензии · Черновик сохранён`;
  c.classList.toggle('bad',rt.value.length>0&&rt.value.length<150);
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
  document.getElementById('confirmSpoilerToggle').onchange=e=>{
   writeReviewDraft(draft.movieId,{...draft,spoiler:e.target.checked});
  };
  document.getElementById('confirmSubmit').onclick=()=>{
   const confirmedSpoiler=!!document.getElementById('confirmSpoilerToggle')?.checked;
   if(!body){
    api.saveRating({movieId:draft.movieId,parts}).then(()=>{
     clearReviewDraft(draft.movieId);
     close();render();showNotice(`Оценка ${total}/100 сохранена без рецензии.`);
    }).catch(apiFail);
    return;
   }
   api.publishReview({movieId:draft.movieId,title:title||'Без заголовка',text:body,parts,spoiler:confirmedSpoiler}).then(saved=>{
    REVIEWS.unshift(saved||{id:Date.now(),movieId:draft.movieId,...reviewerIdentity(),total,parts,title:title||'Без заголовка',text:body,likes:0,date:todayRu(),spoiler:confirmedSpoiler,popular:0,authorLikes:[]});
    clearReviewDraft(draft.movieId);
    close();render();showNotice('Рецензия опубликована.');
   }).catch(apiFail);
  };
 };

 document.querySelectorAll('[data-user-auth]').forEach(b=>b.onclick=()=>{state.userAuth=b.dataset.userAuth;render()});
 const us=document.getElementById('userSubmit');if(us)us.onclick=async()=>{
   let res;
   try{res=await api.userAuth(state.userAuth,{login:document.getElementById('authLogin')?.value.trim()||'',password:document.getElementById('authPassword')?.value||''})}
   catch(e){apiFail(e);return}
   if(res?.user)state.user=res.user;
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
 document.querySelectorAll('[data-author-auth]').forEach(b=>b.onclick=()=>{state.authorAuth=b.dataset.authorAuth;render()});
 const ast=document.getElementById('authorSpecialtyToggle');if(ast)ast.onclick=()=>{state.openAuthorSpecialty=!state.openAuthorSpecialty;render()};
 document.querySelectorAll('[data-author-specialty]').forEach(b=>b.onclick=()=>{state.authorSpecialty=b.dataset.authorSpecialty;state.openAuthorSpecialty=false;render()});
 const afs=document.getElementById('authorFilmSort');if(afs)afs.onchange=()=>{state.authorFilmSort=afs.value;render()};
 const as=document.getElementById('authorSubmit');if(as)as.onclick=async()=>{
  let res;
  try{res=await api.authorAuth(state.authorAuth,{login:document.getElementById('authorLogin')?.value.trim()||'',password:document.getElementById('authorPassword')?.value||'',name:document.getElementById('authorPublicName')?.value.trim()||'',specialty:state.authorSpecialty})}
  catch(e){apiFail(e);return}
  if(res?.pending){showNotice('Заявка отправлена. Кабинет откроется после подтверждения роли.',5000);return}
  if(res?.author?.name)state.authorAccount=res.author.name;
  state.authorLogged=true;state.userLogged=false;state.view='studio';state.studioPage='dashboard';render();scrollTo(0,0);
 };

 document.querySelectorAll('[data-studio]').forEach(b=>b.onclick=()=>{state.studioPage=b.dataset.studio;render();scrollTo(0,0)});
 const smt=document.getElementById('studioMovieToggle');if(smt)smt.onclick=()=>{state.openStudioMovieSelect=!state.openStudioMovieSelect;render()};
 document.querySelectorAll('[data-studio-movie]').forEach(b=>b.onclick=()=>{state.studioCommentMovie=+b.dataset.studioMovie;state.openStudioMovieSelect=false;render()});
 
 
 
 
 document.querySelectorAll('[data-author-profile-tab]').forEach(b=>b.onclick=()=>{state.authorProfileTab=b.dataset.authorProfileTab;render()});
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


/* ===== Запуск: загрузить данные (демо или сервер) и отрисовать интерфейс ===== */
async function boot(){
 const root=document.getElementById('root');
 root.innerHTML='<div class="app-loading" role="status">Загрузка…</div>';
 try{
  const data=await api.loadInitialData();
  MOVIES=data.movies;REVIEWS=data.reviews;AUTHOR_PROFILES=data.authors;PEOPLE=data.people;USER_PROFILES=data.users;DEMO_USER=data.demoUser;
  if(data.demoAuthor)state.authorAccount=data.demoAuthor;
  state.user=DEMO_USER?structuredClone(DEMO_USER):null;
  if(data.session?.role==='user'){state.user=data.session.user;state.userLogged=true}
  if(data.session?.role==='author'&&data.session.author){state.authorAccount=data.session.author;state.authorLogged=true}
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
