// Единственная точка обмена данными между интерфейсом и бэкендом.
//
// Если VITE_API_URL не задан (файл .env), интерфейс работает на демо-данных из mock-data.js.
// Если задан — все функции ниже ходят на сервер. Пути и форматы описаны в API.md.
//
// Важно: внутри интерфейса id фильма = его позиция в массиве фильмов (так устроен прототип).
// Настоящие id с сервера хранятся в поле apiId; перевод туда и обратно делается только здесь.

import {createMockData} from './mock-data.js';

const BASE=(import.meta.env.VITE_API_URL||'').replace(/\/+$/,'');
export const USE_MOCK=!BASE;

export class ApiError extends Error{
 constructor(message,status){super(message);this.status=status}
}

async function request(path,{method='GET',body}={}){
 let res;
 try{
  res=await fetch(BASE+path,{
   method,
   credentials:'include',               // сессия по cookie; для токена — добавить заголовок Authorization здесь
   headers:body!==undefined?{'Content-Type':'application/json'}:{},
   body:body!==undefined?JSON.stringify(body):undefined
  });
 }catch(_){throw new ApiError('Нет связи с сервером',0)}
 if(!res.ok){
  let message=`Ошибка сервера (${res.status})`;
  try{const data=await res.json();if(data?.message)message=data.message}catch(_){}
  throw new ApiError(message,res.status);
 }
 return res.status===204?null:res.json();
}

/* ---------- перевод id ---------- */
let movieApiIds=[];                                   // индекс в интерфейсе -> id на сервере
const apiMovieId=index=>USE_MOCK?index:movieApiIds[index];
const uiMovieId=apiId=>USE_MOCK?apiId:movieApiIds.indexOf(apiId);

function normalizeUser(u,people){
 if(!u)return null;
 const byName=(list,names)=>(names||[]).map(n=>list.findIndex(p=>p[0]===n)).filter(i=>i>=0);
 return {
  ...u,
  prefMovies:(u.prefMovies||[]).map(uiMovieId).filter(i=>i>=0),
  prefDirectors:byName(people.directors,u.prefDirectors),
  prefActors:byName(people.actors,u.prefActors)
 };
}
function normalizeReview(r){return {authorLikes:[],spoiler:false,popular:r.likes||0,...r,movieId:uiMovieId(r.movieId)}}

function normalize({movies,reviews,authors,people,users,me}){
 movieApiIds=movies.map(m=>m.id);
 const peopleUi={
  directors:(people?.directors||[]).map(p=>[p.name,p.image]),
  actors:(people?.actors||[]).map(p=>[p.name,p.image])
 };
 const crewList=list=>(list||[]).map(c=>Array.isArray(c)?c:[c.name,c.role,c.image]);
 return {
  movies:movies.map((m,i)=>({...m,apiId:m.id,id:i,crew:{actors:crewList(m.crew?.actors),team:crewList(m.crew?.team)}})),
  reviews:reviews.map(normalizeReview).filter(r=>r.movieId>=0),
  authors:Object.fromEntries(authors.map(a=>[a.name,a])),
  people:peopleUi,
  users:Object.fromEntries(users.map(u=>[u.nickname,u])),
  session:me?{role:me.role,user:me.role==='user'?normalizeUser(me.user,peopleUi):null,author:me.role==='author'?me.author?.name:null}:null,
  demoUser:null,demoAuthor:null
 };
}

let mock=null;           // демо-данные текущей вкладки
let peopleCache={directors:[],actors:[]};

export const api={
 /** Всё, что нужно интерфейсу при запуске. */
 async loadInitialData(){
  if(USE_MOCK){
   mock=createMockData();
   peopleCache=mock.people;
   return {...mock,session:null};
  }
  const [movies,reviews,authors,people,users,me]=await Promise.all([
   request('/movies'),request('/reviews'),request('/authors'),request('/people'),request('/users'),
   request('/auth/me').catch(e=>{if(e.status===401)return null;throw e})
  ]);
  const data=normalize({movies,reviews,authors,people,users,me});
  peopleCache=data.people;
  return data;
 },

 /* ---------- вход и регистрация ---------- */
 // mode: 'login' | 'register'. Ответ: { user }
 async userAuth(mode,{login,password}){
  if(USE_MOCK)return {user:mock.demoUser};
  const data=await request(mode==='register'?'/auth/register':'/auth/login',{method:'POST',body:{login,password}});
  return {user:normalizeUser(data.user,peopleCache)};
 },
 // Ответ: { author: { name } }. Регистрация автора = заявка, профиль подтверждается модератором.
 async authorAuth(mode,{login,password,name,specialty}){
  if(USE_MOCK)return {author:{name:mock.demoAuthor}};
  return request(mode==='register'?'/auth/author/register':'/auth/author/login',{method:'POST',body:{login,password,name,specialty}});
 },
 async logout(){if(!USE_MOCK)await request('/auth/logout',{method:'POST'})},

 /* ---------- профиль зрителя ---------- */
 async updateProfile({nickname,bio,avatar}){
  if(!USE_MOCK)await request('/me',{method:'PATCH',body:{nickname,bio,avatar}});
 },
 // movies — индексы фильмов в интерфейсе; directors/actors — имена.
 async savePreferences({movies,directors,actors}){
  if(!USE_MOCK)await request('/me/preferences',{method:'PUT',body:{movies:movies.map(apiMovieId),directors,actors}});
 },

 /* ---------- рецензии и оценки ---------- */
 // Ответ сервера — созданная рецензия в формате списка /reviews.
 async publishReview({movieId,title,text,parts,spoiler}){
  if(USE_MOCK)return null;
  const r=await request(`/movies/${apiMovieId(movieId)}/reviews`,{method:'POST',body:{title,text,parts,spoiler}});
  return normalizeReview(r);
 },
 async saveRating({movieId,parts}){
  if(!USE_MOCK)await request(`/movies/${apiMovieId(movieId)}/ratings`,{method:'POST',body:{parts}});
 },
 async setReviewLike(reviewId,liked){
  if(!USE_MOCK)await request(`/reviews/${reviewId}/like`,{method:liked?'PUT':'DELETE'});
 },

 /* ---------- автор ---------- */
 async setAuthorLike(reviewId,liked){
  if(!USE_MOCK)await request(`/reviews/${reviewId}/author-like`,{method:liked?'PUT':'DELETE'});
 },
 async publishAuthorComment({movieId,title,text}){
  if(!USE_MOCK)return request(`/movies/${apiMovieId(movieId)}/author-comment`,{method:'PUT',body:{title,text}});
  return null;
 },
 async setAuthorCommentLike(movieId,liked){
  if(!USE_MOCK)await request(`/movies/${apiMovieId(movieId)}/author-comment/like`,{method:liked?'PUT':'DELETE'});
 }
};
