import os
import csv
import xml.etree.ElementTree as ET

def clean(s):
    if s is None:
        return ""
    return str(s).strip().replace('\t', ' ').replace('\r', '').replace('\n', '')

def format_display_name(name):
    # Ensure a single space after comma: "Žďár,Doubrava" -> "Žďár, Doubrava"
    if ',' in name and ', ' not in name:
        return name.replace(',', ', ')
    return name

def generate_database():
    os.makedirs('app/src/main/assets', exist_ok=True)
    out_path = 'app/src/main/assets/pid_stops.tsv'
    
    print('Parsing StopsByName.xml...')
    tree = ET.parse('StopsByName.xml')
    root = tree.getroot()
    
    stops_dict = {}
    
    for g in root.findall('group'):
        name = clean(g.attrib.get('name'))
        if not name:
            continue
            
        full_name = clean(g.attrib.get('fullName', name))
        unique_name = clean(g.attrib.get('uniqueName', name))
        idos_name = clean(g.attrib.get('idosName', ''))
        node = clean(g.attrib.get('node', ''))
        cis = clean(g.attrib.get('cis', ''))
        lat_str = g.attrib.get('avgLat', '0')
        lon_str = g.attrib.get('avgLon', '0')
        m_type = clean(g.attrib.get('mainTrafficType', 'bus'))
        muni = clean(g.attrib.get('municipality', ''))
        district = clean(g.attrib.get('districtCode', ''))
        is_train_attr = g.attrib.get('isTrain', 'false').lower() == 'true'
        
        try:
            lat = round(float(lat_str), 6)
            lon = round(float(lon_str), 6)
        except ValueError:
            lat = 0.0
            lon = 0.0
            
        # Collect lines from stop children
        lines = set()
        for s in g.findall('stop'):
            for l in s.findall('line'):
                l_name = clean(l.attrib.get('name'))
                if l_name:
                    lines.add(l_name)
                    
        is_train = is_train_attr or m_type == 'train' or any(l.startswith(('S', 'R', 'U', 'V', 'L', 'XS', 'XR')) for l in lines)
        
        display_name = format_display_name(name)
        golemio_primary = name
        
        region = muni
        if district and district != muni:
            region = f"{muni} ({district})" if muni else district
            
        # Alternative names
        alts = set()
        alts.add(name)
        alts.add(display_name)
        if full_name: alts.add(format_display_name(full_name))
        if unique_name: alts.add(format_display_name(unique_name))
        if idos_name: alts.add(format_display_name(idos_name))
        
        # Add parts separated by comma
        if ',' in name:
            parts = [p.strip() for p in name.split(',') if p.strip()]
            for p in parts:
                alts.add(p)
            # Add without comma: "Žďár Doubrava"
            alts.add(' '.join(parts))
            
        # Add variants for abbreviations
        for term, expansion in [
            ('žel.st.', 'železniční stanice'),
            ('žel.st.', 'nádraží'),
            ('žel.st.', 'vlak'),
            ('aut.st.', 'autobusové stanoviště'),
            ('aut.st.', 'autobusové nádraží'),
            ('nám.', 'náměstí'),
            ('rozc.', 'rozcestí'),
            ('u Mn.Hradiště', 'u Mnichova Hradiště'),
            ('u Mn.Hradiště', 'Mnichovo Hradiště'),
            ('hl.n.', 'hlavní nádraží'),
            ('Příhrazská', 'Příhrazy'),
            ('Příhrazská', 'Žďár, Příhrazy'),
            ('Příhrazská', 'Příhrazy skalní město'),
            ('Břehy,obecní úřad', 'Břehy'),
            ('Břehy,obecní úřad', 'Žďár, Břehy')
        ]:
            if term in name:
                alts.add(format_display_name(name.replace(term, expansion)))
                
        # Custom local aliases for Žďár a okolí
        if 'Žďár' in name:
            alts.add('Žďár')
            if 'Žehrov' in name:
                alts.add('Skokovy')
                alts.add('Žďár, Skokovy')
        if 'Loukov' in name:
            alts.add('Loukov')
            if 'Mnichova Hradiště' in name or 'Mn.Hradiště' in name or 'žel.st.' in name:
                alts.add('Loukov u Mnichova Hradiště')
                alts.add('Loukov, žel.st.')
                alts.add('Loukov vlak')
                alts.add('Loukov nádraží')
                
        # For Prague stops, add Praha prefix if not already present
        if muni == 'Praha' and not name.startswith('Praha'):
            alts.add(f"Praha, {display_name}")
            alts.add(f"Praha,{name}")
            alts.add(f"Praha {display_name}")
            
        # Add municipality alone
        if muni:
            alts.add(muni)
            
        # Discard empty or identical to display_name
        alts = [clean(a) for a in alts if clean(a) and clean(a) != display_name]
        
        stops_dict[name] = {
            'displayName': display_name,
            'golemioPrimaryName': golemio_primary,
            'region': region,
            'latitude': lat,
            'longitude': lon,
            'isTrain': is_train,
            'aswNode': node,
            'cis': cis,
            'municipality': muni,
            'district': district,
            'trafficType': m_type,
            'lines': sorted(list(lines)),
            'alternativeNames': sorted(list(set(alts)))
        }

    print(f'Processed {len(stops_dict)} stops from XML.')
    
    # Now merge passenger stops from GTFS that are not in XML
    print('Checking PID_GTFS/stops.txt...')
    with open('PID_GTFS/stops.txt', 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            s_name = clean(row.get('stop_name'))
            if not s_name or s_name in stops_dict:
                continue
            # Skip technical railway markers
            if any(p in s_name.lower() for p in ['km ', 'hr.', 'ahr ', 'vjezd.', 'násun.', 'odb ', 'výh.', 'vl. ']):
                continue
                
            try:
                lat = round(float(row.get('stop_lat', 0)), 6)
                lon = round(float(row.get('stop_lon', 0)), 6)
            except ValueError:
                continue
                
            node = clean(row.get('asw_node_id', ''))
            zone = clean(row.get('zone_id', ''))
            
            display_name = format_display_name(s_name)
            
            alts = set([s_name, display_name])
            if zone == 'P' or zone == '0':
                alts.add(f"Praha, {display_name}")
                alts.add(f"Praha,{s_name}")
            if ',' in s_name:
                for p in s_name.split(','):
                    if p.strip(): alts.add(p.strip())
                
            stops_dict[s_name] = {
                'displayName': display_name,
                'golemioPrimaryName': s_name,
                'region': f"Pásmo {zone}" if zone else "PID",
                'latitude': lat,
                'longitude': lon,
                'isTrain': False,
                'aswNode': node,
                'cis': '',
                'municipality': '',
                'district': '',
                'trafficType': 'bus',
                'lines': [],
                'alternativeNames': [clean(a) for a in sorted(list(alts)) if clean(a) and clean(a) != display_name]
            }

    print(f'Total stops after GTFS merge: {len(stops_dict)}')
    
    # Write TSV
    with open(out_path, 'w', encoding='utf-8') as f:
        for s in stops_dict.values():
            lines_str = ';'.join(s['lines'])
            alts_str = ';'.join(s['alternativeNames'])
            is_train_str = '1' if s['isTrain'] else '0'
            row = [
                s['displayName'],
                s['golemioPrimaryName'],
                s['region'],
                str(s['latitude']),
                str(s['longitude']),
                is_train_str,
                s['aswNode'],
                s['cis'],
                s['municipality'],
                s['district'],
                s['trafficType'],
                lines_str,
                alts_str
            ]
            f.write('\t'.join(row) + '\n')
            
    size = os.path.getsize(out_path)
    print(f'Wrote {len(stops_dict)} stops to {out_path} ({size:,} bytes, {size/1024:.1f} KB)')

if __name__ == '__main__':
    generate_database()

