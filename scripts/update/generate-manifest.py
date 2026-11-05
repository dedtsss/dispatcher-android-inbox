#!/usr/bin/env python3
"""Сформировать метаданные уже подписанного APK без публикации канала."""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path
from urllib.parse import urlsplit


def generate(apk, apk_url, build_tools):
    url = urlsplit(apk_url)
    if url.scheme != 'https' or not url.hostname or url.username or url.password:
        raise ValueError('Нужен HTTPS URL APK без встроенных учётных данных')
    certs = subprocess.check_output([str(build_tools / 'apksigner'), 'verify', '--print-certs', str(apk)], text=True)
    signers = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})', certs)
    if len(signers) != 1:
        raise ValueError('Ожидался один текущий сертификат подписи')
    badging = subprocess.check_output([str(build_tools / 'aapt'), 'dump', 'badging', str(apk)], text=True)
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not package or package[1] != 'ru.bruce.dispatcherinbox':
        raise ValueError('Неожиданный пакет APK')
    return dict(versionCode=int(package[2]), versionName=package[3], apkUrl=apk_url,
                sha256=hashlib.file_digest(apk.open('rb'), 'sha256').hexdigest(),
                packageName=package[1], signerSha256=signers[0].lower())


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--apk-url', required=True)
    parser.add_argument('--build-tools', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.write_text(json.dumps(generate(args.apk, args.apk_url, args.build_tools), ensure_ascii=False, indent=2) + '\n')
