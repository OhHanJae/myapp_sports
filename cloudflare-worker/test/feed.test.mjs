import test from "node:test";
import assert from "node:assert/strict";
import worker, { classifyLeague, normalizeMatch, convertKbo, convertKboPostseason, parseOfficialKboSchedule, buildFeed } from "../src.mjs";

const team = (id, name, acronym) => ({ id, name, acronym, image_url: "" });
const gen = team(1, "Gen.G", "GEN"), t1 = team(2, "T1", "T1"), hle = team(3, "Hanwha Life Esports", "HLE");
const match = (id, league, tournament, date, status, a = gen, b = t1) => ({
  id, league: { id: league === "LCK" ? 11 : 12, name: league },
  serie: { full_name: league + " 2026" },
  tournament: { id: tournament, name: tournament === 31 ? "Regular Season" : "Knockout" },
  begin_at: date, status, name: "Round " + id, number_of_games: 5,
  opponents: [{ opponent: a }, { opponent: b }],
  results: [{team_id:a.id,score:3}, {team_id:b.id,score:1}]
});
const fixtureLck = match(101, "LCK", 31, "2026-10-30T09:00:00Z", "finished");
const fixtureWorlds = match(102, "Worlds", 32, "2026-11-01T09:00:00Z", "not_started", hle, t1);
const kbo = [{
  gamePk:"20261001LTSS0",gameDate:"2026-10-01T18:30:00+09:00",
  status:{abstractGameState:"F",detailedState:"Final"},isPostseason:false,
  teams:{
    away:{team:{abbrev:"LOT"},score:4},
    home:{team:{abbrev:"SAM"},score:7}
  }
}];
const reply = body => ({ok:true,headers:{get:()=>""},json:async()=>body});
const row = cells => ({row:cells.map(([Class,Text])=>({Class,Text}))});
function fetcher(url, options = {}) {
  if (url.includes("raw.githubusercontent.com")) return Promise.resolve(reply(kbo));
  if (url.includes("koreabaseball.com/Schedule/Schedule.aspx")) return Promise.resolve(reply({}));
  if (url.includes("koreabaseball.com/ws/Schedule.asmx/GetScheduleList")) {
    const october = row([
      ["day","10.26(월)"],["time","<b>18:30</b>"],
      ["play",'<span>KIA</span><em><span class="win">5</span><span>vs</span><span class="lose">1</span></em><span>삼성</span>'],
      ["relay","<a href='?gameId=20261026HTSS0&section=REVIEW' id='btnReview'>리뷰</a>"]
    ]);
    const november = row([
      ["day","11.01(일)"],["time","<b>14:00</b>"],
      ["play",'<span>KIA</span><em><span class="same">0</span><span>vs</span><span class="same">0</span></em><span>삼성</span>'],
      ["relay",""]
    ]);
    const novemberKs = row([
      ["day","11.03(화)"],["time","<b>18:30</b>"],
      ["play","<span>PO<br/>승리팀</span><em><span>vs</span></em><span>KT</span>"],
      ["relay",""]
    ]);
    return Promise.resolve(reply({
      rows: options.body?.get("gameMonth")==="11" ? [november, novemberKs] : [october]
    }));
  }
  if (url.includes("/lol/matches/upcoming")) return Promise.resolve(reply([fixtureWorlds]));
  if (url.includes("/lol/matches/running")) return Promise.resolve(reply([]));
  if (url.includes("/lol/matches/past")) return Promise.resolve(reply([]));
  if (url.includes("/lol/leagues?")) return Promise.resolve(reply([
    {id:11,name:"LCK"}, {id:12,name:"Worlds"}
  ]));
  if (url.includes("/leagues/11/matches")) return Promise.resolve(reply([fixtureLck]));
  if (url.includes("/leagues/12/matches")) return Promise.resolve(reply([fixtureWorlds]));
  if (url.includes("/standings")) return Promise.resolve(reply(
    [{rank:1,team:gen,wins:12,losses:1,games_won:25,games_lost:8},
     {rank:2,team:t1,wins:11,losses:2,games_won:24,games_lost:10}]
  ));
  if (url.includes("/brackets")) return Promise.resolve(reply([
    {...fixtureWorlds,previous_matches:[]}
  ]));
  throw new Error("Unexpected URL " + url);
}
test("league classification distinguishes global events and LCK Academy", () => {
  assert.equal(classifyLeague({name:"LCK"}), "LCK");
  assert.equal(classifyLeague({name:"LCK Challengers"}), null);
  assert.equal(classifyLeague({name:"Worlds"}), "WORLDS");
  assert.equal(classifyLeague({name:"MSI"}), "MSI");
});
test("match timestamps are KST and Gen.G favorite ID is stable", () => {
  const m = normalizeMatch(fixtureLck,"LCK");
  assert.equal(m.home,"GEN");
  assert.equal(m.dateTime,"2026-10-30T18:00:00.000+09:00");
  assert.equal(m.status,"FINAL");
});
test("KBO regular-season scores generate standings, not fictional playoffs", () => {
  const data = convertKbo(kbo,2026);
  assert.equal(data.games.length,1);
  assert.equal(data.games[0].home,"SAM");
  assert.equal(data.games[0].homeScore,7);
  assert.equal(data.standings.find(s=>s.team==="SAM").won,1);
  assert.equal(data.series.length,0);
});
test("full feed retains LoL international matches, standings, brackets and KBO",async()=>{
  const feed = await buildFeed({PANDASCORE_TOKEN:"not-a-real-token"},fetcher,2026);
  assert.equal(feed.isDemo,false);
  assert.ok(feed.games.some(x=>x.competition==="KBO"));
  assert.ok(feed.games.some(x=>x.id.includes("20261026HTSS0")));
  assert.ok(feed.games.some(x=>x.home==="SAM"&&x.away==="KIA"&&x.dateTime.startsWith("2026-11-01")));
  assert.ok(feed.games.some(x=>x.home==="KT"&&x.away==="TBD"&&x.dateTime.startsWith("2026-11-03")));
  assert.ok(feed.series.some(x=>x.competition==="KBO" && x.stage==="플레이오프"));
  assert.ok(feed.series.some(x=>x.competition==="KBO" && x.stage==="한국시리즈"));
  assert.ok(feed.games.some(x=>x.competition==="WORLDS"));
  assert.ok(feed.games.some(x=>x.competition==="LCK"));
  assert.ok(feed.standings.some(x=>x.competition==="LCK"));
  assert.ok(feed.series.some(x=>x.competition==="WORLDS"));
  assert.ok(feed.diagnostics.counts.KBO>0);
  assert.ok(feed.diagnostics.counts.KBOPostseason>0);
  assert.ok(feed.diagnostics.counts.LOL>0);
});
test("postseason series are derived from actual scheduled results",()=>{
  const regular = [{team:"KT",rank:1},{team:"SAM",rank:2},{team:"KIA",rank:3}];
  const r=convertKboPostseason([{
    gameId:"PO5",categoryId:"kbo",gameDateTime:"2026-11-01T14:00:00+09:00",
    homeTeamCode:"SS",homeTeamName:"삼성",homeTeamScore:4,
    awayTeamCode:"HT",awayTeamName:"KIA",awayTeamScore:2,statusCode:"RESULT"
  }],regular,2026);
  assert.equal(r.games[0].home,"SAM");
  assert.equal(r.games[0].away,"KIA");
  assert.equal(r.series[0].stage,"플레이오프");
  assert.equal(r.series[0].leftWins,1);
  assert.equal(r.series[0].group,"MAIN");
});
test("official 0:0 without REVIEW is not falsely marked FINAL",()=>{
  const fixtures=[row([
    ["day","11.01(일)"],["time","<b>14:00</b>"],
    ["play",'<span>KIA</span><em><span class="same">0</span><span>vs</span><span class="same">0</span></em><span>삼성</span>'],
    ["relay",""]
  ])];
  const api=parseOfficialKboSchedule({rows:fixtures},2026,11,Date.parse("2026-11-01T15:00:00+09:00"));
  assert.equal(api.length,1);
  assert.equal(api[0].statusCode,"UNCERTAIN");
  assert.equal(api[0].homeTeamScore,null);
  assert.equal(api[0].awayTeamScore,null);
  const series=convertKboPostseason(api,[{team:"KT",rank:1},{team:"SAM",rank:2},{team:"KIA",rank:3}],2026);
  assert.equal(series.games[0].stage,"플레이오프");
  assert.equal(series.games[0].status,"UNKNOWN");
  assert.equal(series.series[0].status,"UNKNOWN");
});
test("KBO official playoff winner placeholders create a Korean Series bracket",()=>{
  const x=parseOfficialKboSchedule({rows:[row([
    ["day","11.03(화)"],["time","<b>18:30</b>"],
    ["play","<span>PO<br/>승리팀</span><em><span>vs</span></em><span>KT</span>"]
  ])]},2026,11,Date.parse("2026-11-01T10:00:00+09:00"));
  const out=convertKboPostseason(x,[{team:"KT",rank:1},{team:"SAM",rank:2},{team:"KIA",rank:3}],2026);
  assert.equal(x[0].awayTeamCode,"TBD");
  assert.equal(out.series[0].stage,"한국시리즈");
  assert.equal(out.series[0].left,"KT");
  assert.equal(out.series[0].right,"TBD");
});
test("feed endpoint denies incorrect key; health never exposes secrets",async()=>{
  const env={PANDASCORE_TOKEN:"testing",FEED_ACCESS_KEY:"private"};
  const health=await worker.fetch(new Request("https://fake.workers.dev/health"),env,{});
  assert.equal(health.status,200);
  const j=await health.json();
  assert.equal(j.keyConfigured,true);
  assert.equal(j.version,3);
  assert.equal(JSON.stringify(j).includes("private"),false);
  const denied=await worker.fetch(new Request("https://fake.workers.dev/feed.json?key=wrong"),env,{});
  assert.equal(denied.status,403);
});
