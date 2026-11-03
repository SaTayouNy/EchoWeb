import {createMockData} from './mock-data.js';

const BASE=(import.meta.env.VITE_API_URL||'').replace(/\/+$/,'');
const TMDB_IMAGE_PREFIX='https://image.tmdb.org/t/p/';
const tmdbImage=url=>{
 if(!url)return '';
 if(!url.startsWith(TMDB_IMAGE_PREFIX))return url;
 const path=url.slice(TMDB_IMAGE_PREFIX.length);
 return /^w500\/[A-Za-z0-9_-]+\.(?:jpe?g|png|webp)$/.test(path)?`/tmdb-images/${path}`:url;
};
export const USE_MOCK=!BASE;
const TOKEN_KEY='echo-session-v1';
const PARAMS=['dramaturgy_structure','characters_performance','visual_language','sound_music','impression'];
let tokens=null,refreshPending=null,mock=null,currentUser=null;
let movieIds=[],reviewIds=[],peopleSources=new Map(),peopleCache={directors:[],actors:[]};
let cachedMovies=[],cachedAuthors={},cachedReviews=[],cachedComments=[],cachedAuthorLikes=[],cachedPreferenceCounts=[];
const detailRequests=new Map();
try{tokens=JSON.parse(sessionStorage.getItem(TOKEN_KEY)||'null')}catch(_){sessionStorage.removeItem(TOKEN_KEY)}

