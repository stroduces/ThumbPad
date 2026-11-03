import pathlib,shutil,sys
target=sys.argv[1];root=pathlib.Path('desktop/src-tauri/target')/target/'release/bundle';out=pathlib.Path('dist');out.mkdir(exist_ok=True)
if 'windows' in target:
    files=list(root.glob('nsis/*-setup.exe'));assert len(files)==1;shutil.copy2(files[0],out/'ThumbPad-Windows-Setup.exe')
else:
    files=list(root.glob('dmg/*.dmg'));assert len(files)==1
    arch='AppleSilicon' if target.startswith('aarch64') else 'Intel'
    shutil.copy2(files[0],out/f'ThumbPad-macOS-{arch}.dmg')
