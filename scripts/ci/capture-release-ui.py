#!/usr/bin/env python3
"""Снимки и геометрия настоящего Android UI; запуск только внутри Actions."""
import hashlib
import json
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

assert os.environ.get('GITHUB_ACTIONS') == 'true'
assert os.environ.get('GITHUB_REPOSITORY') == 'dedtsss/dispatcher-android-inbox'
OUT = Path('visual')
PKG = 'ru.bruce.dispatcherinbox'
APK = Path('release/catching-0.6.11-release.apk')
BASE = Path(os.environ['CATCHING_VISUAL_BASELINE'])


def adb(*args, check=True):
    return subprocess.run(['adb', *args], check=check, capture_output=True).stdout


def launch():
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'am', 'start', '-W', '-n', PKG + '/.MainActivity')
    time.sleep(2)


def snapshot(name):
    adb('shell', 'uiautomator', 'dump', '/sdcard/catching-window.xml')
    adb('pull', '/sdcard/catching-window.xml', str(OUT / (name + '.xml')))
    (OUT / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))
    return ET.parse(OUT / (name + '.xml')).getroot()


def bounds(node):
    return tuple(map(int, re.findall(r'\d+', node.get('bounds', ''))))


def target(root, label):
    parents = {child: parent for parent in root.iter() for child in parent}
    found = [n for n in root.iter('node') if label in (n.get('content-desc'), n.get('text'))]
    assert found, f'Не найдено действие: {label}'
    node = found[-1]
    while node.get('clickable') != 'true' and node in parents:
        node = parents[node]
    assert node.get('clickable') == 'true', f'Действие не нажимается: {label}'
    return bounds(node)


def tap(root, label):
    left, top, right, bottom = target(root, label)
    adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))
    time.sleep(1)


def screen(width, height):
    # Явно фиксируем density, чтобы widths соответствовали dp, а не названию AVD.
    adb('shell', 'wm', 'density', '320')
    adb('shell', 'wm', 'size', f'{width * 2}x{height * 2}')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')


geometry = {}
try:
    adb('shell', 'svc', 'wifi', 'disable')
    adb('shell', 'svc', 'data', 'disable')
    # Очистка разрешена только в одноразовом AVD; предыдущие миграционные
    # доказательства уже сохранены, данные физического телефона не затрагиваются.
    adb('uninstall', PKG, check=False)
    if BASE.exists():
        assert hashlib.sha256(BASE.read_bytes()).hexdigest() == '366156f0cb087475f52257d04ccd010a0d6969ab845495cd8925df5943182900'
        adb('install', str(BASE))
        screen(390, 844)
        launch()
        snapshot('capture-before-0.6.10-390')
        adb('shell', 'am', 'force-stop', PKG)
        adb('install', '-r', str(APK))
    else:
        adb('install', str(APK))
    adb('shell', 'pm', 'grant', PKG, 'android.permission.RECORD_AUDIO')
    (OUT / 'installed-release-package.txt').write_bytes(adb('shell', 'dumpsys', 'package', PKG))
    for width, height in [(360, 800), (390, 844), (412, 915), (360, 640)]:
        key = f'{width}x{height}'
        screen(width, height)
        launch()
        root = snapshot('capture-idle-' + key)
        actions = {name: target(root, name) for name in ['Начать запись', 'Текст', 'Файл', 'Записать', 'Очередь', 'Настройки']}
        for label, (left, top, right, bottom) in actions.items():
            assert right - left >= 96 and bottom - top >= 96, (key, label, 'Область нажатия менее 48dp', actions[label])
            assert 0 <= left < right <= width * 2 and 0 <= top < bottom <= height * 2, (key, label, actions[label])
        mic, text, file = (actions[k] for k in ['Начать запись', 'Текст', 'Файл'])
        assert abs((mic[0] + mic[2]) / 2 - width) <= 4, (key, 'Микрофон не по центру')
        assert mic[2] - mic[0] >= width * 0.85, (key, 'Микрофон слишком мал')
        assert abs(text[1] - file[1]) <= 4 and text[2] < file[0], (key, 'Текст/Файл не в одном ряду')
        assert mic[3] < text[1] and text[3] < actions['Записать'][1], (key, 'Нарушена иерархия')
        geometry[key] = {'density': 2, 'actionsPx': actions}
        if width == 390:
            tap(root, 'Начать запись')
            time.sleep(2)
            root = snapshot('capture-recording-390')
            tap(root, 'Остановить и сохранить')
            root = snapshot('capture-saved-390')
            tap(root, 'Очередь')
            root = snapshot('capture-queue-390')
            tap(root, 'Записать')
            root = snapshot('capture-return-390')
            tap(root, 'Текст')
            root = snapshot('capture-keyboard-390')
            adb('shell', 'input', 'text', 'release-ui-check')
            adb('shell', 'input', 'keyevent', '4')
            root = snapshot('capture-text-390')
            # Возврат из системного выбора файла, без добавления случайных файлов.
            tap(root, 'Файл')
            snapshot('capture-file-picker-390')
            adb('shell', 'input', 'keyevent', '4')
            root = snapshot('capture-file-return-390')
            tap(root, 'Сохранить')
            root = snapshot('capture-text-saved-390')
            tap(root, 'Текст')
            snapshot('capture-idle-after-text-390')
            # Текст сохранён настоящей release-логикой; следующий размер снова idle.
            adb('shell', 'am', 'force-stop', PKG)
    screen(390, 844)
    launch()
    adb('shell', 'am', 'start', '-W', '-a', 'android.intent.action.ASSIST', '-n', PKG + '/.assistant.AssistantEntryActivity')
    time.sleep(2)
    snapshot('capture-assistant-390')
    (OUT / 'capture-geometry.json').write_text(json.dumps(geometry, ensure_ascii=False, indent=2))
    (OUT / 'capture-source.json').write_text(json.dumps({'head': Path('release/source-head.txt').read_text().strip(), 'releaseSha256': hashlib.sha256(APK.read_bytes()).hexdigest(), 'baseline': 'v0.6.10' if BASE.exists() else None, 'signal': 'Эмулятор: системный микрофон; не физическая речь и не калибровка чувствительности.'}, ensure_ascii=False, indent=2))
finally:
    adb('shell', 'wm', 'size', 'reset', check=False)
    adb('shell', 'wm', 'density', 'reset', check=False)
