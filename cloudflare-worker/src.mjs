/**
 * MATCHDAY public-feed Worker v2.
 * Keep PANDASCORE_TOKEN and FEED_ACCESS_KEY in Cloudflare Secret variables.
 * KBO regular-season data: dentearl/kbo-data; postseason: KBO official schedule endpoint.
 * No fictional scores or playoff brackets are generated.
 */
const PANDA = "https://api.pandascore.co";
const KBO = "https://raw.githubusercontent.com/dentearl/kbo-data/main/data/kbo/";
const COMPETITIONS = [
  ["LCK", "LCK"], ["MSI", "Mid-Season Invitational"],
  ["EWC", "Esports World Cup"], ["WORLDS", "Worlds"], ["FST", "First Stand"]
];
const KBO_TEAMS = [
  ["KIA", "KIA 타이거즈", "KIA"], ["KT", "KT 위즈", "KT"],
  ["LG", "LG 트윈스", "LG"], ["NC", "NC 다이노스", "NC"],
  ["SSG", "SSG 랜더스", "SSG"], ["DOO", "두산 베어스", "두산"],
  ["LOT", "롯데 자이언츠", "롯데"], ["SAM", "삼성 라이온즈", "삼성"],
  ["KIW", "키움 히어로즈", "키움"], ["HAN", "한화 이글스", "한화"]
];
const DATE_OFFSET = 9 * 60 * 60 * 1000;
const CACHE_SECONDS = 300;
const jsonResponse = (value, status = 200) => new Response(JSON.stringify(value), {
  status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" }
});
const list = x => Array.isArray(x) ? x : [];
const val = x => x && typeof x === "object" && !Array.isArray(x) ? x : {};
const str = x => typeof x === "string" ? x.trim() : "";
const num = x => Number.isFinite(Number(x)) ? Number(x) : 0;
const integer = x => Math.max(0, Math.trunc(num(x)));
const safeUrl = x => str(x).startsWith("https://") ? x : "";
const isoKst = s => {
  const d = Date.parse(s);
  return Number.isFinite(d) ? new Date(d + DATE_OFFSET).toISOString().replace("Z", "+09:00") : "";
};
const yearOf = m => new Date(Date.parse(m.begin_at || m.scheduled_at || m.original_scheduled_at)).getUTCFullYear();
const isCurrent = (m, year) => yearOf(m) === year || (yearOf(m) === year + 1 && m.status !== "finished");
export function classifyLeague(league) {
  const s = (str(league?.name) + " " + str(league?.slug)).toLowerCase();
  if (/challenger|academy|junior|secondary|lck\s?cl|lck\s?as/.test(s)) return null;
  if (/\blck\b|champions korea/.test(s)) return "LCK";
  if (/\bmsi\b|mid.?season invitational/.test(s)) return "MSI";
  if (/\bewc\b|esports world cup/.test(s)) return "EWC";
  if (/\bworlds\b|world championship/.test(s)) return "WORLDS";
  if (/\bfst\b|first stand/.test(s)) return "FST";
  return null;
}
const gameCompetition = m => classifyLeague(m.league) ||
  classifyLeague(m.serie) || classifyLeague({ name: m.serie?.full_name });
const isGen = t => /(^|\W)(gen\.?g|geng)(\W|$)/i.test(
  str(t?.name) + " " + str(t?.slug) + " " + str(t?.acronym));
const teamId = t => t?.id == null ? "TBD" : isGen(t) ? "GEN" : "PS_" + t.id;
const opponent = item => val(item?.opponent || item?.team || item);
function teamInfo(t) {
  if (t?.id == null) return null;
  const name = str(t.name) || "미정";
  return { id: teamId(t), sport: "LOL", name,
    short: str(t.acronym) || name.slice(0, 10),
    mark: str(t.acronym) || name.slice(0, 3),
    logoUrl: safeUrl(t.image_url) };
}
const score = (match, id) => {
  const row = list(match.results).find(r => String(r.team_id ?? r.opponent_id) === String(id));
  return row?.score == null ? null : integer(row.score);
};
const gameStatus = status => status === "running" ? "LIVE" :
  status === "finished" ? "FINAL" :
  ["canceled", "cancelled", "postponed"].includes(status) ? "POSTPONED" : "UPCOMING";
