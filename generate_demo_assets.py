#!/usr/bin/env python3
"""Deterministic *fictional* fixture feed and non-official team badge PNGs for offline debug."""
import json, shutil, pathlib, math
from PIL import Image, ImageDraw, ImageFont, ImageColor
root = pathlib.Path(__file__).resolve().parent
team_defs = [
('SAM','KBO','삼성 라이온즈','삼성','SL','#1A76D2'),('LG','KBO','LG 트윈스','LG','LG','#B9203A'),
('HAN','KBO','한화 이글스','한화','HE','#DB6B18'),('KT','KBO','KT 위즈','KT','KT','#333A48'),
('SSG','KBO','SSG 랜더스','SSG','SSG','#CE2141'),('LOT','KBO','롯데 자이언츠','롯데','LT','#C32B3B'),
('KIA','KBO','KIA 타이거즈','KIA','KIA','#BD1E2E'),('DOO','KBO','두산 베어스','두산','DB','#15336A'),
('NC','KBO','NC 다이노스','NC','NC','#174781'),('KIW','KBO','키움 히어로즈','키움','KH','#9B183A'),
('GEN','LOL','Gen.G','GEN','GEN','#CFA340'),('T1','LOL','T1','T1','T1','#E63D4A'),
('HLE','LOL','Hanwha Life Esports','HLE','HLE','#E58A2D'),('DK','LOL','Dplus KIA','DK','DK','#E2E2E2'),
('KTLOL','LOL','KT Rolster','KT','KT','#E83950'),('BFX','LOL','BNK FearX','BFX','BFX','#F2D542'),
('DNS','LOL','DN Freecs','DN','DN','#416EEB'),('NS','LOL','Nongshim RedForce','NS','NS','#D62F35'),
('BRO','LOL','OKSavingsBank BRION','BRO','BRO','#46B878'),('DRX','LOL','DRX','DRX','DRX','#3C68DC'),
('G2','LOL','G2 Esports','G2','G2','#B7BEC8'),('BLG','LOL','Bilibili Gaming','BLG','BLG','#F29E42'),
('FNC','LOL','Fnatic','FNC','FNC','#F28434'),('C9','LOL','Cloud9','C9','C9','#69B7EC'),
('FLY','LOL','FlyQuest','FLY','FLY','#49AC81'),('TES','LOL','Top Esports','TES','TES','#D94E4E')
]
teams = [dict(id=id,sport=sport,name=name,short=short,mark=mark,logoUrl='') for id,sport,name,short,mark,color in team_defs]
# All content in this fixture is deliberately fabricated; it must never be displayed as live sport results.
games=[]; series=[]; standings=[]
def game(id,comp,dt,home,away,status='UPCOMING',score=None,stage='',venue='',sid='',season='2026'):
    games.append(dict(id=id,competition=comp,season=season,dateTime=dt,home=home,away=away,status=status,homeScore=score[0] if score else None,awayScore=score[1] if score else None,stage=stage,venue=venue,seriesId=sid))
def bracket(id,comp,stage,group,round,left,right,wins=(0,0),status='UPCOMING',day='2026-10-30T18:30:00+09:00',bestOf=5,nxt=''):
    series.append(dict(id=id,competition=comp,stage=stage,group=group,roundIndex=round,left=left,right=right,leftWins=wins[0],rightWins=wins[1],status=status,scheduled=day,bestOf=bestOf,nextId=nxt))
def rank(comp,ids,wl,draws=None):
    draws=draws or [0]*len(ids)
    for i,(id,(w,l),d) in enumerate(zip(ids,wl,draws),1):
        standings.append(dict(competition=comp,season='2026',rank=i,team=id,played=w+l+d,won=w,lost=l,drawn=d,forScore=w*2+(i%3),againstScore=l*2+(i%4),streak=('3연승' if i%4==1 else '2승3패' if i%3==0 else '1연패')))
