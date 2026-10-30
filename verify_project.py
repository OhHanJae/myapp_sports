#!/usr/bin/env python3
"""Checks fixture references, app metadata, resources, preview outputs, and wrapper contents."""
from pathlib import Path
from zipfile import ZipFile
import json, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent
feed=json.loads((root/'feeds/example.json').read_text('utf8'))
assert feed['schemaVersion']==1 and feed['isDemo'] is True
ids={t['id'] for t in feed['teams']}|{'TBD'}
assert len(ids)>20
assert all(g['home'] in ids and g['away'] in ids for g in feed['games'])
assert all(s['left'] in ids and s['right'] in ids for s in feed['series'])
assert len({g['id'] for g in feed['games']})==len(feed['games'])
assert len({s['id'] for s in feed['series']})==len(feed['series'])
for module in ['phone','wear']:
    data=json.loads((root/module/'src/main/assets/demo_feed.json').read_text('utf8'))
    assert feed==data
    root_xml=ET.parse(root/module/'src/main/AndroidManifest.xml').getroot()
    assert root_xml.tag=='manifest'
    for team in feed['teams']:
        assert (root/module/f"src/main/res/drawable-nodpi/team_{team['id'].lower()}.png").is_file()
assert all((root/'previews'/f).exists() for f in ['index.html','01-kbo-home-and-watch.png','02-lol-bracket-and-watch.png','03-kbo-postseason-bracket.png','04-kbo-standings.png'])
with ZipFile(root/'gradle/wrapper/gradle-wrapper.jar') as z:
    assert 'org/gradle/wrapper/GradleWrapperMain.class' in z.namelist()
print(f"PASS: {len(feed['teams'])} teams, {len(feed['games'])} games, {len(feed['standings'])} rankings, {len(feed['series'])} bracket nodes; resources + manifests + previews + wrapper verified")