export function normalizeMatch(m, comp) {
  const [a = {}, b = {}] = list(m.opponents).map(opponent);
  const dateTime = isoKst(m.begin_at || m.scheduled_at || m.original_scheduled_at);
  if (!dateTime || m.id == null) return null;
  const status = gameStatus(m.status);
  return { id: "PSM_" + m.id, competition: comp, season: String(yearOf(m)),
    dateTime, home: teamId(a), away: teamId(b), status,
    homeScore: status === "UPCOMING" ? null : score(m, a.id),
    awayScore: status === "UPCOMING" ? null : score(m, b.id),
    stage: str(m.tournament?.name) || str(m.name) || "경기",
    venue: [str(m.league?.name), str(m.serie?.full_name || m.serie?.name)].filter(Boolean).join(" · "),
    seriesId: "" };
}
function unpackStanding(node, depth = 0) {
  if (depth > 7 || !node) return [];
  if (Array.isArray(node)) return node.flatMap(x => unpackStanding(x, depth + 1));
  if (typeof node !== "object") return [];
  if ((node.rank ?? node.position ?? node.placement) != null &&
    (node.team || node.opponent || node.team_id)) return [node];
  return ["rankings", "standings", "groups", "entries", "teams", "results"]
    .flatMap(k => unpackStanding(node[k], depth + 1));
}
export function normalizeStandings(raw, comp, season) {
  return unpackStanding(raw).map(x => {
    const t = val(x.team || x.opponent);
    const tid = t.id ?? x.team_id;
    if (tid == null) return null;
    const r = val(x.total || x.record);
    const won = integer(x.wins ?? x.matches_won ?? r.wins);
    const lost = integer(x.losses ?? x.matches_lost ?? r.losses);
    return { competition: comp, season, rank: integer(x.rank ?? x.position ?? x.placement),
      team: teamId({ ...t, id: tid }), played: won + lost, won, lost, drawn: integer(x.draws),
      forScore: integer(x.games_won ?? x.games_for), againstScore: integer(x.games_lost ?? x.games_against), streak: "" };
  }).filter(r => r && r.rank > 0);
}
function standingsTeams(raw, output) {
  const visit = (node, depth = 0) => {
    if (depth > 8 || !node) return;
    if (Array.isArray(node)) { node.forEach(n => visit(n, depth + 1)); return; }
    if (typeof node !== "object") return;
    if (node.team && typeof node.team === "object") visit(node.team, depth + 1);
    if (node.opponent && typeof node.opponent === "object") visit(node.opponent, depth + 1);
    const t = teamInfo(node);
    if (t && node.name) output.set(t.id, t);
    for (const k of ["rankings", "standings", "groups", "entries", "teams", "results"])
      if (node[k]) visit(node[k], depth + 1);
  };
  visit(raw);
}
function bracketMatches(raw) { return list(raw).length ? raw : list(raw?.matches || raw?.brackets); }
export function normalizeBracket(raw, comp) {
  const matches = bracketMatches(raw).filter(x => x && x.id != null);
  const lookup = new Map(matches.map(m => [String(m.id), m]));
  const depths = new Map();
  const depth = (m, seen = new Set()) => {
    if (depths.has(String(m.id))) return depths.get(String(m.id));
    if (seen.has(String(m.id))) return 0;
    const used = new Set(seen); used.add(String(m.id));
    const parents = list(m.previous_matches).map(p => lookup.get(String(p.match_id))).filter(Boolean);
    const result = parents.length ? Math.max(...parents.map(p => depth(p, used))) + 1 : 0;
    depths.set(String(m.id), result);
    return result;
  };
  const double = matches.some(m => list(m.previous_matches).some(p => /loser|loss/i.test(str(p.type))));
  const groups = m => list(m.previous_matches).some(p => /loser|loss/i.test(str(p.type))) ? "LOWER" :
    /grand.?final|finals?$/i.test(str(m.name)) ? "MAIN" : (double ? "UPPER" : "MAIN");
  return matches.map(m => {
    const [a = {}, b = {}] = list(m.opponents).map(opponent);
    const group = groups(m);
    const next = matches.find(n => list(n.previous_matches).some(p =>
      String(p.match_id) === String(m.id) && String(p.type).toLowerCase() === "winner"));
    return { id: "PSB_" + m.id, competition: comp, stage: str(m.round || m.name) || "토너먼트",
      group, roundIndex: depth(m), left: teamId(a), right: teamId(b),
      leftWins: score(m, a.id) ?? 0, rightWins: score(m, b.id) ?? 0,
      status: gameStatus(m.status), scheduled: isoKst(m.begin_at || m.scheduled_at) || "",
      bestOf: Math.max(1, integer(m.number_of_games) || 5),
      nextId: next ? "PSB_" + next.id : "" };
  });
}
function tournamentCandidates(matches) {
  const map = new Map();
  for (const m of matches) {
    const t = m.tournament;
    if (t?.id == null) continue;
    const date = Date.parse(m.begin_at || m.scheduled_at) || 0;
    if (!map.has(t.id) || map.get(t.id).date < date)
      map.set(t.id, { id: t.id, name: str(t.name), date, bracket: !!t.has_bracket });
  }
  return [...map.values()].sort((a, b) => b.date - a.date);
}
async function panda(path, env, fetcher) {
  const r = await fetcher(PANDA + path, {
    headers: { Authorization: "Bearer " + env.PANDASCORE_TOKEN, Accept: "application/json" }
  });
  if (!r.ok) throw new Error("PandaScore HTTP " + r.status + " at " + path.split("?")[0]);
  return r.json();
}
async function optional(path, env, fetcher, warnings) {
  try { return await panda(path, env, fetcher); }
  catch (e) { warnings.push(str(e.message).slice(0, 120)); return null; }
}
function dedup(matches) { return [...new Map(matches.filter(x => x?.id != null).map(m => [m.id, m])).values()]; }
async function fetchLoL(env, fetcher, year, warnings) {
  const byComp = new Map(COMPETITIONS.map(([id]) => [id, []]));
  const [upcoming, running, past] = await Promise.all([
    optional("/lol/matches/upcoming?sort=begin_at&per_page=100", env, fetcher, warnings),
    optional("/lol/matches/running?per_page=100", env, fetcher, warnings),
    optional("/lol/matches/past?sort=-begin_at&per_page=100", env, fetcher, warnings)
  ]);
  for (const m of [...list(past), ...list(running), ...list(upcoming)]) {
    const c = gameCompetition(m);
    if (c && isCurrent(m, year)) byComp.get(c).push(m);
  }
  // Per-league requests repair missing LCK/MSI/EWC results that fall outside
  // the 100 most recent matches from ALL LoL leagues combined.
  const leagues = await Promise.all(COMPETITIONS.map(async ([comp, term]) => {
    const found = await optional("/lol/leagues?search[name]=" + encodeURIComponent(term) +
      "&per_page=100", env, fetcher, warnings);
    return { comp, league: list(found).find(l => classifyLeague(l) === comp) };
  }));
  await Promise.all(leagues.filter(x => x.league?.id != null).map(async ({ comp, league }) => {
    const pages = comp === "LCK" ? [1, 2] : [1];
    const results = await Promise.all(pages.map(page => optional(
      "/leagues/" + encodeURIComponent(league.id) +
      "/matches?sort=-begin_at&per_page=100&page=" + page, env, fetcher, warnings)));
    for (const m of results.flatMap(list)) if (isCurrent(m, year)) byComp.get(comp).push(m);
  }));
  const teams = new Map([["GEN", {
    id: "GEN", sport: "LOL", name: "Gen.G", short: "GEN", mark: "GEN", logoUrl: ""
  }]]);
  const games = [];
  const standings = [];
  const series = [];
  for (const [comp, raw] of byComp) {
    const items = dedup(raw);
    for (const m of items) {
      list(m.opponents).forEach(entry => {
        const t = teamInfo(opponent(entry));
        if (t) teams.set(t.id, t);
      });
      const normalized = normalizeMatch(m, comp);
      if (normalized) games.push(normalized);
    }
    const candidates = tournamentCandidates(items);
    const regular = candidates.filter(t => /regular|group|season|round|stage|swiss/i.test(t.name));
    const playoff = candidates.filter(t => /playoff|knockout|bracket|final|quarter|elimination/i.test(t.name) || t.bracket);
    // Keep within free Workers subrequest budgets and PandaScore 60 req/min burst limit.
    const maxChecks = comp === "LCK" ? 3 : 2;
    const checkedStandings = new Set();
    for (const t of [...regular, ...candidates].slice(0, maxChecks)) {
      if (checkedStandings.has(t.id)) continue;
      checkedStandings.add(t.id);
      const data = await optional("/tournaments/" + t.id + "/standings?per_page=100", env, fetcher, warnings);
      const rows = normalizeStandings(data, comp, String(year));
      standingsTeams(data, teams);
      if (rows.length > 0) { standings.push(...rows); break; }
    }
    const checkedBrackets = new Set();
    for (const t of [...playoff, ...candidates].slice(0, maxChecks)) {
      if (checkedBrackets.has(t.id)) continue;
      checkedBrackets.add(t.id);
      const data = await optional("/tournaments/" + t.id + "/brackets?per_page=100", env, fetcher, warnings);
      const rows = normalizeBracket(data, comp);
      if (rows.length > 0) {
        series.push(...rows);
        for (const s of rows) {
          const match = games.find(g => g.id === "PSM_" + s.id.slice(4));
          if (match) match.seriesId = s.id;
        }
        // Some bracket participants were not in the global match list.
        for (const m of bracketMatches(data)) list(m.opponents).forEach(e => {
          const t = teamInfo(opponent(e));
          if (t) teams.set(t.id, t);
        });
        break;
      }
    }
  }
  // Reference rows of genuinely completed regular stage matches only, if
  // a tournament standings endpoint is absent. No fake official ranks.
  for (const [comp, raw] of byComp) {
    if (standings.some(x => x.competition === comp)) continue;
    const finished = dedup(raw).filter(m => m.status === "finished" &&
      /regular|group|swiss|season/i.test(str(m.tournament?.name)));
    if (!finished.length) continue;
    const totals = new Map();
    for (const m of finished) {
      const o = list(m.opponents).map(opponent);
      if (o.length !== 2 || o.some(t => t.id == null)) continue;
      const a = score(m, o[0].id), b = score(m, o[1].id);
      if (a == null || b == null || a === b) continue;
      o.forEach((t, i) => {
        const id = teamId(t);
        const v = totals.get(id) || { won: 0, lost: 0, forScore: 0, againstScore: 0 };
        if ((i === 0 ? a : b) > (i === 0 ? b : a)) v.won++;
        else v.lost++;
        v.forScore += i === 0 ? a : b;
        v.againstScore += i === 0 ? b : a;
        totals.set(id, v);
      });
    }
    const ranking = [...totals].sort((a, b) =>
      (b[1].won / Math.max(1, b[1].won + b[1].lost)) -
      (a[1].won / Math.max(1, a[1].won + a[1].lost)) ||
      b[1].forScore - b[1].againstScore - (a[1].forScore - a[1].againstScore));
    ranking.forEach(([team, v], i) => standings.push({
      competition: comp, season: String(year), rank: i + 1, team,
      played: v.won + v.lost, won: v.won, lost: v.lost, drawn: 0,
      forScore: v.forScore, againstScore: v.againstScore, streak: "경기 기록 기준"
    }));
  }
  return { teams, games: dedup(games), standings, series };
}

