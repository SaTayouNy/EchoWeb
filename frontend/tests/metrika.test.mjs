import test from 'node:test';
import assert from 'node:assert/strict';
import {MetricaClient, counterIdFrom, virtualPage} from '../src/metrika.js';

function browser({host='echo.example',protocol='https:',counter='12345678',consent=null}={}) {
  const saved=new Map();
  if(consent!==null)saved.set('echo:metrika:consent:v1',consent);
  const scripts=[];
  const makeEl=tag=>{
    const events={};
    const element={tagName:tag, dataset:{}, children:[], className:'', attributes:{},
      setAttribute(k,v){this.attributes[k]=v},
      addEventListener(k,fn){events[k]=fn},
      querySelector(selector){
        if(selector==='[data-metrica-consent="yes"]')return {addEventListener:(_,fn)=>{this.yes=fn}};
        if(selector==='[data-metrica-consent="no"]')return {addEventListener:(_,fn)=>{this.no=fn}};
        return null;
      },
      remove(){this.removed=true},
      clickYes(){this.yes?.()},clickNo(){this.no?.()},
    };
    return element;
  };
  const doc={body:{children:[],appendChild(e){this.children.push(e)}},head:{appendChild(e){scripts.push(e)}},createElement:makeEl};
  const win={__ECHO_YANDEX_METRIKA_ID__:counter,location:{hostname:host,protocol,origin:`${protocol}//${host}`},localStorage:{getItem:k=>saved.get(k)??null,setItem:(k,v)=>saved.set(k,v)}};
  return {win,doc,saved,scripts};
}

test('Metrica is disabled with missing or invalid ID',()=>{
  for(const id of ['', 'x', '12x', '1', '1234567890123']){
    const b=browser({counter:id});
    const m=new MetricaClient({win:b.win,doc:b.doc});
    assert.equal(m.enabled,false);assert.equal(b.doc.body.children.length,0);assert.equal(b.scripts.length,0);
  }
  assert.equal(counterIdFrom('12345678'),12345678);
});

test('Metrica never loads on file:// or localhost',()=>{
  for(const options of [{host:'localhost'},{host:'127.0.0.1'},{host:'echo.example',protocol:'file:'}]){
    const b=browser({...options,consent:'yes'});
    const m=new MetricaClient({win:b.win,doc:b.doc});
    m.pageView({pageType:'home',routeKey:'home'});
    assert.equal(b.scripts.length,0);
  }
});

test('Without consent, no third-party requests; accepting starts exactly once',()=>{
  const b=browser();const m=new MetricaClient({win:b.win,doc:b.doc});
  assert.equal(b.doc.body.children.length,1);
  m.pageView({pageType:'home',routeKey:'site:home'});
  assert.equal(b.scripts.length,0);assert.equal(b.win.ym,undefined);
  b.doc.body.children[0].clickYes();
  assert.equal(b.scripts.length,1);assert.equal(b.scripts[0].src,'https://mc.yandex.ru/metrika/tag.js?id=12345678');
  assert.equal(b.saved.get('echo:metrika:consent:v1'),'yes');
  const calls=b.win.ym.a.map(args=>Array.from(args));
  assert.equal(calls[0][1],'init');assert.equal(calls[0][2].defer,true);
  assert.equal(calls[0][2].webvisor,true);
  assert.equal(calls[0][2].ssr,true);
  assert.equal(calls[0][2].ecommerce,'dataLayer');
  assert.equal(calls[0][2].clickmap,true);
  assert.equal(calls[0][2].trackLinks,true);
  assert.equal(calls[0][2].accurateTrackBounce,true);
  assert.equal(calls[0][2].url,'https://echo.example/_echo/');
  assert.ok(Array.isArray(b.win.dataLayer));
  assert.equal(calls[1][1],'hit');assert.equal(calls[1][2],'https://echo.example/_echo/');
  assert.equal(m.pageView({pageType:'home',routeKey:'site:home'}),false);
  assert.equal(b.win.ym.a.length,2);
  m.start();assert.equal(b.scripts.length,1);
});

