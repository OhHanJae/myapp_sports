import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src.mjs";

const key = "private+ampersand&equal=token";
const env = {PANDASCORE_TOKEN:"not-the-feed-access-key", FEED_ACCESS_KEY:key};
const fixture = {
  schemaVersion:1,updatedAt:"2026-11-04T19:45:00+09:00",
  isDemo:false,demoDay:"2026-11-04",
  teams:[{id:"KT",sport:"KBO",name:"KT 위즈",short:"KT",mark:"KT",logoUrl:""}],
  games:[],standings:[],series:[],sources:{KBO:"test"},diagnostics:{warnings:[]}
};
const live = {
  schemaVersion:1,updatedAt:"2026-11-04T20:00:00+09:00",
  games:[],teams:[],diagnostics:{warnings:[]}
};

test("both /feed.json and /live.json require the SAME correctly encoded key",async()=>{
  const old = globalThis.caches;
  let cacheReads=0;
  globalThis.caches={default:{
    match:async req=>{
      cacheReads++;
      const pathname=new URL(req.url).pathname;
      if(pathname.endsWith("__matchday_feed_v5"))
        return new Response(JSON.stringify(fixture));
      if(pathname.endsWith("__matchday_live_v6"))
        return new Response(JSON.stringify(live));
      throw Error("Unexpected cache lookup "+pathname);
    }
  }};
  try {
    const base="https://test.workers.dev";
    const escaped=encodeURIComponent(key);
    for(const path of ["/feed.json","/live.json","/status"]) {
      const res=await worker.fetch(new Request(base+path+"?key="+escaped),env,{});
      assert.equal(res.status,200,path);
      const payload=await res.json();
      if(path==="/feed.json")assert.equal(payload.teams[0].id,"KT");
      if(path==="/live.json")assert.deepEqual(payload.games,[]);
    }
    assert.equal(cacheReads,3);
    for(const path of ["/feed.json","/live.json","/status"]) {
      for(const suffix of ["","?key=wrong","?key="+encodeURIComponent(env.PANDASCORE_TOKEN)]) {
        const res=await worker.fetch(new Request(base+path+suffix),env,{});
        assert.equal(res.status,403,path+suffix);
        const payload=await res.json();
        assert.equal(payload.error,"Forbidden");
        assert.equal(payload.code,"INVALID_FEED_ACCESS_KEY");
        assert.ok(payload.hint.includes("?key="));
        assert.ok(!JSON.stringify(payload).includes(key));
        assert.ok(!JSON.stringify(payload).includes(env.PANDASCORE_TOKEN));
      }
    }
    assert.equal(cacheReads,3,"unauthorized requests must not touch caches");
  }finally {
    if(old===undefined)delete globalThis.caches;
    else globalThis.caches=old;
  }
});

test("missing Worker secrets return a service configuration error, not 403",async()=>{
  const res=await worker.fetch(
    new Request("https://test.workers.dev/live.json?key=some-value"),
    {PANDASCORE_TOKEN:"",FEED_ACCESS_KEY:""},{});
  assert.equal(res.status,503);
  assert.equal((await res.json()).error,"Secrets missing");
});