/** Supplemental KBO postseason from Naver's undocumented schedule gateway.
 * Source is unofficial and can disappear without notice. Never invent fixtures. */
const KBO_OFFICIAL = "https://www.koreabaseball.com";
const NAVER_KBO_CODES = {HT:"KIA",KIA:"KIA",KT:"KT",LG:"LG",NC:"NC",
  SK:"SSG",SSG:"SSG",OB:"DOO",DOO:"DOO",LT:"LOT",LOT:"LOT",
  SS:"SAM",SAM:"SAM",WO:"KIW",KIW:"KIW",HH:"HAN",HAN:"HAN"};
function kboTeamFromNaver(code, name) {
  const key = str(code).toUpperCase();
  if (NAVER_KBO_CODES[key]) return NAVER_KBO_CODES[key];
  if (/PO\s*승리팀|플레이오프\s*승리팀/i.test(str(name))) return "TBD";
  const n = str(name).replace(/\s/g,"").toLowerCase();
  for (const [id, full, short] of KBO_TEAMS)
    if (n === full.replace(/\s/g,"").toLowerCase() ||
      n === short.replace(/\s/g,"").toLowerCase()) return id;
  return null;
}
function kboNaverTime(value) {
  let s = str(value).replace(" ", "T");
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(s)) s += ":00";
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(s)) s += "+09:00";
  if (!Number.isFinite(Date.parse(s))) return null;
  return s.endsWith("Z") ? isoKst(s) : s;
}
export function convertKboPostseason(raw, regularStandings, year) {
  // Postseason seeds are known for 2026. Prefer the official playoff seeds
  // over potentially incomplete cached regular-season standings.
  const ranks = year === 2026
    ? new Map([["KT", 1], ["SAM", 2], ["KIA", 3], ["LG", 4], ["DOO", 5]])
    : new Map(regularStandings.map(s => [s.team, s.rank]));
  const known = list(raw).filter(m=>m?.categoryId==="kbo");
  const games = known.map(m=>{
    const home=kboTeamFromNaver(m.homeTeamCode,m.homeTeamName);
    const away=kboTeamFromNaver(m.awayTeamCode,m.awayTeamName);
    const dateTime=kboNaverTime(m.gameDateTime);
    if (!home || !away || !dateTime || m.gameId == null) return null;
    const code = str(m.statusCode).toUpperCase();
    const status = code==="RESULT"?"FINAL":code==="STARTED"?"LIVE":
      code==="CANCEL"?"POSTPONED":code==="SUSPENDED"?"POSTPONED":
      code==="UNCERTAIN"?"UNKNOWN":"UPCOMING";
    const homeScore = status==="UPCOMING" || status==="UNKNOWN" ? null :
      m.homeTeamScore == null ? null : num(m.homeTeamScore);
    const awayScore = status==="UPCOMING" || status==="UNKNOWN" ? null :
      m.awayTeamScore == null ? null : num(m.awayTeamScore);
    const bestRank = Math.min(ranks.get(home)??99,ranks.get(away)??99);
    const roundIndex = bestRank===1?3:bestRank===2?2:bestRank===3?1:0;
    const stage = ["와일드카드","준플레이오프","플레이오프","한국시리즈"][roundIndex];
    const sortedTeams=[home,away].sort((a,b)=>(ranks.get(a)??99)-(ranks.get(b)??99));
    const seriesId="KBOPS_"+year+"_"+roundIndex+"_"+sortedTeams.join("_");
    return { id:"NPS_"+m.gameId,competition:"KBO",season:String(year),
      dateTime,home,away,status,homeScore,awayScore,stage,
      venue:str(m.stadium),seriesId };
  }).filter(Boolean);
  const map = new Map();
  for (const game of games) {
    let entry=map.get(game.seriesId);
    if (!entry) {
      entry={ id:game.seriesId,competition:"KBO",stage:game.stage,
        group:"MAIN",roundIndex:["와일드카드","준플레이오프","플레이오프","한국시리즈"].indexOf(game.stage),
        left: game.home, right:game.away, leftWins:0,rightWins:0,
        status:"UPCOMING", scheduled:game.dateTime,
        bestOf:game.stage==="한국시리즈"?7:game.stage==="와일드카드"?2:5,nextId:"" };
      if ((ranks.get(entry.left)??99)>(ranks.get(entry.right)??99))
        [entry.left,entry.right]=[entry.right,entry.left];
      map.set(game.seriesId,entry);
    }
    if(game.status==="FINAL" && game.homeScore!=null && game.awayScore!=null) {
      const winner=game.homeScore>game.awayScore?game.home:game.awayScore>game.homeScore?game.away:null;
      if(winner===entry.left) entry.leftWins++;
      if(winner===entry.right) entry.rightWins++;
    }
  }
  const series=[...map.values()].sort((a,b)=>a.roundIndex-b.roundIndex);
  for(const entry of series) {
    const matches=games.filter(g=>g.seriesId===entry.id).sort((a,b)=>a.dateTime.localeCompare(b.dateTime));
    const open=matches.find(g=>g.status==="LIVE"||g.status==="UPCOMING"||g.status==="UNKNOWN");
    entry.scheduled=open?.dateTime||matches[0]?.dateTime||"";
    // In KBO wildcard, the 4th seed advances with one win (or a tie);
    // the 5th seed must win two games. Other series are best-of-five/seven.
    const finished = entry.stage==="와일드카드"
      ? entry.leftWins>=1 || entry.rightWins>=2 || matches.some(g=>
          g.status==="FINAL" && g.homeScore!=null && g.homeScore===g.awayScore)
      : entry.leftWins>=(entry.stage==="한국시리즈"?4:3) ||
        entry.rightWins>=(entry.stage==="한국시리즈"?4:3);
    entry.status=finished?"FINAL":
      matches.some(g=>g.status==="LIVE")?"LIVE":
      matches.some(g=>g.status==="UNKNOWN")?"UNKNOWN":"UPCOMING";
    entry.nextId=series.find(s=>s.roundIndex===entry.roundIndex+1)?.id||"";
  }
  return {games,series};
}

