"""Extract typing-sized passages from every paragraph in the supplied FB2 ZIP.
Usage: python tools/extract_typing_book.py path/to/book.zip
The original archive is not copied into the repository.
"""
from pathlib import Path
from zipfile import ZipFile
import xml.etree.ElementTree as ET
import re, sys, hashlib

with ZipFile(sys.argv[1]) as archive:
    source = next(name for name in archive.namelist() if name.lower().endswith('.fb2'))
    root = ET.fromstring(archive.read(source))
ns = {'f': 'http://www.gribuser.ru/xml/fictionbook/2.0'}
passages = []
for body in root.findall('f:body', ns):
    if body.get('name') in ('notes', 'comments'):
        continue
    for paragraph in body.findall('.//f:p', ns):
        text = re.sub(r'\s+', ' ', ''.join(paragraph.itertext())).strip()
        for sentence in re.split(r'(?<=[.!?…])\s+', text):
            chunk = ''
            for word in sentence.split():
                if chunk and len(chunk) + len(word) + 1 > 180:
                    passages.append(chunk)
                    chunk = ''
                chunk = (chunk + ' ' + word).strip()
            if chunk and any(c.isalpha() for c in chunk):
                passages.append(chunk)
passages = list(dict.fromkeys(passages))
output = Path(__file__).resolve().parents[1] / 'app/src/main/assets/typing-book-ru.tsv'
output.write_text(''.join('book-' + hashlib.sha256(text.encode()).hexdigest()[:16] + '\t' + text + '\n' for text in passages), encoding='utf-8')
print(f'{len(passages)} passages; {source}; max length {max(map(len, passages))}')