export class ApiError extends Error{constructor(message,status){super(message);this.status=status}}
function saveTokens(value){tokens=value;value?sessionStorage.setItem(TOKEN_KEY,JSON.stringify(value)):sessionStorage.removeItem(TOKEN_KEY)}
async function raw(path,{method='GET',body,auth=true}={}){
 let response;
 try{response=await fetch(BASE+path,{method,headers:{...(body===undefined?{}:{'Content-Type':'application/json'}),...(auth&&tokens?.accessToken?{Authorization:`Bearer ${tokens.accessToken}`}:{})},body:body===undefined?undefined:JSON.stringify(body)})}
 catch(_){throw new ApiError('Нет связи с сервером',0)}
 if(!response.ok){
  let message=`Ошибка сервера (${response.status})`;
  try{const data=await response.json();message=data?.error?.message||data?.message||message}catch(_){}
  throw new ApiError(message,response.status);
 }
 return response.status===204?null:response.json();
}
async function request(path,options={}){
 try{return await raw(path,options)}catch(error){
  if(error.status!==401||!tokens?.refreshToken||path==='/v1/auth/refresh'||options.auth===false)throw error;
  if(!refreshPending)refreshPending=raw('/v1/auth/refresh',{method:'POST',body:{refreshToken:tokens.refreshToken},auth:false})
   .then(saveTokens).catch(e=>{saveTokens(null);throw e}).finally(()=>{refreshPending=null});
  await refreshPending;
  return raw(path,options);
 }
}
const movieId=index=>USE_MOCK?index:movieIds[index];
const movieIndex=id=>USE_MOCK?id:movieIds.indexOf(Number(id));
const ruDate=value=>value?new Date(value).toLocaleDateString('ru-RU',{day:'numeric',month:'long',year:'numeric'}):'';
const emptyScore=()=>({total:null,parts:[]});
function uiMovie(detail,index){
 return {id:index,apiId:detail.tmdbId,title:detail.title,year:detail.releaseYear||'',genre:detail.genres?.[0]||'',director:detail.director||'',
  country:(detail.countries||[]).join(', '),poster:tmdbImage(detail.posterUrl),desc:detail.overview||'',reviews:0,ratings:0,preferred:0,
  scoreSets:{reviews:emptyScore(),ratings:emptyScore(),critics:emptyScore()},authorComment:null,detailLoaded:true,
  crew:{actors:(detail.castDetails||[]).map(c=>[c.name,c.role,tmdbImage(c.profileUrl)]),team:(detail.crewDetails||[]).map(c=>[c.name,c.role,tmdbImage(c.profileUrl)])}};
}
function registerSummary(item){
 const known=movieIndex(item.tmdbId);
 if(known>=0)return known;
 const index=movieIds.length;
 movieIds.push(item.tmdbId);
 cachedMovies.push({id:index,apiId:item.tmdbId,title:item.title||'Фильм',year:item.releaseYear||'',genre:item.genres?.[0]||'',
  director:'',country:'',poster:tmdbImage(item.posterUrl),desc:item.overview||'',tmdbRating:item.rating??null,reviews:0,ratings:0,preferred:0,
  scoreSets:{reviews:emptyScore(),ratings:emptyScore(),critics:emptyScore()},authorComment:null,detailLoaded:false,
  crew:{actors:[],team:[]}});
 return index;
}
function addDetailedCredits(detail){
 for(const [type,credits] of [['DIRECTOR',(detail.crewDetails||[]).filter(c=>c.role?.toLowerCase()==='director')],['ACTOR',detail.castDetails||[]]]){
  for(const credit of credits){
   if(!credit?.tmdbPersonId||!credit.name)continue;
   const key=`${type}:${credit.name}`;
   if(!peopleSources.has(key)){
    peopleSources.set(key,{id:credit.tmdbPersonId,sourceMediaType:'MOVIE',sourceTmdbId:detail.tmdbId});
    peopleCache[type==='DIRECTOR'?'directors':'actors'].push([credit.name,tmdbImage(credit.profileUrl)]);
   }
   if(!cachedAuthors[credit.name])cachedAuthors[credit.name]={name:credit.name,image:tmdbImage(credit.profileUrl),
    specialty:type==='DIRECTOR'?'Режиссёр':'Актёр',verified:false,bio:'',authorRatings:0,comments:0,authorLikes:0,
    preferred:0,scores:{reviews:null,ratings:null,critics:null},works:[]};
   const author=cachedAuthors[credit.name];
   if(!author.works.some(w=>w.id===`${credit.tmdbPersonId}-${detail.tmdbId}`))author.works.push({
    id:`${credit.tmdbPersonId}-${detail.tmdbId}`,title:detail.title,year:detail.releaseYear,genre:detail.genres?.[0]||'',
    director:detail.director||'',role:credit.role||'',poster:tmdbImage(detail.posterUrl),reviews:0,ratings:0,popularity:0,
    scores:{reviews:null,ratings:null,critics:null}});
  }
 }
}
// Утверждённая таблица баллов статуса (совпадает с окном «Как получать баллы» и лендингом авторов).
export const POINT_VALUES={givenLikes:5,receivedLikes:10,ratings:50,reviews:250,authorLikesReceived:1000};
export function pointsFromStats(stats={}){return Object.entries(POINT_VALUES).reduce((sum,[key,value])=>sum+(Number(stats[key])||0)*value,0)}
// Параметры оценки в профиле: поддерживаются {parameters}, {rating:{parameters}} и {ratingParameters}.
function ratingParts(r){
 const p=r.parameters||r.rating?.parameters||r.ratingParameters;
 if(!p)return [];
 const parts=PARAMS.map(code=>Number(p[code]));
 return parts.every(v=>Number.isFinite(v)&&v>0)?parts:[];
}
function uiUser(user,prefs=[],profile={}){
 const byName=(list,names)=>names.map(name=>list.findIndex(p=>p[0]===name)).filter(index=>index>=0);
 const selected=type=>prefs.filter(p=>p.type===type);
 const stats=profile.statistics||{};
 const points=pointsFromStats(stats);
 return {id:user.userId,nickname:user.username,kind:'user',bio:user.bio||'',avatar:user.avatarUrl||'',joined:ruDate(user.createdAt),points,stats,
  ratings:(profile.ratings||[]).map((r,index)=>({movieId:movieIndex(r.tmdbId),score:r.finalScore??r.rating?.finalScore??(typeof r.rating==='number'?r.rating*10:null),parts:ratingParts(r),order:index})).filter(r=>r.movieId>=0),
  prefMovies:selected('MOVIE').map(p=>movieIndex(p.tmdbId)).filter(i=>i>=0),
  prefDirectors:byName(peopleCache.directors,selected('DIRECTOR').map(p=>p.person?.name)),
  prefActors:byName(peopleCache.actors,selected('ACTOR').map(p=>p.person?.name))};
}
function uiReview(review,users={}){
 const rating=review.rating,parts=PARAMS.map(code=>rating?.parameters?.[code]??0);
 const id=reviewIds.indexOf(review.reviewId);
 const author=users[review.userId];
 return {id,userId:review.userId,movieId:movieIndex(review.media?.tmdbId),user:author?.nickname||'Зритель Эхо',avatar:author?.avatar||'',
  status:'',points:typeof author?.points==='number'?author.points:undefined,kind:'user',moderation:review.status||'',total:rating?.finalScore??(review.legacyRating?review.legacyRating*10:null),parts,
  title:review.title||'',text:review.content||'',spoiler:!!review.spoiler,likes:review.likes||0,popular:review.likes||0,
  likedByMe:false,authorLikes:[],date:ruDate(review.publishedAt||review.createdAt),published:review.status==='PUBLISHED'};
}
function personCredit(credit,type,movie,people,authors){
 if(!credit?.tmdbPersonId||!credit.name)return;
 const key=`${type}:${credit.name}`;
 if(!peopleSources.has(key)){
  peopleSources.set(key,{id:credit.tmdbPersonId,sourceMediaType:'MOVIE',sourceTmdbId:movie.tmdbId});
  people[type==='DIRECTOR'?'directors':'actors'].push({name:credit.name,image:tmdbImage(credit.profileUrl)});
 }
 if(!authors.has(credit.name))authors.set(credit.name,{name:credit.name,image:tmdbImage(credit.profileUrl),specialty:type==='DIRECTOR'?'Режиссёр':'Актёр',
  verified:false,bio:'',authorRatings:0,comments:0,authorLikes:0,preferred:0,scores:{reviews:null,ratings:null,critics:null},works:[]});
 authors.get(credit.name).works.push({id:`${credit.tmdbPersonId}-${movie.tmdbId}`,title:movie.title,year:movie.releaseYear,genre:movie.genres?.[0]||'',
  director:movie.director||'',role:credit.role||'',poster:tmdbImage(movie.posterUrl),reviews:0,ratings:0,popularity:0,
  scores:{reviews:null,ratings:null,critics:null}});
}
async function collectMovies(){
 const [popular,best]=await Promise.all([request('/v1/media/discover/movies/popular'),request('/v1/media/discover/movies?sort=best&page=1')]);
 const ids=[...new Set([...(popular.items||[]).slice(0,8),...(best.items||[]).slice(0,12),...(popular.items||[])].map(m=>m.tmdbId))].slice(0,16);
 const details=await Promise.all(ids.map(id=>request(`/v1/media/MOVIE/${id}`)));
 movieIds=details.map(m=>m.tmdbId);
 const people={directors:[],actors:[]},authors=new Map();
 for(const movie of details){
  (movie.crewDetails||[]).filter(c=>c.role?.toLowerCase()==='director').forEach(c=>personCredit(c,'DIRECTOR',movie,people,authors));
  (movie.castDetails||[]).forEach(c=>personCredit(c,'ACTOR',movie,people,authors));
 }
 const movies=details.map(uiMovie);
 cachedMovies=movies;
 return {movies,people,authors:[...authors.values()]};
}
async function loadSession(){
 if(!tokens)return null;
 try{
  const [user,prefs,profile]=await Promise.all([request('/v1/me'),request('/v1/me/preferences?size=100'),request('/v1/me/profile?activityLimit=50')]);
  currentUser=user;
  const savedIds=[...new Set([...(prefs.items||[]).filter(p=>p.type==='MOVIE').map(p=>p.tmdbId),
   ...(profile.ratings||[]).filter(r=>r.mediaType==='MOVIE').map(r=>r.tmdbId)])].filter(id=>movieIndex(id)<0);
  await Promise.all(savedIds.map(async id=>{
   try{const detail=await request(`/v1/media/MOVIE/${id}`);const index=registerSummary(detail);
    Object.assign(cachedMovies[index],uiMovie(detail,index));addDetailedCredits(detail)}catch(_){}
  }));
  if(user.role==='AUTHOR')return {role:'author',author:await request('/v1/author/me')};
  return {role:'user',user:uiUser(user,prefs.items||[],profile)};
 }catch(error){if(error.status===401){saveTokens(null);return null}throw error}
}
async function rate(movie,parts){
 const scheme=await request('/v2/rating-schemes/current');
 return request(`/v2/me/ratings/MOVIE/${movieId(movie)}`,{method:'PUT',body:{schemeVersion:scheme.version,
  parameters:Object.fromEntries(PARAMS.map((code,i)=>[code,Number(parts[i])]))}});
}