/** Official KBO schedule API returns HTML-like rows wrapped in JSON.
 * A pregame 0:0 marked with 'same' is NOT a completed draw; 'REVIEW' links
 * are the signal for a finalized game. */
export function parseOfficialKboSchedule(raw, year, month, now = Date.now()) {
  let lastDay = "";
  const result = [];
  const teamText = html => str(html).replace(/<br\s*\/?>/gi, "").replace(/<[^>]*>/g, "").trim();
  for (const record of list(raw?.rows)) {
    const cells = list(record?.row);
    const col = cls => str(cells.find(c => c?.Class === cls)?.Text);
    const dayText = col("day");
    if (dayText) lastDay = dayText;
    const day = /^(\d{1,2})\.(\d{1,2})/.exec(lastDay);
    const time = teamText(col("time"));
    const html = col("play");
    if (!day || !/^\d{1,2}:\d{2}$/.test(time) || !html) continue;
    const awayLabel = teamText(html.match(/^<span>([\s\S]*?)<\/span>/i)?.[1]);
    const homeLabel = teamText(html.match(/<\/em>\s*<span>([\s\S]*?)<\/span>/i)?.[1]);
    const away = kboTeamFromNaver("", awayLabel);
    const home = kboTeamFromNaver("", homeLabel);
    if (!home || !away) continue;
    const mm = String(month).padStart(2, "0");
    const dd = day[2].padStart(2, "0");
    const dateTime = year + "-" + mm + "-" + dd + "T" + time + ":00+09:00";
    const relay = col("relay");
    const review = /section=REVIEW|btnReview/i.test(relay);
    const running = !review && /section=RELAY|btnRelay/i.test(relay);
    const scoreRows = [...html.matchAll(/<span class="(win|lose|same)">(\d+)<\/span>/gi)];
    const completed = review && scoreRows.length === 2;
    // Empty relay with "same" 0:0 means scheduled or score not available.
    const uncertain = !completed && !running && now >= Date.parse(dateTime);
    const statusCode = completed ? "RESULT" : running ? "STARTED" :
      uncertain ? "UNCERTAIN" : "BEFORE";
    const gameId = relay.match(/gameId=([A-Za-z0-9]+)/i)?.[1] ||
      year + mm + dd + "_" + away + "_" + home;
    result.push({
      gameId, categoryId: "kbo", gameDateTime: dateTime,
      homeTeamCode: home, awayTeamCode: away,
      homeTeamName: homeLabel, awayTeamName: awayLabel,
      homeTeamScore: completed || running ? Number(scoreRows[1]?.[2] || 0) : null,
      awayTeamScore: completed || running ? Number(scoreRows[0]?.[2] || 0) : null,
      statusCode, stadium: ""
    });
  }
  return result;
}

