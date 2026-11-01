// Probe official KBO scoreboard series ID with a known finished playoff match.
// Temporary diagnostic: no keys or authenticated requests.
const base="https://www.koreabaseball.com";
const ua="Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/125.0.0.0 Safari/537.36";
let cookie="";
try {
 const page=await fetch(base+"/Schedule/GameCenter/Main.aspx",{headers:{"User-Agent":ua},signal:AbortSignal.timeout(8000)});
 cookie=(page.headers.get("set-cookie")||"").split(";")[0];
 console.log("page",page.status,cookie.length);
}catch(e){console.log("page-error",String(e));}
for(const gameId of ["20261101HTSS0","20261030SSHT0"]){
 for (const srId of [5,7,0]) {
  try {
   const r=await fetch(base+"/ws/Schedule.asmx/GetScoreBoardScroll",{
    method:"POST",headers:{"User-Agent":ua,"Content-Type":"application/x-www-form-urlencoded; charset=UTF-8","X-Requested-With":"XMLHttpRequest","Referer":base+"/Schedule/GameCenter/Main.aspx","Origin":base,...(cookie?{"Cookie":cookie}:{})},body:new URLSearchParams({leId:"1",srId:String(srId),seasonId:"2026",gameId}),signal:AbortSignal.timeout(7000)
   });
   const raw=await r.text();let j={};try{j=JSON.parse(raw);}catch{}
   console.log(JSON.stringify({gameId,srId,http:r.status,found:j.G_ID,returnedSeries:j.SR_ID,home:j.B_SCORE_CN,away:j.T_SCORE_CN,maxInning:j.maxInning,start:j.START_TM,end:j.END_TM,table2Length:j.table2?.length,keys:Object.keys(j).slice(0,14),table3Length:j.table3?.length,code:j.code,msg:j.msg,rawHead:raw.slice(0,60)}));
  }catch(e){console.log(JSON.stringify({gameId,srId,error:String(e).slice(0,120)}));}
 }
}