test('Rejecting consent disables tracking across reloads',()=>{
  const b=browser();const m=new MetricaClient({win:b.win,doc:b.doc});
  m.pageView({pageType:'home',routeKey:'home'});
  b.doc.body.children[0].clickNo();
  assert.equal(b.scripts.length,0);
  assert.equal(b.saved.get('echo:metrika:consent:v1'),'no');
  const again=new MetricaClient({win:b.win,doc:b.doc});
  again.pageView({pageType:'film',filmId:123,routeKey:'film:123'});
  assert.equal(b.scripts.length,0);
});

test('SPA views are distinct, deduplicated, and sanitized',()=>{
  const b=browser({consent:'yes'});const m=new MetricaClient({win:b.win,doc:b.doc});
  m.pageView({pageType:'search',page:'search',routeKey:'search:личные данные'});
  m.pageView({pageType:'search',page:'search',routeKey:'search:личные данные'});
  m.pageView({pageType:'film',page:'movie',filmId:456,routeKey:'film:456',userId:99887766});
  m.pageView({pageType:'author_profile',page:'authorProfile',routeKey:'author:Иван Петров'});
  const hits=b.win.ym.a.map(x=>Array.from(x)).filter(x=>x[1]==='hit');
  assert.equal(hits.length,3);
  assert.deepEqual(hits.map(x=>x[2]),['https://echo.example/_echo/search','https://echo.example/_echo/film/456','https://echo.example/_echo/author']);
  assert.equal(hits[1][3].referer,'https://echo.example/_echo/search');
  assert.ok(!JSON.stringify(hits).includes('Иван Петров'));
  assert.ok(!JSON.stringify(hits).includes('личные данные'));
  assert.ok(!JSON.stringify(hits).includes('99887766'));
});

test('Auth and Studio use safe virtual URLs; goals are opt-in and allowlisted',()=>{
  assert.equal(virtualPage({view:'userAuth',userAuth:'register'}).path,'/auth/user/register');
  assert.equal(virtualPage({view:'studio',studioPage:'anything-sensitive'}).path,'/studio/dashboard');
  const b=browser({consent:'yes'});const m=new MetricaClient({win:b.win,doc:b.doc});
  assert.equal(m.goal('review_started'),true);
  assert.equal(m.goal('user_registered'),false); // BE owns this event.
  assert.equal(m.goal('secret_token'),false);
  assert.equal(b.win.ym.a.filter(x=>x[1]==='reachGoal').length,1);
});

test('v10.40: counter ID comes from VITE_YANDEX_METRIKA_ID via options',()=>{
  const b=browser({counter:''});
  assert.equal(new MetricaClient({win:b.win,doc:b.doc,id:'114072239'}).id,114072239);
  assert.equal(new MetricaClient({win:b.win,doc:b.doc,id:'abc'}).enabled,false);
  assert.equal(new MetricaClient({win:b.win,doc:b.doc,id:undefined}).enabled,false);
});


test('Webvisor masks inputs and private content on each SPA render',()=>{
  const b=browser({consent:'yes'});
  const marks=[];
  b.doc.querySelectorAll=()=>[
    {classList:{add:(name)=>marks.push(['input',name])},matches:()=>true},
    {classList:{add:(name)=>marks.push(['review',name])},matches:()=>false},
  ];
  const m=new MetricaClient({win:b.win,doc:b.doc});
  m.pageView({pageType:'film',filmId:123,routeKey:'film:123'});
  assert.ok(marks.some(([item,name])=>item==='input'&&name==='ym-disable-keys'));
  assert.ok(marks.some(([item,name])=>item==='review'&&name==='ym-hide-content'));
});

test('Metrica does not expose query parameters from external referrers',()=>{
  const b=browser({consent:'yes'});
  b.doc.referrer='https://search.example/search?q=private+query&token=abc#secret';
  new MetricaClient({win:b.win,doc:b.doc});
  const init=b.win.ym.a.map(x=>Array.from(x)).find(x=>x[1]==='init');
  assert.equal(init[2].referrer,'https://search.example/');
  assert.ok(!JSON.stringify(init).includes('private+query'));
  assert.ok(!JSON.stringify(init).includes('token=abc'));
});