rank('KBO',['LG','SAM','HAN','KIA','KT','SSG','LOT','NC','DOO','KIW'],[(82,60),(80,62),(77,65),(75,67),(73,69),(70,72),(67,75),(63,79),(60,82),(54,88)],[2]*10)
rank('LCK',['GEN','T1','HLE','DK','KTLOL','BFX','DNS','NS','BRO','DRX'],[(18,2),(16,4),(15,5),(13,7),(11,9),(9,11),(7,13),(6,14),(3,17),(2,18)])
rank('MSI',['GEN','BLG','G2','T1'],[(8,1),(7,2),(5,4),(4,5)])
rank('EWC',['GEN','T1','FNC','G2'],[(5,0),(4,1),(3,2),(2,3)])
rank('WORLDS',['GEN','BLG','T1','G2','HLE','FNC','FLY','C9'],[(4,0),(3,1),(3,1),(3,2),(2,2),(2,3),(1,3),(0,4)])
for x in [
 ('kbo-1','2026-10-30T18:30:00+09:00','SAM','LG','UPCOMING',None,'플레이오프','대구','kbo-po'),
 ('kbo-2','2026-10-30T18:30:00+09:00','KIA','HAN','UPCOMING',None,'포스트시즌','광주',''),
 ('kbo-3','2026-10-30T14:00:00+09:00','KT','NC','LIVE',(3,2),'와일드카드','수원','kbo-wc'),
 ('kbo-4','2026-10-29T18:30:00+09:00','LG','SAM','FINAL',(2,5),'플레이오프','잠실','kbo-po'),
 ('kbo-5','2026-10-31T14:00:00+09:00','SAM','LG','UPCOMING',None,'플레이오프','대구','kbo-po'),
 ('kbo-6','2026-11-03T18:30:00+09:00','TBD','KIA','UPCOMING',None,'한국시리즈','미정','kbo-ks')]:
    id,dt,a,b,s,score,stage,venue,sid=x;game(id,'KBO',dt,a,b,s,score,stage,venue,sid)
for x in [
 ('lck-1','2026-10-30T17:00:00+09:00','GEN','T1','UPCOMING',None,'승자조 결승','서울','lck-upper-final'),
 ('lck-2','2026-10-30T19:00:00+09:00','DK','HLE','LIVE',(1,1),'패자조','서울','lck-lower-1'),
 ('lck-3','2026-10-29T17:00:00+09:00','GEN','DK','FINAL',(3,1),'승자조 1R','서울','lck-upper-1'),
 ('lck-4','2026-10-31T17:00:00+09:00','KTLOL','BFX','UPCOMING',None,'패자조','서울','lck-lower-2'),
 ('lck-5','2026-11-07T17:00:00+09:00','TBD','TBD','UPCOMING',None,'최종 결승','서울','lck-final')]:
    id,dt,a,b,s,score,stage,venue,sid=x;game(id,'LCK',dt,a,b,s,score,stage,venue,sid)
for x in [
 ('worlds-1','2026-10-30T20:00:00+09:00','GEN','G2','UPCOMING',None,'8강','국제 대회','worlds-q1'),
 ('worlds-2','2026-10-30T23:00:00+09:00','T1','BLG','UPCOMING',None,'8강','국제 대회','worlds-q2'),
 ('worlds-3','2026-10-29T20:00:00+09:00','HLE','FNC','FINAL',(3,1),'8강','국제 대회','worlds-q3'),
 ('worlds-4','2026-11-05T20:00:00+09:00','TBD','TBD','UPCOMING',None,'4강','국제 대회','worlds-s1')]:
    id,dt,a,b,s,score,stage,venue,sid=x;game(id,'WORLDS',dt,a,b,s,score,stage,venue,sid)
for x in [
 ('msi-1','2026-10-30T16:00:00+09:00','GEN','BLG','UPCOMING',None,'승자조','국제 대회','msi-up'),
 ('msi-2','2026-10-31T16:00:00+09:00','T1','G2','UPCOMING',None,'패자조','국제 대회','msi-low')]:
    id,dt,a,b,s,score,stage,venue,sid=x;game(id,'MSI',dt,a,b,s,score,stage,venue,sid)
