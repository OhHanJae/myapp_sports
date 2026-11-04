import test from "node:test";
import assert from "node:assert/strict";
import worker, {buildFeed} from "../src.mjs";

const sample = {
  schemaVersion: 1, updatedAt: "2026-11-04T12:00:00+09:00",
  isDemo: false, demoDay: "2026-11-04",
  teams: [{id:"SAM",sport:"KBO",name:"삼성 라이온즈",short:"삼성",mark:"SAM",logoUrl:""}],
  games: [], standings: [], series: [], sources:{KBO:"test", LOL:"test"},
  diagnostics:{counts:{KBO:0,LOL:0},warnings:[]}
};

test("a stale full-feed snapshot returns immediately, without upstream calls", async () => {
  const prior = globalThis.caches;
  const calls=[];
  globalThis.caches = {default:{
    match: async req => {
      const k = new URL(req.url).pathname;
      if(k.endsWith("/__matchday_feed_v5"))return new Response(JSON.stringify(sample),{
        headers:{"content-type":"application/json"}});
      if(k.endsWith("/__matchday_feed_v5_refresh"))return new Response("in progress");
      return undefined;
    },
    put: async req => { calls.push(new URL(req.url).pathname); }
  }};
  try {
    const ctx={waitUntil:()=>{throw Error("Must not refresh with a lock in place");}};
    const env={PANDASCORE_TOKEN:"dummy",FEED_ACCESS_KEY:"dummy-access"};
    const start=Date.now();
    const response=await worker.fetch(
      new Request("https://matchday.workers.dev/feed.json?key=dummy-access"),env,ctx);
    assert.equal(response.status,200);
    const data=await response.json();
    assert.equal(data.teams[0].id,"SAM");
    assert.ok(Date.now()-start<2500,"cached response should not block on upstream");
    assert.deepEqual(calls,[]);
    const status=await worker.fetch(
      new Request("https://matchday.workers.dev/status?key=dummy-access"),env,ctx);
    const info=await status.json();
    assert.equal(info.cache,"stale-while-refreshing");
    assert.ok(info.ageSeconds>=0);
  } finally {
    if(prior===undefined) delete globalThis.caches;
    else globalThis.caches=prior;
  }
});

test("slow standings API fetches are concurrent across competitions",async()=>{
  const terms=["LCK","MSI","Esports World Cup","Worlds","First Stand"];
  const leagueId=["LCK","MSI","EWC","WORLDS","FST"];
  const matches=terms.map((name,i)=>({
    id:100+i, league:{id:50+i,name},serie:{name},
    tournament:{id:200+i,name:"Regular Season 2026"},
    begin_at:"2026-11-04T10:00:00Z",status:"finished",
    opponents:[{opponent:{id:300+i*2,name:"Team "+i,acronym:"T"+i}},
               {opponent:{id:301+i*2,name:"Other "+i,acronym:"O"+i}}],
    results:[{team_id:300+i*2,score:3},{team_id:301+i*2,score:1}]
  }));
  let pendingStandings=0, maxConcurrentStandings=0;
  const resp = data => ({
    ok:true,headers:{get:()=>""},
    json:async()=>data
  });
  const fakeFetch=async (url,opts={})=>{
    if(url.includes("/lol/matches/upcoming"))return resp(matches);
    if(url.includes("/lol/matches/running")||url.includes("/lol/matches/past"))
      return resp([]);
    if(url.includes("/lol/leagues?"))return resp([]);
    if(url.includes("/standings?")){
      pendingStandings++;
      maxConcurrentStandings=Math.max(maxConcurrentStandings,pendingStandings);
      await new Promise(done=>setTimeout(done,30));
      pendingStandings--;
      return resp([]); 
    }
    if(url.includes("/brackets?"))return resp([]);
    if(url.includes("raw.githubusercontent.com"))return resp([]);
    if(url.endsWith("Schedule/Schedule.aspx"))return resp({});
    if(url.includes("GetScheduleList"))return resp({rows:[]});
    throw Error("Unexpected mocked request "+url);
  };
  const feed=await buildFeed({PANDASCORE_TOKEN:"fake"},fakeFetch,2026);
  assert.ok(feed.teams.length>=10); // KBO teams must not disappear if upstream is slow.
  assert.equal(feed.isDemo,false);
  assert.ok(feed.games.length>=5);
  assert.ok(maxConcurrentStandings>=2,
    "standings were fetched serially and can still trigger phone read timeout");
});
