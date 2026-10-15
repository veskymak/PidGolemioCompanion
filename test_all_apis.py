import urllib.request
import urllib.error
import json
import time
import ssl
import sys

sys.stdout.reconfigure(encoding='utf-8')

with open('local.properties', 'r', encoding='utf-8') as f:
    for line in f:
        if line.startswith('GOLEMIO_API_KEY='):
            api_key = line.split('=', 1)[1].strip()
            break

headers = {
    'X-Access-Token': api_key,
    'Accept': 'application/json',
    'User-Agent': 'PidGolemioCompanion/1.0'
}

ctx = ssl.create_default_context()

tests = [
    ('Odjezdy: Žďár, Doubrava (aswIds=6403)', 'https://api.golemio.cz/v2/pid/departureboards?aswIds=6403&minutesBefore=0&minutesAfter=240&limit=20'),
    ('Odjezdy: Loukov nádražní uzel (aswIds=9441)', 'https://api.golemio.cz/v2/pid/departureboards?aswIds=9441&minutesBefore=0&minutesAfter=240&limit=20'),
    ('Odjezdy: Mladá Boleslav aut.st. (names=Mladá Boleslav,aut.st.)', 'https://api.golemio.cz/v2/pid/departureboards?names=Mlad%C3%A1%20Boleslav%2Caut.st.&minutesBefore=0&minutesAfter=120&limit=20'),
    ('Odjezdy: Turnov terminál (names=Turnov,Terminál u žel.st.)', 'https://api.golemio.cz/v2/pid/departureboards?names=Turnov%2CTermin%C3%A1l%20u%20%C5%BEel.st.&minutesBefore=0&minutesAfter=120&limit=20'),
    ('Odjezdy: Praha Hlavní nádraží (aswIds=280)', 'https://api.golemio.cz/v2/pid/departureboards?aswIds=280&minutesBefore=0&minutesAfter=60&limit=20'),
    ('Živé polohy vozidel: Všechny spoje PID (limit=5000)', 'https://api.golemio.cz/v2/vehiclepositions?limit=5000&includeNotTracking=true'),
    ('Živé polohy vozidel: Linka 715 (Bakov - Loukov - Žďár)', 'https://api.golemio.cz/v2/vehiclepositions?routeShortName=715&includeNotTracking=true'),
    ('Živé polohy vozidel: Linka 345 (Praha - Mn. Hradiště - Turnov)', 'https://api.golemio.cz/v2/vehiclepositions?routeShortName=345&includeNotTracking=true'),
    ('P+R Parkoviště: Praha P+R parkoviště (v3/parking)', 'https://api.golemio.cz/v3/parking?primarySource=tsk-offstreet&limit=100'),
    ('P+R Měření: Živá obsazenost parkovišť (v3/parking-measurements)', 'https://api.golemio.cz/v3/parking-measurements?primarySource=tsk-offstreet&limit=100'),
    ('CARTO Basemaps dlaždice (Voyager Raster PNG)', 'https://basemaps.cartocdn.com/rastertiles/voyager/14/8912/5614.png?key=cb1_4ll2_1_f0afdd71b59d8a4f0f10563a')
]

results = []
print(f'Testování API požadavků s Golemio klíčem (délka: {len(api_key)} znaků)...\n')

for label, url in tests:
    t0 = time.time()
    try:
        req_headers = headers if 'cartocdn' not in url else {'User-Agent': 'PidGolemioCompanion/1.0'}
        req = urllib.request.Request(url, headers=req_headers)
        with urllib.request.urlopen(req, context=ctx, timeout=15) as resp:
            data = resp.read()
            dt = (time.time() - t0) * 1000
            status = resp.status
            
            if 'cartocdn' in url:
                info = f'HTTP {status} OK, PNG dlaždice ({len(data):,} bajtů)'
            else:
                j = json.loads(data.decode('utf-8'))
                if isinstance(j, dict) and 'departures' in j:
                    deps = j.get('departures', [])
                    sample = []
                    for d in deps[:3]:
                        r = d.get('route', {}).get('short_name', '')
                        h = d.get('trip', {}).get('headsign', '')
                        sched = d.get('departure_timestamp', {}).get('scheduled', '')
                        t = sched[11:16] if len(sched) >= 16 else ''
                        delay = d.get('delay', {}).get('seconds', 0)
                        delay_str = f" (+{delay//60}m)" if delay and delay > 0 else ""
                        sample.append(f"{r} -> {h} v {t}{delay_str}")
                    info = f'HTTP {status} OK, nalezeno {len(deps)} odjezdů. [{", ".join(sample)}]'
                elif isinstance(j, dict) and 'features' in j:
                    feats = j.get('features', [])
                    if 'vehiclepositions' in url:
                        sample = []
                        for f_item in feats[:5]:
                            rn = f_item.get('properties', {}).get('trip', {}).get('gtfs', {}).get('routeShortName')
                            if rn: sample.append(rn)
                        info = f'HTTP {status} OK, {len(feats)} vozidel online. [{", ".join(sample)}]'
                    elif 'parking' in url:
                        sample = [p.get('properties', {}).get('name') for p in feats[:4] if p.get('properties', {}).get('name')]
                        info = f'HTTP {status} OK, {len(feats)} P+R parkovišť v Praze. [{", ".join(sample)}]'
                    else:
                        info = f'HTTP {status} OK, {len(feats)} prvků.'
                elif isinstance(j, list) and 'parking-measurements' in url:
                    sample = [f"{m.get('parking_id','').split('-')[-1][:6]}: {m.get('free_spot_number')}/{m.get('total_spot_number')} volno" for m in j[:3]]
                    info = f'HTTP {status} OK, {len(j)} měření obsazenosti P+R. [{", ".join(sample)}]'
                else:
                    info = f'HTTP {status} OK ({len(data):,} bajtů)'
            results.append((label, True, dt, info))
    except urllib.error.HTTPError as e:
        dt = (time.time() - t0) * 1000
        body = e.read().decode('utf-8')[:200]
        results.append((label, False, dt, f'HTTP {e.code} Error: {body}'))
    except Exception as e:
        dt = (time.time() - t0) * 1000
        results.append((label, False, dt, f'Chyba spojení: {e}'))

print('=== SOUHRNNÉ VÝSLEDKY TESTŮ API ===')
for label, success, dt, info in results:
    status_icon = '✅' if success else '❌'
    print(f'{status_icon} [{dt:.0f} ms] {label}')
    print(f'     -> {info}')