/** A last-verified October 31 snapshot, not live data.
 * Used only if the official schedule endpoint is unreachable. */
function confirmed2026Snapshot(now = Date.now()) {
  const final = (date, time, away, home, awayScore, homeScore) => ({
    gameId: "CONF_" + date.replaceAll("-", "") + "_" + away + "_" + home,
    categoryId: "kbo", gameDateTime: date + "T" + time + ":00+09:00",
    homeTeamCode: home, awayTeamCode: away, homeTeamName: home,
    awayTeamName: away, homeTeamScore: homeScore, awayTeamScore: awayScore,
    statusCode: "RESULT", stadium: ""
  });
  const fixture = (date, time, away, home) => {
    const dt = date + "T" + time + ":00+09:00";
    return {
      gameId: "CONF_" + date.replaceAll("-", "") + "_" + away + "_" + home,
      categoryId: "kbo", gameDateTime: dt,
      homeTeamCode: home, awayTeamCode: away, homeTeamName: home === "TBD" ? "PO승리팀" : home,
      awayTeamName: away === "TBD" ? "PO승리팀" : away,
      homeTeamScore: null, awayTeamScore: null,
      statusCode: now >= Date.parse(dt) ? "UNCERTAIN" : "BEFORE", stadium: ""
    };
  };
  return [
    final("2026-10-14","18:30","DOO","LG",1,4),
    final("2026-10-17","14:00","LG","KIA",1,5),
    final("2026-10-18","14:00","LG","KIA",3,6),
    final("2026-10-20","18:30","KIA","LG",13,1),
    final("2026-10-26","18:30","KIA","SAM",5,1),
    final("2026-10-27","18:30","KIA","SAM",3,8),
    final("2026-10-29","18:30","SAM","KIA",6,4),
    final("2026-10-30","18:30","SAM","KIA",2,4),
    fixture("2026-11-01","14:00","KIA","SAM"),
    fixture("2026-11-03","18:30","TBD","KT"),
    fixture("2026-11-04","18:30","TBD","KT"),
    fixture("2026-11-06","18:30","KT","TBD"),
    fixture("2026-11-07","14:00","KT","TBD"),
    fixture("2026-11-08","14:00","KT","TBD"),
    fixture("2026-11-10","18:30","TBD","KT"),
    fixture("2026-11-11","18:30","TBD","KT")
  ];
}
async function fetchKboPostseason(fetcher, year, regularStandings, warnings) {
  if (year < 2026) return { games: [], series: [], source: "not requested" };
  const headers = {
    "Accept": "application/json, text/html",
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0 Safari/537.36"
  };
  let cookie = "";
  let months = [];
  try {
    const page = await fetcher(KBO_OFFICIAL + "/Schedule/Schedule.aspx", {
      headers, signal: AbortSignal.timeout(6000)
    });
    if (!page.ok) throw new Error("KBO official homepage HTTP " + page.status);
    cookie = str(page.headers.get("set-cookie")).split(";")[0];
  } catch (err) {
    warnings.push("KBO official homepage: " + str(err.message).slice(0, 95));
  }
  await Promise.all(["10", "11"].map(async month => {
    try {
      const r = await fetcher(KBO_OFFICIAL + "/ws/Schedule.asmx/GetScheduleList", {
        method: "POST", signal: AbortSignal.timeout(6000),
        headers: {
          ...headers, "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
          "Referer": KBO_OFFICIAL + "/Schedule/Schedule.aspx",
          "Origin": KBO_OFFICIAL, "X-Requested-With": "XMLHttpRequest",
          ...(cookie ? { Cookie: cookie } : {})
        },
        body: new URLSearchParams({
          leId: "1", srIdList: "3,4,5,7", seasonId: String(year), gameMonth: month, teamId: ""
        })
      });
      if (!r.ok) throw new Error("KBO official HTTP " + r.status);
      const data = await r.json();
      if (!Array.isArray(data?.rows)) throw new Error("KBO unexpected JSON format");
      months.push(...parseOfficialKboSchedule(data, year, month));
    } catch (err) {
      warnings.push("KBO official " + month + " month: " + str(err.message).slice(0, 95));
    }
  }));
  if (!months.length && year === 2026) {
    warnings.push("KBO postseason: 2026-10-31 confirmed snapshot; current results unverified");
    return { ...convertKboPostseason(confirmed2026Snapshot(), regularStandings, year),
      source: "confirmed snapshot as of 2026-10-31, NOT live" };
  }
  return { ...convertKboPostseason(months, regularStandings, year),
    source: "KBO official monthly postseason schedule" };
}

