// Public endpoint availability probe. No credentials or private URLs.
const sources=[
  ["tving","https://www.tving.com/sports/kbo"],
  ["yagoonara","https://www.yagoonara.com/postseason/schedule"],
  ["mykbo","https://mykbostats.com/"],
  ["naver-daily","https://api-gw.sports.naver.com/schedule/games?upperCategoryId=kbaseball&fromDate=2026-11-01&toDate=2026-11-01"]
];
await Promise.all(sources.map(async ([source,url])=>{
  try {
    const response=await fetch(url,{headers:{Accept:"text/html,application/json","User-Agent":"Mozilla/5.0 (compatible; MatchdayMonitor/1.0)"},signal:AbortSignal.timeout(7000)});
    const body=await response.text();
    console.log(JSON.stringify({source,status:response.status,length:body.length,contentType:response.headers.get("content-type"),hasSamsung:/삼성|Samsung/.test(body),hasPlayoff:/플레이오프|Playoff|postseason/.test(body),hasNov1:/11월 1일|11\.01|2026-11-01/.test(body),sample:body.slice(0,80)}));
  }catch(e){console.log(JSON.stringify({source,error:String(e)}));}
}));
const health=await fetch("https://matchday-pandascore-feed.kungsman3356.workers.dev/health");
console.log("deployed-health",health.status,(await health.text()).slice(0,200));
