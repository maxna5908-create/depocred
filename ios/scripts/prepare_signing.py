#!/usr/bin/env python3
"""Local helper (Windows/macOS/Linux + OpenSSL): create CSR or package Apple's certificate.
Never run this helper in public Actions: the generated key must remain private.
"""
import argparse
import base64
import getpass
import os
from pathlib import Path
import subprocess

parser=argparse.ArgumentParser()
parser.add_argument('action',choices=['csr','package'])
parser.add_argument('--directory',type=Path,required=True,help='Private folder outside the repository')
parser.add_argument('--certificate',type=Path)
parser.add_argument('--profile',type=Path)
args=parser.parse_args()
directory=args.directory.expanduser().resolve()
repo=Path(__file__).resolve().parents[2]
if directory==repo or repo in directory.parents: parser.error('Choose a private folder outside the Git repository')
directory.mkdir(parents=True,exist_ok=True)
if os.name!='nt': directory.chmod(0o700)
key=directory/'depocred-distribution.key'
if args.action=='csr':
    if key.exists(): parser.error('Key already exists; use the existing CSR or a new private folder')
    password=getpass.getpass('New private-key password: ')
    if not password or password!=getpass.getpass('Repeat password: '): parser.error('Passwords are empty or do not match')
    env=dict(os.environ,DEPOCRED_SIGNING_PASSWORD=password)
    subprocess.run(['openssl','genpkey','-algorithm','RSA','-pkeyopt','rsa_keygen_bits:2048','-aes-256-cbc','-pass','env:DEPOCRED_SIGNING_PASSWORD','-out',str(key)],env=env,check=True)
    subprocess.run(['openssl','req','-new','-sha256','-key',str(key),'-passin','env:DEPOCRED_SIGNING_PASSWORD','-subj','/CN=DepoCred Distribution','-out',str(directory/'DepoCred.certSigningRequest')],env=env,check=True)
    print('Upload only DepoCred.certSigningRequest to Apple. Keep the private key and its password.')
else:
    if not args.certificate or not args.profile or not key.exists(): parser.error('Provide --certificate and --profile and the original private-key directory')
    password=getpass.getpass('Private-key password: ')
    p12_password=getpass.getpass('New .p12 password: ')
    if not p12_password or p12_password!=getpass.getpass('Repeat .p12 password: '): parser.error('Passwords are empty or do not match')
    env=dict(os.environ,DEPOCRED_SIGNING_PASSWORD=password,DEPOCRED_P12_PASSWORD=p12_password)
    pem=directory/'distribution-cert.pem'
    subprocess.run(['openssl','x509','-inform','DER','-in',str(args.certificate),'-out',str(pem)],check=True)
    p12=directory/'depocred-distribution.p12'
    subprocess.run(['openssl','pkcs12','-export','-inkey',str(key),'-passin','env:DEPOCRED_SIGNING_PASSWORD','-in',str(pem),'-out',str(p12),'-passout','env:DEPOCRED_P12_PASSWORD','-keypbe','PBE-SHA1-3DES','-certpbe','PBE-SHA1-3DES','-macalg','sha1'],env=env,check=True)
    (directory/'APPLE_CERTIFICATE_BASE64.txt').write_text(base64.b64encode(p12.read_bytes()).decode())
    (directory/'APPLE_PROVISIONING_PROFILE_BASE64.txt').write_text(base64.b64encode(args.profile.read_bytes()).decode())
    print('Paste the two generated Base64 files and the .p12 password into the documented GitHub Secrets. Do not commit them.')
