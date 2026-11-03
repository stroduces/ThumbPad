"""Create a clean source archive without credentials, caches or generated binaries."""
import pathlib, zipfile
root=pathlib.Path(__file__).resolve().parent.parent
excluded={'dist','.tools','target','build','node_modules','.gradle','.git','.kotlin','.idea','local-smoke','__pycache__'}
out=root/'dist'/'ThumbPad-source.zip';out.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as archive:
    for p in root.rglob('*'):
        if not p.is_file():continue
        relative=p.relative_to(root)
        if set(relative.parts)&excluded or p.name=='local.properties' or p.suffix in {'.jks','.keystore','.log'}:continue
        archive.write(p,'ThumbPad/'+relative.as_posix())
with zipfile.ZipFile(out) as archive:
    assert archive.testzip() is None
    for item in ['android/gradle/wrapper/gradle-wrapper.jar','.github/workflows/build.yml','desktop/core/Cargo.lock','desktop/src-tauri/Cargo.lock','README.md']:
        assert 'ThumbPad/'+item in archive.namelist()
print(f'{out}: {out.stat().st_size} bytes; source archive verified')
