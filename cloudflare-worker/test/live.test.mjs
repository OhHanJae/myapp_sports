import test from "node:test";
import assert from "node:assert/strict";
import { buildLiveFeed, normalizeOfficialLiveBoard, parseOfficialLiveScoreHtml, officialWebScoreToGame, LIVE_CACHE_SECONDS } from "../src.mjs";

const now = Date.parse("2026-11-01T16:14:00+09:00");
const row = (...cells) => ({row:cells.map(([Class, Text]) => ({Class, Text}))});

const scoreboardHtml=String.raw`
<div class="smsScore">
 <p class='leftTeam'><strong class='teamT'>KIA</strong>
 <em class="score"><span id="cphContents_rptGameList_lblAwayTeamScore_0">1</span></em></p>
 <strong class="flag"><span id="cphContents_rptGameList_lblGameState_0">8회말</span></strong>
 <p class='rightTeam'><em class="score">
 <span id="cphContents_rptGameList_lblHomeTeamScore_0">6</span></em>
 <strong class='teamT'>삼성</strong></p>
 <div class="btnSms"><a onclick="window.open('/Game/LiveText.aspx?leagueId=1&seriesId=5&gameId=20261101HTSS0&gyear=2026')">중계</a></div>
</div>
`;

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
const reply = value => ({ok:true,headers:{get:()=>""},json:async()=>value,text:async()=>typeof value==="string"?value:JSON.stringify(value)});
async function fakeFetch(url,options={}) {
  requests.push({url,method:options.method||"GET"});
  if(url.includes("ScoreBoard.aspx")) return reply(scoreboardHtml);
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
  assert.equal(kbo.homeScore,6);
  assert.equal(kbo.awayScore,1);
  assert.equal(kbo.inning,"8회말");
  const lol=live.games.find(g=>g.competition==="WORLDS");
  assert.equal(lol.status,"LIVE");
  assert.equal(lol.home,"GEN");
  assert.equal(lol.homeScore,2);
  assert.equal(lol.awayScore,1);
  assert.equal(live.teams.length,2);
  assert.ok(!requests.some(x=>x.url.includes("GetScoreBoardScroll")));
  assert.ok(requests.some(x=>x.url.includes("ScoreBoard.aspx")));
  assert.ok(!requests.some(x=>x.url.includes("/standings")||x.url.includes("/brackets")));
  assert.ok(requests.length<=5);
});
test("official live HTML accepts running score but rejects pregame 0:0",()=>{
  const live=parseOfficialLiveScoreHtml(scoreboardHtml);
  assert.equal(live.length,1);
  assert.equal(live[0].gameId,"20261101HTSS0");
  assert.equal(live[0].homeScore,6);
  assert.equal(live[0].awayScore,1);
  assert.equal(live[0].status,"LIVE");
  assert.equal(live[0].inning,"8회말");
  const pregame=scoreboardHtml
    .replace("8회말","경기전").replace(">1</span>",">0</span>")
    .replace(">6</span>",">0</span>");
  assert.equal(parseOfficialLiveScoreHtml(pregame).length,0);
});
test("KBO live runs survive an unavailable or stale monthly schedule",async()=>{
  const local=[];
  const report=await buildLiveFeed({PANDASCORE_TOKEN:"mock"},async(url,options={})=>{
    local.push(url);
    if(url.includes("ScoreBoard.aspx"))return reply(scoreboardHtml);
    if(url.includes("GetScheduleList"))throw Error("Schedule API blocked");
    if(url.includes("/lol/matches/"))return reply([]);
    throw Error("Unexpected "+url);
  },now);
  assert.equal(report.diagnostics.counts.KBO,1);
  const game=report.games.find(g=>g.competition==="KBO");
  assert.deepEqual([game.home,game.away,game.homeScore,game.awayScore],
    ["SAM","KIA",6,1]);
  assert.equal(game.inning,"8회말");
  assert.equal(game.status,"LIVE");
  assert.ok(game.dateTime.startsWith("2026-11-01T16:14"));
  assert.ok(!local.some(x=>x.includes("GetScheduleList")),
    "A valid live scoreboard must not block on a stale schedule");
});
test("KBO never fabricates a game from an invalid scoreboard ID",()=>{
  const invalid=scoreboardHtml.replace("20261101HTSS0","20261101XXSS0");
  assert.equal(parseOfficialLiveScoreHtml(invalid).length,1);
  // The scoreboard adapter must reject unknown official team codes.
  const games=officialWebScoreToGame({gameId:"20261101XXSS0",status:"LIVE",
    homeScore:3,awayScore:1},"2026-11-01T16:14:00+09:00",now);
  assert.equal(games,null);
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
