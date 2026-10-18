#!/usr/bin/env python3
"""Verify the published OgAuto release APK using installed Android build tools."""
import argparse, hashlib, subprocess, zipfile
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('apk',type=Path)
p.add_argument('--build-tools',required=True,type=Path)
a=p.parse_args()
def run(name,*args):
 return subprocess.check_output([str(a.build_tools/name),*map(str,args)],text=True,stderr=subprocess.STDOUT)
badging=run('aapt','dump','badging',a.apk)
assert "package: name='com.onurguner.ogauto' versionCode='1' versionName='0.1.0'" in badging,badging[:300]
assert "application-label:'OgAuto'" in badging
manifest=run('aapt','dump','xmltree',a.apk,'AndroidManifest.xml')
assert 'android:debuggable' not in manifest, 'Release APK must not be debuggable'
with zipfile.ZipFile(a.apk) as z:
 license=z.read('assets/LICENSE-AA-Suite.txt').decode()
 assert 'Copyright (c) 2026 iAlias' in license
 assert not any('keystore' in x.lower() or x.endswith('.p12') for x in z.namelist())
print(run('apksigner','verify','--verbose','--print-certs',a.apk))
print(run('zipalign','-c','4',a.apk))
print('Package, version, label, release flag, license and signature verified.')
print('SHA256:',hashlib.sha256(a.apk.read_bytes()).hexdigest())