export const api={
 async loadInitialData(){
  if(USE_MOCK){mock=createMockData();peopleCache=mock.people;return {...mock,session:null}}
  const {movies,people,authors}=await collectMovies();
  peopleCache={directors:people.directors.map(p=>[p.name,p.image]),actors:people.actors.map(p=>[p.name,p.image])};
  cachedAuthors=Object.fromEntries(authors.map(a=>[a.name,a]));
  const [page,session,approved,comments,authorLikes,preferenceCounts]=await Promise.all([
   request('/v2/reviews?limit=100'),loadSession(),request('/v1/author/profiles'),request('/v1/author/comments'),request('/v1/author/review-likes'),
   request('/v1/site/preference-counts')]);
  for(const author of approved){
   const known=cachedAuthors[author.name];
   if(known){known.verified=true;known.specialty=author.specialty}
   else cachedAuthors[author.name]={...author,image:'',bio:'',authorRatings:0,comments:0,authorLikes:0,preferred:0,
    scores:{reviews:null,ratings:null,critics:null},works:[]};
  }
  // Фильмы с авторскими комментариями подгружаются, даже если их нет на главной: комментарий показывается вместе с фильмом.
 await Promise.all([...new Set(comments.map(c=>c.movieId))].filter(id=>movieIndex(id)<0).map(async id=>{
  try{const detail=await request(`/v1/media/MOVIE/${id}`);const index=registerSummary(detail);Object.assign(cachedMovies[index],uiMovie(detail,index));addDetailedCredits(detail)}catch(_){}
 }));
 for(const comment of comments){const index=movieIndex(comment.movieId);if(index>=0)movies[index].authorComment=comment}
  const listed=[...(page.items||[])].reverse();reviewIds=listed.map(r=>r.reviewId);
  for(const review of listed)if(review.media?.tmdbId)registerSummary(review.media);
  const users={};
  await Promise.all([...new Set(listed.map(r=>r.userId))].map(async id=>{
   try{const u=await request(`/v1/users/${id}/profile`);users[id]=uiUser(u,[],u)}catch(_){}
  }));
  const reviews=listed.map(r=>uiReview(r,users)).filter(r=>r.movieId>=0);
  if(session?.role==='user'){
   const liked=new Set(await request('/v1/site/me/review-likes'));
   for(const review of reviews)review.likedByMe=liked.has(reviewIds[review.id]);
  }
  let pendingReviews=[];
  if(session?.role==='user'){
   const mine=await request('/v2/reviews/mine?limit=100');
   for(const review of mine.items||[])if(review.media?.tmdbId)registerSummary(review.media);
   pendingReviews=(mine.items||[]).filter(r=>r.status!=='PUBLISHED').map(r=>{
    reviewIds.push(r.reviewId);
    return uiReview(r,{[r.userId]:session.user});
   }).filter(r=>r.movieId>=0);
  }
  for(const like of authorLikes){const index=reviewIds.indexOf(like.reviewId);const review=reviews.find(r=>r.id===index);if(review)review.authorLikes.push(like.author)}
  for(const review of reviews)movies[review.movieId].reviews++;
  for(const count of preferenceCounts){
   if(count.type==='MOVIE'){
    const index=movieIndex(count.tmdbId);if(index>=0)movies[index].preferred=count.count;
   }else{
    for(const author of Object.values(cachedAuthors)){
     const source=peopleSources.get(`${count.type}:${author.name}`);
     if(source?.id===count.tmdbId)author.preferred=count.count;
    }
   }
  }
  await Promise.all(movies.map(async movie=>{
   const scores=await request(`/v1/site/movies/${movie.apiId}/scores`);
   movie.ratings=scores.ratingsCount;
   movie.scoreSets={reviews:scores.reviews,ratings:scores.ratings,critics:scores.critics};
  }));
  for(const author of Object.values(cachedAuthors)){
   author.comments=comments.filter(c=>c.author===author.name).length;
   author.authorLikes=authorLikes.filter(l=>l.author===author.name).length;
   for(const work of author.works){
    const movie=movies.find(m=>m.title===work.title);
    if(movie){work.reviews=movie.reviews;work.ratings=movie.ratings;work.popularity=movie.preferred;
     work.scores=Object.fromEntries(Object.entries(movie.scoreSets).map(([key,value])=>[key,value?.total??null]));}
   }
   author.scores=Object.fromEntries(['reviews','ratings','critics'].map(key=>{
    const values=author.works.map(w=>w.scores?.[key]).filter(v=>v!==null&&v!==undefined);
    return [key,values.length?Math.round(values.reduce((sum,value)=>sum+value,0)/values.length):null];
   }));
  }
  cachedReviews=reviews;
  cachedComments=comments;cachedAuthorLikes=authorLikes;cachedPreferenceCounts=preferenceCounts;
  return {movies,reviews,pendingReviews,authors:cachedAuthors,people:peopleCache,
   users:Object.fromEntries(Object.values(users).map(u=>[u.nickname,u])),
   session,demoUser:null,demoAuthor:null};
 },
 async discoverMovies({page=1,sort='popular',year='',genreId='',country=''}){
  if(USE_MOCK)return {ids:mock.movies.map(m=>m.id),page:1,totalPages:1,totalResults:mock.movies.length};
  const params=new URLSearchParams({page:String(page),sort});
  if(year)params.set('year',String(year));
  if(genreId)params.set('genreId',String(genreId));
  if(country)params.set('country',country.toUpperCase());
  const result=await request(`/v1/media/discover/movies?${params}`);
  return {ids:(result.items||[]).map(registerSummary),page:result.page,totalPages:result.totalPages,totalResults:result.totalResults};
 },
 async searchMovies(query,page=1){
  if(USE_MOCK)return {ids:mock.movies.map(m=>m.id).filter(id=>mock.movies[id].title.toLowerCase().includes(query.toLowerCase())),page:1,totalPages:1,totalResults:0};
  const result=await request(`/v1/media/search/movies?query=${encodeURIComponent(query)}&page=${page}`);
  return {ids:(result.items||[]).map(registerSummary),page:result.page,totalPages:result.totalPages,totalResults:result.totalResults};
 },
 async hydrateMovie(index){
  if(USE_MOCK||cachedMovies[index]?.detailLoaded)return cachedMovies[index];
  if(detailRequests.has(index))return detailRequests.get(index);
  const task=(async()=>{
   const tmdbId=movieId(index);
   const [detail,scores,page]=await Promise.all([
    request(`/v1/media/MOVIE/${tmdbId}`),request(`/v1/site/movies/${tmdbId}/scores`),
    request(`/v2/reviews?tmdbId=${tmdbId}&mediaType=movie&limit=100`)]);
   const movie=cachedMovies[index],counts={reviews:movie.reviews,preferred:movie.preferred,authorComment:movie.authorComment,tmdbRating:movie.tmdbRating};
   Object.assign(movie,uiMovie(detail,index),counts);
   movie.ratings=scores.ratingsCount;movie.scoreSets={reviews:scores.reviews,ratings:scores.ratings,critics:scores.critics};
   movie.authorComment=cachedComments.find(c=>c.movieId===tmdbId)||null;
   movie.preferred=cachedPreferenceCounts.find(c=>c.type==='MOVIE'&&c.tmdbId===tmdbId)?.count||movie.preferred;
   addDetailedCredits(detail);
   for(const review of page.items||[]){
    if(reviewIds.includes(review.reviewId))continue;
    let user={};try{const profile=await request(`/v1/users/${review.userId}/profile`);user={[review.userId]:uiUser(profile,[],profile)}}catch(_){}
    reviewIds.push(review.reviewId);
    const item=uiReview(review,user);item.authorLikes=cachedAuthorLikes.filter(l=>l.reviewId===review.reviewId).map(l=>l.author);
    cachedReviews.push(item);movie.reviews++;
   }
   return movie;
  })().finally(()=>detailRequests.delete(index));
  detailRequests.set(index,task);return task;
 },
 async userAuth(mode,{login,password}){
  if(USE_MOCK)return {user:mock.demoUser};
  if(mode==='register'){
   const result=await request('/v1/auth/register',{method:'POST',body:{username:login,password},auth:false});saveTokens(result.tokens);
  }else saveTokens(await request('/v1/auth/login',{method:'POST',body:{username:login,password},auth:false}));
  const session=await loadSession();
  if(session?.role==='author'){saveTokens(null);throw new ApiError('Для этого аккаунта используйте вход автора',403)}
  return {user:session?.user};
 },
 async authorAuth(mode,{login,password,name,specialty}){
  if(USE_MOCK)return {author:{name:mock.demoAuthor}};
  if(mode==='register')return request('/v1/author/apply',{method:'POST',body:{login,password,name,specialty},auth:false});
  saveTokens(await request('/v1/author/login',{method:'POST',body:{login,password},auth:false}));
  const session=await loadSession();
  if(session?.role!=='author'){saveTokens(null);throw new ApiError('Заявка автора ещё не подтверждена',403)}
  return {author:session.author};
 },
 async logout(){if(USE_MOCK)return;try{if(tokens)await request('/v1/auth/logout',{method:'POST'})}finally{saveTokens(null);currentUser=null}},
 async updateProfile({nickname,bio,avatar}){
  if(USE_MOCK)return;
  if(avatar?.startsWith('data:'))avatar=(await request('/v1/me/avatar',{method:'POST',body:{dataUrl:avatar}})).avatarUrl;
  const profile=await request('/v1/me/profile');
  currentUser=await request('/v1/me/profile',{method:'PUT',body:{username:nickname,avatarUrl:avatar||null,bio:bio||'',preferences:profile.preferences}});
  return avatar;
 },
 async savePreferences({movies,directors,actors}){
  if(USE_MOCK)return;
  const old=(await request('/v1/me/preferences?size=100')).items||[];
  const chosen=[...movies.map(index=>({type:'MOVIE',id:movieId(index)})),
   ...directors.map(name=>({type:'DIRECTOR',...peopleSources.get(`DIRECTOR:${name}`)})),
   ...actors.map(name=>({type:'ACTOR',...peopleSources.get(`ACTOR:${name}`)}))].filter(item=>item.id);
  const key=item=>`${item.type}:${item.id??item.tmdbId}`;
  const wanted=new Set(chosen.map(key)),existing=new Set(old.map(key));
  for(const item of old)if(!wanted.has(key(item)))await request(`/v1/me/preferences/${item.type}/${item.tmdbId}`,{method:'DELETE'});
  for(const item of chosen)if(!existing.has(key(item)))await request(`/v1/me/preferences/${item.type}/${item.id}`,{method:'PUT',
   ...(item.type==='MOVIE'?{}:{body:{sourceMediaType:item.sourceMediaType,sourceTmdbId:item.sourceTmdbId}})});
 },
 async publishReview({movieId,title,text,parts,spoiler}){
  if(USE_MOCK)return null;
  const rating=await rate(movieId,parts);
  const created=await request('/v2/reviews',{method:'POST',body:{tmdbId:movieId===undefined?null:movieIds[movieId],mediaType:'MOVIE',title,content:text,draft:false,spoiler}});
  reviewIds.push(created.reviewId);
  const review=uiReview(created,{[currentUser.userId]:{nickname:currentUser.username,avatar:currentUser.avatarUrl}});
  review.total=rating.finalScore;review.parts=parts;
  return review;
 },
 async saveRating({movieId,parts}){return USE_MOCK?null:rate(movieId,parts)},
 async setReviewLike(id,liked){if(!USE_MOCK)await request(`/v2/reviews/${reviewIds[id]}/like`,{method:liked?'PUT':'DELETE'})},
 async setAuthorLike(id,liked){if(!USE_MOCK)await request(`/v1/author/reviews/${reviewIds[id]}/like`,{method:liked?'PUT':'DELETE'})},
 async publishAuthorComment({movieId,title,text}){return USE_MOCK?null:request(`/v1/author/movies/${movieId===undefined?null:movieIds[movieId]}/comment`,{method:'PUT',body:{title,text}})},
 async setAuthorCommentLike(movieId,liked){if(!USE_MOCK)await request(`/v1/author/movies/${movieIds[movieId]}/comment/like`,{method:liked?'PUT':'DELETE'})}
};
