#!/usr/bin/env python3
from pathlib import Path
from playwright.sync_api import sync_playwright
from threading import Thread
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
import functools
root=Path(__file__).resolve().parent
server=ThreadingHTTPServer(('127.0.0.1',0),functools.partial(SimpleHTTPRequestHandler,directory=str(root)))
Thread(target=server.serve_forever,daemon=True).start()
with sync_playwright() as p:
  browser=p.chromium.launch(headless=True, executable_path='/usr/bin/chromium', args=['--no-sandbox'])
  page=browser.new_page(viewport={'width':1280,'height':970}, device_scale_factor=1)
  import base64, json
  raw=(root/'previews/index.html').read_text('utf8')
  logos={f.stem.replace('team_','').upper(): 'data:image/png;base64,'+base64.b64encode(f.read_bytes()).decode() for f in (root/'phone/src/main/res/drawable-nodpi').glob('team_*.png')}
  raw=raw.replace("const teams=", 'const logoImages='+json.dumps(logos)+';const teams=')
  raw=raw.replace('src=\"../phone/src/main/res/drawable-nodpi/team_${id.toLowerCase()}.png\"','src=\"${logoImages[id]}\"')
  page.set_content(raw,wait_until='load');page.wait_for_timeout(400)
  print('rendered:',page.locator('#body').inner_text()[:80])
  page.screenshot(path=str(root/'previews/01-kbo-home-and-watch.png'),full_page=True)
  page.get_by_role('button',name='LoL · Gen.G').click()
  page.get_by_role('button',name='대진',exact=True).first.click()
  page.screenshot(path=str(root/'previews/02-lol-bracket-and-watch.png'),full_page=True)
  page.get_by_role('button',name='KBO · 삼성').click()
  page.get_by_role('button',name='대진',exact=True).first.click()
  page.screenshot(path=str(root/'previews/03-kbo-postseason-bracket.png'),full_page=True)
  page.get_by_role('button',name='순위',exact=True).first.click()
  page.screenshot(path=str(root/'previews/04-kbo-standings.png'),full_page=True)
  browser.close()
server.shutdown()
print('screenshots rendered')
