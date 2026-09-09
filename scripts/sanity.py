#!/usr/bin/env python3
"""Static sanity checks (no Android toolchain needed): brace balance, string keys, forbidden APIs."""
import os, re, xml.etree.ElementTree as ET
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
kts = []
for d, _, fs in os.walk(os.path.join(root, 'app/src')):
    for f in fs:
        if f.endswith('.kt'): kts.append(os.path.join(d, f))
print(len(kts), 'kotlin files')

def strip(code):
    code = re.sub(r'"""[\s\S]*?"""', '""', code)
    code = re.sub(r'//.*', '', code)
    code = re.sub(r'/\*[\s\S]*?\*/', '', code)
    code = re.sub(r"'(\\.|[^'\\])'", "''", code)
    out=[]; i=0; n=len(code)
    while i<n:
        c=code[i]
        if c=='"':
            i+=1
            while i<n and code[i]!='"':
                if code[i]=='\\': i+=1
                elif code[i]=='$' and i+1<n and code[i+1]=='{':
                    depth=1; i+=2
                    while i<n and depth>0:
                        if code[i]=='{': depth+=1
                        elif code[i]=='}': depth-=1
                        i+=1
                    continue
                i+=1
            i+=1; out.append('""'); continue
        out.append(c); i+=1
    return ''.join(out)
bad=0
for p in kts:
    s=strip(open(p).read())
    for a,b in ['{}','()','[]']:
        if s.count(a)!=s.count(b):
            print('UNBALANCED', a, b, s.count(a), s.count(b), p); bad+=1
print('balance issues:', bad)

def keys(path):
    t=ET.parse(path).getroot()
    return {e.get('name') for e in t if e.tag in ('string','plurals')}
en=keys(os.path.join(root,'app/src/main/res/values/strings.xml'))
my=keys(os.path.join(root,'app/src/main/res/values-my/strings.xml'))
print('en keys', len(en), 'my keys', len(my), 'diff', en^my)
used=set()
for p in kts:
    used |= set(re.findall(r'R\.string\.(\w+)', open(p).read()))
for d,_,fs in os.walk(os.path.join(root,'app/src/main/res')):
    for f in fs:
        if f.endswith('.xml'):
            used |= set(re.findall(r'@string/(\w+)', open(os.path.join(d,f)).read()))
print('missing strings:', sorted(used-en))

for p in kts+[os.path.join(root,'app/src/main/AndroidManifest.xml')]:
    s=open(p).read()
    if 'WebView' in s or 'android.permission.INTERNET' in s: print('FORBIDDEN in', p)
    if re.search('[\U0001F300-\U0001FAFF\u2600-\u27BF]', s): print('EMOJI in', p)
    if '@Suppress("unused")' in s: print('SUPPRESS in', p)
    m=re.search(r'^package ([\w.]+)', s, re.M)
    if p.endswith('.kt'):
        base = os.path.join(root,'app/src/main/java') if '/main/' in p else os.path.join(root,'app/src/test/java')
        rel=os.path.relpath(os.path.dirname(p), base).replace('/','.')
        if not m or m.group(1)!=rel: print('PACKAGE MISMATCH', p, m and m.group(1), rel)

res=set()
for d,_,fs in os.walk(os.path.join(root,'app/src/main/res')):
    for f in fs: res.add(os.path.splitext(f)[0])
for p in kts:
    for kind,name in re.findall(r'R\.(drawable|raw|font|mipmap|xml)\.(\w+)', open(p).read()):
        if name not in res: print('MISSING RES', kind, name, p)

# imports: each imported simple name should appear elsewhere in the file
for p in kts:
    s=open(p).read()
    body=re.sub(r'^import .*$', '', s, flags=re.M)
    for imp in re.findall(r'^import ([\w.]+)$', s, re.M):
        name=imp.split('.')[-1]
        if not re.search(r'\b'+re.escape(name)+r'\b', body):
            print('UNUSED IMPORT', name, os.path.relpath(p, root))
print('done')
