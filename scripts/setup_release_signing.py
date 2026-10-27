#!/usr/bin/env python3
"""Create the first Flytly upload key without exposing credentials or overwriting a key."""
from pathlib import Path
import argparse,base64,hashlib,json,os,secrets,subprocess,tempfile

parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path,default=Path('artifacts/google-play/1.0.0-1'));args=parser.parse_args()
private=Path.home()/'.android/flytly-release'
private.mkdir(mode=0o700,parents=True,exist_ok=True);private.chmod(0o700)
profile=private/'signing.properties';keystore=private/'flytly-upload.p12'
if profile.exists() or keystore.exists():
    raise SystemExit('Signing material already exists; refusing to replace it.')
keytool=Path(os.environ.get('JAVA_HOME','/Applications/Android Studio.app/Contents/jbr/Contents/Home'))/'bin/keytool'
password=secrets.token_urlsafe(48)
fd,name=tempfile.mkstemp(prefix='.keytool-',dir=private);os.fchmod(fd,0o600)
try:
    with os.fdopen(fd,'w') as stream:stream.write(password+'\n')
    command=[str(keytool),'-genkeypair','-noprompt','-alias','flytly-upload','-keyalg','RSA','-keysize','3072','-sigalg','SHA256withRSA','-validity','10000','-dname','CN=Flytly Android Upload, OU=Mobile, O=Flytly','-storetype','PKCS12','-keystore',str(keystore),'-storepass:file',name,'-keypass:file',name]
    result=subprocess.run(command,capture_output=True,text=True)
    if result.returncode:raise SystemExit('keytool failed; inspect the private folder before retrying.')
    keystore.chmod(0o600)
    data=f'# PRIVATE: Flytly upload signing. Never commit or share.\nstoreFile={keystore}\nstorePassword={password}\nkeyAlias=flytly-upload\nkeyPassword={password}\n'
    out=os.open(profile,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(out,'w') as stream:stream.write(data)
    args.output.mkdir(parents=True,exist_ok=True)
    cert=args.output/'flytly-upload-certificate.pem'
    subprocess.run([str(keytool),'-exportcert','-rfc','-alias','flytly-upload','-keystore',str(keystore),'-storepass:file',name,'-file',str(cert)],check=True,capture_output=True)
    text=cert.read_text();der=base64.b64decode(''.join(line for line in text.splitlines() if not line.startswith('-----')))
    def fingerprint(algorithm):return ':'.join(algorithm(der).hexdigest()[i:i+2] for i in range(0,algorithm().digest_size*2,2)).upper()
    report={'purpose':'Upload key for Google Play, not the Google-managed app signing key','alias':'flytly-upload','keyAlgorithm':'RSA-3072','certificateSHA1':fingerprint(hashlib.sha1),'certificateSHA256':fingerprint(hashlib.sha256),'privateMaterialOutsideProject':True,'privateDirectoryMode':'0700','privateFilesMode':'0600','privateProfile':str(profile),'publicCertificate':str(cert.resolve())}
    (args.output/'upload-certificate.json').write_text(json.dumps(report,indent=2)+'\n')
    print('Created Flytly upload key and private signing profile. Only public certificate metadata was exported.')
finally:
    Path(name).unlink(missing_ok=True)
