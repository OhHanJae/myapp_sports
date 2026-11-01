import test from "node:test";
import assert from "node:assert/strict";
import { buildLiveFeed, normalizeOfficialLiveBoard, LIVE_CACHE_SECONDS } from "../src.mjs";

const now = Date.parse("2026-11-01T16:14:00+09:00");
const row = (...cells) => ({row:cells.map(([Class, Text]) => ({Class, Text}))});
const schedule = {rows:[
  row(["day","11.01(일)"],["time","<b>14:00</b>"],
    ["play",'<span>KIA</span><em><span class="same">0</span><span>vs</span><span class="same">0</span></em><span>삼성</span>'],
    ["relay",""]),
  row(["day","11.03(화)"],["time","<b>18:30</b>"],
    ["play","<span>PO<br/>승리팀</span><em><span>vs</span></em><span>KT</span>"],
    ["relay",""])
]};
const match = {
  id: 888, status:"running",
  begin_at:"2026-11-01T07:00:00Z",
  league:{name:"Worlds"},serie:{name:"Worlds 2026"},
  tournament:{id:201,name:"Bracket"},
  opponents:[
    {opponent:{id:40,name:"Gen.G",acronym:"GEN"}},
    {opponent:{id:41,name:"T1",acronym:"T1"}}
  ],
  results:[{team_id:40,score:2},{team_id:41,score:1}]
};
let requests=[];
const reply = value => ({ok:true,headers:{get:()=>""},json:async()=>value});
async function fakeFetch(url,options={}) {
  requests.push({url,method:options.method||"GET"});
  if(url.includes("ScoreBoard.aspx")) return reply("");
  if(url.includes("GetScheduleList")) return reply(schedule);
  if(url.includes("GetScoreBoardScroll")) return reply({
    G_ID:"20261101HTSS0",SR_ID:5,code:"100",START_TM:"14:01",END_TM:"",
    B_SCORE_CN:4,T_SCORE_CN:3,
    table2:JSON.stringify({headers:[{row:[{Text:"1"},{Text:"2"},{Text:"3"}]}],
      rows:[{row:[{Text:"0"},{Text:"2"},{Text:"1"}]},
            {row:[{Text:"1"},{Text:"1"},{Text:"2"}]}]})
  });
  if(url.includes("/lol/matches/running")) return reply([match]);
  if(url.includes("/lol/matches/past")) return reply([]);
  throw Error("Unexpected " + url);
}
test("running KBO and LoL data are returned without expensive tournament/standings calls", async()=>{
  requests=[];
  const live=await buildLiveFeed({PANDASCORE_TOKEN:"mock"},fakeFetch,now);
  assert.equal(LIVE_CACHE_SECONDS,55);
  assert.equal(live.schemaVersion,1);
  const kbo=live.games.find(g=>g.competition==="KBO");
  assert.equal(kbo.status,"LIVE");
  assert.equal(kbo.home,"SAM");
  assert.equal(kbo.away,"KIA");
  assert.equal(kbo.homeScore,4);
  assert.equal(kbo.awayScore,3);
  assert.equal(kbo.inning,"3회");
  const lol=live.games.find(g=>g.competition==="WORLDS");
  assert.equal(lol.status,"LIVE");
  assert.equal(lol.home,"GEN");
  assert.equal(lol.homeScore,2);
  assert.equal(lol.awayScore,1);
  assert.equal(live.teams.length,2);
  assert.ok(requests.some(x=>x.url.includes("GetScoreBoardScroll")&&x.method==="POST"));
  assert.ok(!requests.some(x=>x.url.includes("/standings")||x.url.includes("/brackets")));
  assert.ok(requests.length<=5);
});
test("KBO scoreboard failure does not invent live scores",async()=>{
  const notAvailable=normalizeOfficialLiveBoard({G_ID:"20261101HTSS0",code:"200",msg:"not ready"},
    {id:"NPS_20261101_KIA_SAM",home:"SAM",away:"KIA",
      dateTime:"2026-11-01T14:00:00+09:00",status:"UNKNOWN"},
    "2026-11-01T16:14:00+09:00",now);
  assert.equal(notAvailable,null);
});
test("KBO inactive season makes no public KBO requests",async()=>{
  requests=[];
  const x=await buildLiveFeed({PANDASCORE_TOKEN:"mock"},fakeFetch,
    Date.parse("2027-02-01T14:00:00+09:00"));
  assert.equal(x.diagnostics.counts.KBO,0);
  assert.ok(!requests.some(x=>x.url.includes("koreabaseball.com")));
});
