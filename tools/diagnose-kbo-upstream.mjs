// One-off safe public endpoint diagnostic for KBO postseason. No credentials.
const host = "https://api-gw.sports.naver.com/schedule/games";
const checks = [
  ["broad1","2026-10-14","2026-10-31"],
  ["broad2","2026-11-01","2026-11-20"],
  ["onePO","2026-10-26","2026-10-26"],
  ["oneToday","2026-11-01","2026-11-01"],
  ["sevenDays","2026-10-26","2026-11-01"]
];
for (const [label,from,to] of checks) {
 const url=host+"?"+new URLSearchParams({upperCategoryId:"kbaseball",fromDate:from,toDate:to});
 try {
  const resp=await fetch(url,{headers:{"Accept":"application/json","User-Agent":"Mozilla/5.0"},signal:AbortSignal.timeout(12000)});
  const raw=await resp.text();
  const parsed=JSON.parse(raw);
  const games=parsed?.result?.games;
  const cats=Object.entries((Array.isArray(games)?games:[]).reduce((acc,g)=>{const k=g.categoryId||"null";acc[k]=(acc[k]||0)+1;return acc;},{}));
  console.log(JSON.stringify({label,status:resp.status,ok:parsed.success,rows:Array.isArray(games)?games.length:null,categories:cats,sample:(games||[]).slice(0,3).map(x=>({gameId:x.gameId,categoryId:x.categoryId,home:x.homeTeamName,away:x.awayTeamName,date:x.gameDateTime,status:x.statusCode})),reason:!games?raw.slice(0,140):null}));
 }catch(err){console.log(JSON.stringify({label,error:String(err).slice(0,240)}));}
}
try {
 const res=await fetch("https://matchday-pandascore-feed.kungsman3356.workers.dev/health",{signal:AbortSignal.timeout(8000)});
 console.log("deployed-health",res.status,(await res.text()).slice(0,300));
}catch(e){console.log("deployed-health-error",String(e).slice(0,160));}