for x in [
 ('ewc-1','2026-10-30T21:00:00+09:00','GEN','FNC','UPCOMING',None,'준결승','국제 대회','ewc-s1'),
 ('ewc-2','2026-10-31T21:00:00+09:00','T1','G2','UPCOMING',None,'준결승','국제 대회','ewc-s2')]:
    id,dt,a,b,s,score,stage,venue,sid=x;game(id,'EWC',dt,a,b,s,score,stage,venue,sid)
for x in [
 ('fst-1','2026-10-30T17:00:00+09:00','GEN','G2','UPCOMING',None,'결승','국제 대회','fst-final')]:
    id,dt,a,b,s,score,stage,venue,sid=x;game(id,'FST',dt,a,b,s,score,stage,venue,sid)
for args in [
 ('kbo-wc','KBO','와일드카드','MAIN',0,'KT','NC',(2,1),'FINAL','2026-10-27T18:30:00+09:00',3,'kbo-qp'),
 ('kbo-qp','KBO','준플레이오프','MAIN',1,'SAM','KT',(3,1),'FINAL','2026-10-28T18:30:00+09:00',5,'kbo-po'),
 ('kbo-po','KBO','플레이오프','MAIN',2,'SAM','LG',(2,2),'UPCOMING','2026-10-30T18:30:00+09:00',5,'kbo-ks'),
 ('kbo-ks','KBO','한국시리즈','MAIN',3,'TBD','KIA',(0,0),'UPCOMING','2026-11-03T18:30:00+09:00',7,''),
 ('lck-upper-1','LCK','승자조 1R','UPPER',0,'GEN','DK',(3,1),'FINAL','2026-10-29T17:00:00+09:00',5,'lck-upper-final'),
 ('lck-upper-2','LCK','승자조 1R','UPPER',0,'T1','HLE',(3,2),'FINAL','2026-10-29T20:00:00+09:00',5,'lck-upper-final'),
 ('lck-upper-final','LCK','승자조 결승','UPPER',1,'GEN','T1',(0,0),'UPCOMING','2026-10-30T17:00:00+09:00',5,'lck-final'),
 ('lck-lower-1','LCK','패자조 1R','LOWER',0,'DK','HLE',(1,1),'LIVE','2026-10-30T19:00:00+09:00',5,'lck-lower-final'),
 ('lck-lower-2','LCK','패자조 1R','LOWER',0,'KTLOL','BFX',(0,0),'UPCOMING','2026-10-31T17:00:00+09:00',5,'lck-lower-final'),
 ('lck-lower-final','LCK','패자조 결승','LOWER',1,'TBD','TBD',(0,0),'UPCOMING','2026-11-03T17:00:00+09:00',5,'lck-final'),
 ('lck-final','LCK','최종 결승','MAIN',2,'TBD','TBD',(0,0),'UPCOMING','2026-11-07T17:00:00+09:00',5,''),
 ('worlds-q1','WORLDS','8강','MAIN',0,'GEN','G2',(0,0),'UPCOMING','2026-10-30T20:00:00+09:00',5,'worlds-s1'),
 ('worlds-q2','WORLDS','8강','MAIN',0,'T1','BLG',(0,0),'UPCOMING','2026-10-30T23:00:00+09:00',5,'worlds-s1'),
 ('worlds-q3','WORLDS','8강','MAIN',0,'HLE','FNC',(3,1),'FINAL','2026-10-29T20:00:00+09:00',5,'worlds-s2'),
 ('worlds-q4','WORLDS','8강','MAIN',0,'FLY','C9',(3,0),'FINAL','2026-10-29T17:00:00+09:00',5,'worlds-s2'),
 ('worlds-s1','WORLDS','4강','MAIN',1,'TBD','TBD',(0,0),'UPCOMING','2026-11-05T20:00:00+09:00',5,'worlds-final'),
 ('worlds-s2','WORLDS','4강','MAIN',1,'HLE','FLY',(0,0),'UPCOMING','2026-11-06T20:00:00+09:00',5,'worlds-final'),
 ('worlds-final','WORLDS','결승','MAIN',2,'TBD','TBD',(0,0),'UPCOMING','2026-11-09T20:00:00+09:00',5,''),
 ('msi-up','MSI','승자조 결승','UPPER',0,'GEN','BLG',(0,0),'UPCOMING','2026-10-30T16:00:00+09:00',5,'msi-final'),
 ('msi-low','MSI','패자조 결승','LOWER',0,'T1','G2',(0,0),'UPCOMING','2026-10-31T16:00:00+09:00',5,'msi-final'),
 ('msi-final','MSI','최종 결승','MAIN',1,'TBD','TBD',(0,0),'UPCOMING','2026-11-03T16:00:00+09:00',5,''),
 ('ewc-s1','EWC','준결승','MAIN',0,'GEN','FNC',(0,0),'UPCOMING','2026-10-30T21:00:00+09:00',3,'ewc-final'),
 ('ewc-s2','EWC','준결승','MAIN',0,'T1','G2',(0,0),'UPCOMING','2026-10-31T21:00:00+09:00',3,'ewc-final'),
 ('ewc-final','EWC','결승','MAIN',1,'TBD','TBD',(0,0),'UPCOMING','2026-11-02T21:00:00+09:00',5,''),
 ('fst-final','FST','결승','MAIN',0,'GEN','G2',(0,0),'UPCOMING','2026-10-30T17:00:00+09:00',5,'')
]: bracket(*args)
feed=dict(schemaVersion=1,updatedAt='2026-10-30T11:30:00+09:00',isDemo=True,demoDay='2026-10-30',teams=teams,games=games,standings=standings,series=series)
path=root/'feeds'; path.mkdir(exist_ok=True)
(path/'example.json').write_text(json.dumps(feed,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
for module in ('phone','wear'): shutil.copy2(path/'example.json', root/module/'src/main/assets/demo_feed.json')
# Generate simple original badges. These are placeholders, not trademarked club crests.
fontfiles=['/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc','/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']
fontfile=next((x for x in fontfiles if pathlib.Path(x).exists()),fontfiles[-1])
for id,sport,name,short,mark,color in team_defs:
    im=Image.new('RGBA',(144,144),(0,0,0,0)); d=ImageDraw.Draw(im)
    import PIL.ImageColor
    rgb=ImageColor.getrgb(color)
    d.ellipse((4,4,140,140),fill=rgb+(255,),outline=(255,255,255,226),width=4)
    d.ellipse((13,13,131,131),outline=(255,255,255,90),width=2)
    if sport=='KBO':
        d.arc((28,25,118,119),58,121,fill=(255,255,255,120),width=3)
        d.arc((28,25,118,119),239,300,fill=(255,255,255,120),width=3)
    else:
        d.polygon([(72,16),(115,40),(110,92),(72,126),(34,96),(30,41)],outline=(255,255,255,110),width=2)
    sz=47 if len(mark)<=2 else 34 if len(mark)<=3 else 28
    font=ImageFont.truetype(fontfile,sz)
    bb=d.textbbox((0,0),mark,font=font,stroke_width=0); tw=bb[2]-bb[0];th=bb[3]-bb[1]
    x=72-tw/2-bb[0];y=72-th/2-bb[1]-2
    d.text((x,y),mark,font=font,fill='white',stroke_width=1,stroke_fill=(0,0,0,85))
    for module in ('phone','wear'):
        im.save(root/module/f'src/main/res/drawable-nodpi/team_{id.lower()}.png')
print('demo feed written:',len(teams),'teams',len(games),'games',len(standings),'standings',len(series),'series')
