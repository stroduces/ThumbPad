"""Structural format inspection. Never creates or renames fake artifacts."""
import hashlib, json, pathlib, struct, sys, zipfile
root=pathlib.Path(sys.argv[1] if len(sys.argv)>1 else 'dist')
results=[]
for path in sorted(root.iterdir()):
    if path.suffix not in ('.apk','.exe','.dmg'): continue
    data=path.read_bytes()
    if path.suffix=='.apk':
        with zipfile.ZipFile(path) as z:
            assert z.testzip() is None
            assert {'AndroidManifest.xml','classes.dex','resources.arsc'} <= set(z.namelist())
        kind='Android APK (binary manifest / DEX / resources verified)'
    elif path.suffix=='.exe':
        assert data[:2]==b'MZ'; offset=struct.unpack_from('<I',data,0x3c)[0];assert data[offset:offset+4]==b'PE\0\0'
        assert b'NullsoftInst' in data, 'Not an NSIS installer'
        kind='PE / NSIS Windows installer'
    else:
        assert data[-512:-508]==b'koly', 'Not a UDIF disk image'
        kind='UDIF macOS disk image (hdiutil verify also required)'
    results.append(dict(file=path.name,bytes=len(data),format=kind,sha256=hashlib.sha256(data).hexdigest()))
assert results, 'No real release artifacts found'
print(json.dumps(results,indent=2));(root/'artifact-inspection.json').write_text(json.dumps(results,indent=2))
