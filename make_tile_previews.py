from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
root=Path(__file__).resolve().parent
out=root/'wear/src/main/res/drawable-nodpi'
fontfile='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
for typ,color in [('kbo',(37,108,229)),('lol',(18,151,86))]:
    im=Image.new('RGBA',(320,320),(0,0,0,0));d=ImageDraw.Draw(im)
    d.ellipse((0,0,319,319),fill=(8,20,34),outline=color,width=8)
    font=ImageFont.truetype(fontfile,30)
    d.text((160,94),typ.upper(),font=font,anchor='mm',fill='white')
    d.text((160,151),'MATCHDAY',font=ImageFont.truetype(fontfile,25),anchor='mm',fill=color)
    d.text((160,207),'VS  18:30',font=ImageFont.truetype(fontfile,22),anchor='mm',fill='white')
    im.save(out/f'tile_preview_{typ}.png')