export function convertKbo(raw, year) {
  const teams = KBO_TEAMS.map(([id, name, short]) =>
    ({ id, sport: "KBO", name, short, mark: id, logoUrl: "" }));
  const allowed = new Set(teams.map(t => t.id));
  const games = list(raw).map(m => {
    const a = m?.teams?.away;
    const h = m?.teams?.home;
    const away = str(a?.team?.abbrev).toUpperCase(), home = str(h?.team?.abbrev).toUpperCase();
    if (!allowed.has(home) || !allowed.has(away) || !m.gamePk || !m.gameDate) return null;
    const f = m.status?.abstractGameState === "F";
    const detailed = str(m.status?.detailedState).toLowerCase();
    const running = m.status?.abstractGameState === "I" || /progress|live|inning/.test(detailed);
    const cancelled = /cancel|postpon|rain/.test(detailed);
    return { id: "KBO_" + m.gamePk, competition: "KBO", season: String(year),
      dateTime: m.gameDate, home, away,
      status: f ? "FINAL" : running ? "LIVE" : cancelled ? "POSTPONED" : "UPCOMING",
      homeScore: h.score ?? null, awayScore: a.score ?? null,
      stage: m.isPostseason ? "포스트시즌" : "정규시즌", venue: "", seriesId: "" };
  }).filter(Boolean);
  const stats = new Map(KBO_TEAMS.map(([id]) => [id, {won:0, lost:0, drawn:0}]));
  for (const g of games) {
    if (g.status !== "FINAL" || g.stage !== "정규시즌" ||
      g.homeScore == null || g.awayScore == null) continue;
    const home = stats.get(g.home), away = stats.get(g.away);
    if (g.homeScore === g.awayScore) { home.drawn++; away.drawn++; }
    else if (g.homeScore > g.awayScore) { home.won++; away.lost++; }
    else { away.won++; home.lost++; }
  }
  const ordered = [...stats].sort((a, b) =>
    b[1].won / Math.max(1, b[1].won + b[1].lost) -
    a[1].won / Math.max(1, a[1].won + a[1].lost) ||
    b[1].won - a[1].won);
  const standings = ordered.map(([team, s], i) => ({
    competition: "KBO", season: String(year), rank: i + 1, team,
    played: s.won + s.lost + s.drawn,
    won: s.won, lost: s.lost, drawn: s.drawn,
    forScore: 0, againstScore: 0, streak: "정규시즌 경기 결과 기준"
  }));
  return { teams, games, standings, series: [] };
}
export async function buildFeed(env, fetcher = fetch, year = new Date().getUTCFullYear()) {
  const warnings = [];
  const lol = await fetchLoL(env, fetcher, year, warnings);
  let kbo = { teams: KBO_TEAMS.map(([id, name, short]) =>
    ({id, sport:"KBO", name, short, mark:id, logoUrl:""})), games: [], standings: [], series: [] };
  try {
    const resp = await fetcher(KBO + year + ".json", { headers: { Accept: "application/json" } });
    if (!resp.ok) throw new Error("KBO upstream HTTP " + resp.status);
    kbo = convertKbo(await resp.json(), year);
  } catch (e) { warnings.push("KBO: " + str(e.message).slice(0, 120)); }
  const postseason=await fetchKboPostseason(fetcher,year,kbo.standings,warnings);
  kbo.games.push(...postseason.games);
  kbo.series.push(...postseason.series);
  const teamMap = new Map([...kbo.teams, ...lol.teams.values()].map(t => [t.id, t]));
  const games = dedup([...kbo.games, ...lol.games]).sort((a, b) =>
    a.dateTime.localeCompare(b.dateTime));
  if (!games.length) throw new Error("No real fixtures available from either source");
  const stamp = isoKst(new Date().toISOString());
  const standings = [...kbo.standings, ...lol.standings].filter(x => teamMap.has(x.team));
  const series = [...kbo.series, ...lol.series].filter(s => s.left === "TBD" || teamMap.has(s.left))
    .filter(s => s.right === "TBD" || teamMap.has(s.right));
  return { schemaVersion: 1, updatedAt: stamp, isDemo: false, demoDay: stamp.slice(0, 10),
    teams: [...teamMap.values()], games, standings, series,
    sources: {
      KBO: "GitHub regular-season data + " + postseason.source,
      LOL: "PandaScore", note: "LoL fallback rankings may be computed from available regular-season match results"
    },
    diagnostics: {
      counts: { KBO: kbo.games.length, KBOPostseason: postseason.games.length, LOL: lol.games.length,
        standings: standings.length, series: series.length },
      warnings: [...new Set(warnings)].slice(0, 20)
    } };
}
export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (url.pathname === "/health")
      return jsonResponse({ service:"matchday", ok:true, version:3,
        keyConfigured: !!(env.PANDASCORE_TOKEN && env.FEED_ACCESS_KEY) });
    if (url.pathname !== "/feed.json" && url.pathname !== "/status")
      return jsonResponse({ error:"Not found" }, 404);
    if (request.method !== "GET") return jsonResponse({error:"GET only"},405);
    if (!env.PANDASCORE_TOKEN || !env.FEED_ACCESS_KEY)
      return jsonResponse({error:"Secrets missing"},503);
    if (url.searchParams.get("key") !== env.FEED_ACCESS_KEY)
      return jsonResponse({error:"Forbidden"},403);
    const cache = caches.default;
    const cacheKey = new Request(url.origin + "/__matchday_feed_v3", {method:"GET"});
    let feed;
    const stored = await cache.match(cacheKey);
    if (stored) feed = await stored.json();
    else {
      try {
        feed = await buildFeed(env);
      } catch (e) {
        console.error("MATCHDAY fetch error: " + str(e.message));
        return jsonResponse({error:"Upstream data unavailable"},502);
      }
      if (ctx?.waitUntil) ctx.waitUntil(cache.put(cacheKey, new Response(JSON.stringify(feed), {
        headers: {"content-type":"application/json","cache-control":"public, max-age=" + CACHE_SECONDS}
      })));
    }
    if (url.pathname === "/status")
      return jsonResponse({ updatedAt:feed.updatedAt, sources:feed.sources, diagnostics:feed.diagnostics });
    return jsonResponse(feed);
  }
};
