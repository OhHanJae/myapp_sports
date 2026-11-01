// Public Naver and official KBO schedule probe; credentials are never involved.
const base="https://www.koreabaseball.com";
const headers={"User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36","Accept":"application/json, text/html"};
let cookie="";
try {
 const r=await fetch(base+"/Schedule/Schedule.aspx",{headers,signal:AbortSignal.timeout(8000)});
 console.log("kbo-page",r.status,r.headers.get("content-type"),r.headers.get("set-cookie")?.length||0);
 cookie=(r.headers.get("set-cookie")||"").split(";")[0];
}catch(e){console.log("kbo-page-error",String(e).slice(0,180));}
for (const month of ["10","11"]) {
 try {
  const body=new URLSearchParams({leId:"1",srIdList:"3,4,5,7",seasonId:"2026",gameMonth:month,teamId:""});
  const r=await fetch(base+"/ws/Schedule.asmx/GetScheduleList",{
   method:"POST",body,headers:{...headers,"Content-Type":"application/x-www-form-urlencoded; charset=UTF-8","Referer":base+"/Schedule/Schedule.aspx",Origin:base,"X-Requested-With":"XMLHttpRequest",Cookie:cookie},signal:AbortSignal.timeout(8000)});
  const text=await r.text();
  let parsed;try{parsed=JSON.parse(text)}catch{parsed={}};
  console.log("kbo-post",month,r.status,text.length,"rows",parsed.rows?.length??null,"first",JSON.stringify(parsed.rows?.[0]||{}).slice(0,320),"head",text.slice(0,100));
 }catch(e){console.log("kbo-post-error",month,String(e).slice(0,180));}
}
try {
 const r=await fetch("https://matchday-pandascore-feed.kungsman3356.workers.dev/health",{signal:AbortSignal.timeout(6000)});
 console.log("worker-health",r.status,(await r.text()).slice(0,140));
}catch(e){console.log("worker-error",String(e).slice(0,180));}
