// Official KBO postseason schedule API diagnostics, no secrets.
const base="https://www.koreabaseball.com";
const headers={"User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36","Accept":"application/json, text/html"};
const page=await fetch(base+"/Schedule/Schedule.aspx",{headers,signal:AbortSignal.timeout(9000)});
const cookie=(page.headers.get("set-cookie")||"").split(";")[0];
console.log("KBO homepage status",page.status,"cookie",Boolean(cookie));
for(const month of ["10","11"]){
 const r=await fetch(base+"/ws/Schedule.asmx/GetScheduleList",{method:"POST",
   headers:{...headers,"Content-Type":"application/x-www-form-urlencoded; charset=UTF-8",
    Origin:base,Referer:base+"/Schedule/Schedule.aspx","X-Requested-With":"XMLHttpRequest",Cookie:cookie},
   body:new URLSearchParams({leId:"1",srIdList:"3,4,5,7",seasonId:"2026",gameMonth:month,teamId:""}),
   signal:AbortSignal.timeout(9000)});
 const data=await r.json(); console.log("KBO",month,r.status,"rows",data.rows?.length);
 let lastDay="";
 for(const record of data.rows||[]){
  const cells=record.row||[];
  const col=key=>cells.find(x=>x.Class===key)?.Text||"";
  if(col("day"))lastDay=col("day");
  const html=col("play");
  const teams=[...html.matchAll(/<span>([^<>]{1,25})<\/span>/g)].map(x=>x[1]);
  const scores=[...html.matchAll(/<span class="(win|lose|same)">([^<>]+)<\/span>/g)].map(x=>({state:x[1],value:x[2]}));
  console.log(JSON.stringify({date:lastDay,time:col("time").replace(/<[^>]+>/g,""),
    label:col("gubun"),teams,scores,html:html.slice(0,250),relay:col("relay").slice(0,150),
    stadium:col("stadium"),rowClasses:cells.map(x=>x.Class).filter(Boolean)}));
 }
}
