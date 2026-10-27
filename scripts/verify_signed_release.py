"""Verify staged signed artifacts. Requires the official bundletool jar; never reads signing passwords."""
from pathlib import Path
import json,subprocess,hashlib,zipfile,struct,xml.etree.ElementTree as ET
import argparse,os
root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--release-dir',type=Path,default=root/'artifacts/google-play/1.0.0-1')
parser.add_argument('--bundletool',type=Path,required=True)
args=parser.parse_args()
out=args.release_dir.resolve()
sdk=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'Library/Android/sdk')));java=Path(os.environ.get('JAVA_HOME','/Applications/Android Studio.app/Contents/jbr/Contents/Home'))/'bin'
aab=out/'Flytly-1.0.0-1.aab';apk=out/'Flytly-1.0.0-1.apk';jar=str(args.bundletool.resolve())
def run(args):return subprocess.run(list(map(str,args)),check=True,capture_output=True,text=True).stdout
for name,args in [('apksigner.txt',[sdk/'build-tools/36.0.0/apksigner','verify','--verbose','--print-certs',apk]),('zipalign.txt',[sdk/'build-tools/36.0.0/zipalign','-c','-P','16','-v','4',apk]),('bundle-validation.txt',[java/'java','-jar',jar,'validate','--bundle='+str(aab)]),('bundle-manifest.xml',[java/'java','-jar',jar,'dump','manifest','--bundle='+str(aab)]),('bundle-config.json',[java/'java','-jar',jar,'dump','config','--bundle='+str(aab)])]:
 (out/name).write_text(run(args))
cert=json.loads((out/'upload-certificate.json').read_text());fingerprint=cert['certificateSHA256'].replace(':','').lower()
assert fingerprint in (out/'apksigner.txt').read_text()
cert_text=run([java/'keytool','-printcert','-jarfile',aab]);assert cert['certificateSHA256'] in cert_text
(out/'aab-certificate.txt').write_text(cert_text)
jarlog=run([java/'jarsigner','-verify',aab]);assert 'jar verified.' in jarlog;(out/'aab-verification-summary.txt').write_text(jarlog)
a='http://schemas.android.com/apk/res/android';manifest=ET.fromstring((out/'bundle-manifest.xml').read_text());app=manifest.find('application')
assert manifest.attrib['package']=='com.mountzou.flytly'
assert manifest.attrib['{'+a+'}versionName']=='1.0.0' and manifest.attrib['{'+a+'}versionCode']=='1'
assert app.attrib.get('{'+a+'}debuggable','false')=='false' and app.attrib['{'+a+'}allowBackup']=='false'
permissions=[p.attrib['{'+a+'}name'] for p in manifest.findall('uses-permission')]
assert 'android.permission.ACCESS_COARSE_LOCATION' in permissions
assert not set(permissions)&{'android.permission.ACCESS_FINE_LOCATION','android.permission.ACCESS_BACKGROUND_LOCATION'}
assert not any('AD_ID' in p for p in permissions)
config=json.loads((out/'bundle-config.json').read_text());assert config['optimizations']['uncompressNativeLibraries']['alignment']=='PAGE_ALIGNMENT_16K'
rows=[]
with zipfile.ZipFile(aab) as z:
 dex=b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex'))
 assert b'PlayIntegrityAppCheckProviderFactory' in dex and b'DebugAppCheckProviderFactory' not in dex
 for n in z.namelist():
  if not n.endswith('.so'):continue
  b=z.read(n);order='<' if b[5]==1 else '>';wide=b[4]==2
  off=struct.unpack_from(order+('Q' if wide else 'I'),b,32 if wide else 28)[0];size,count=struct.unpack_from(order+'HH',b,54 if wide else 42)
  align=[struct.unpack_from(order+('Q' if wide else 'I'),b,off+i*size+(48 if wide else 28))[0] for i in range(count) if struct.unpack_from(order+'I',b,off+i*size)[0]==1]
  rows.append({'entry':n,'loadSegmentAlignment':align,'supports16KB':all(v>=16384 for v in align),'is64Bit':wide})
assert all(r['supports16KB'] for r in rows if r['is64Bit'])
(out/'native-alignment.json').write_text(json.dumps(rows,indent=2)+'\n')
reports=list((root/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
tests=failures=errors=skipped=0
for p in reports:
 r=ET.parse(p).getroot()
 for k in ['tests','failures','errors','skipped']:
  v=int(r.attrib.get(k,0))
  if k=='tests':tests+=v
  elif k=='failures':failures+=v
  elif k=='errors':errors+=v
  else:skipped+=v
assert tests>0 and failures==0 and errors==0
lint=ET.parse(root/'app/build/reports/lint-results-release.xml').getroot();issues={}
for item in lint.findall('issue'):issues[item.attrib['severity']]=issues.get(item.attrib['severity'],0)+1
assert not issues.get('Error',0) and not issues.get('Fatal',0)
files={p.name:{'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'bytes':p.stat().st_size} for p in [aab,apk,out/'flytly-upload-certificate.pem']}
registered=json.loads((out/'firebase-sha-after.json').read_text())
assert registered['status']=='success'
hashes={r['shaHash'].lower() for r in registered['result']}
assert fingerprint in hashes and cert['certificateSHA1'].replace(':','').lower() in hashes
summary={'versionName':'1.0.0','versionCode':1,'package':'com.mountzou.flytly','artifacts':files,'signerSHA256':fingerprint,'apkSignatureSchemes':['v2','v3'],'aabSignature':'verified; expected self-signed upload certificate','unitTests':{'tests':tests,'failures':failures,'errors':errors,'skipped':skipped},'lintIssues':issues,'debuggable':False,'backupEnabled':False,'releaseAppCheck':'Play Integrity; debug provider absent','permissions':permissions,'native64BitAlignment16KB':True,'zipAlignment16KB':True,'firebaseUploadCertificateRegistered':True,'playStoreUploadPerformed':False,'playAppSigningCertificateRegistered':False,'playAttestedAuthenticationSyncAndJevTested':False}
(out/'static-verification.json').write_text(json.dumps(summary,indent=2)+'\n');print(json.dumps(summary,indent=2))
