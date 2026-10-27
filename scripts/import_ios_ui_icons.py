#!/usr/bin/env python3
"""Translate the existing iOS Shape paths to Android VectorDrawable resources."""
from pathlib import Path
import math
# Exact translation of AtlasIcons.swift geometry from the iOS source.
root=Path(__file__).resolve().parents[1]/'app/src/main/res/drawable'
def save(name,path,w=24,h=24):
    (root/f'ic_atlas_{name}.xml').write_text(f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="{w}" android:viewportHeight="{h}"><path android:fillColor="#FF000000" android:pathData="{path}"/></vector>\n')
# Owned external-link shape from AtlasIcons.swift; conventional location pointer.
for name, path, stroke in [('external_link', 'M4,20L20,4M8,4H20V16', 3), ('location', 'M21,3L14,21L10,14L3,10Z', 2)]:
    (root/f'ic_atlas_{name}.xml').write_text(f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"><path android:pathData="{path}" android:fillColor="#00000000" android:strokeColor="#FF000000" android:strokeWidth="{stroke}" android:strokeLineJoin="round"/></vector>\n')
def rect(x,y,w,h,r):
    return f'M{x+r},{y}H{x+w-r}Q{x+w},{y} {x+w},{y+r}V{y+h-r}Q{x+w},{y+h} {x+w-r},{y+h}H{x+r}Q{x},{y+h} {x},{y+h-r}V{y+r}Q{x},{y} {x+r},{y}Z'
save('plus',rect(9.72,0,4.56,24,.6)+rect(0,9.72,24,4.56,.6))
save('menu',rect(1,6.5,22,3,.6)+rect(1,14.5,15.4,3,.6))
v=[(0,0),(4.2,0),(9,5.7),(13.8,0),(18,0),(11.5,8.5),(18,17),(13.8,17),(9,11.3),(4.2,17),(0,17),(6.5,8.5)]
def inset(p,q):
    d=math.dist(p,q);return (p[0]+(q[0]-p[0])*.35/d,p[1]+(q[1]-p[1])*.35/d)
p=''
for i,a in enumerate(v):
    b=inset(a,v[i-1]);c=inset(a,v[(i+1)%len(v)])
    p+=f'{"M" if i==0 else "L"}{b[0]},{b[1]}Q{a[0]},{a[1]} {c[0]},{c[1]}'
save('close',p+'Z',18,17)
save('moon','M14.88,1.68C6.48,9.6 14.16,20.4 22.56,16.56C16.56,27.12 1.2,24.24 2.16,13.2C0.48,5.04 8.16,0.48 14.88,1.68Z')
p='M17.04,12A5.04,5.04 0,1 1,6.96,12A5.04,5.04 0,1 1,17.04,12Z'
for i in range(8):
    # Exact source sun ray rectangles, rotated around the 24pt center.
    a=i*math.pi/4;pts=[]
    for x,y in [(10.68,.12),(13.32,.12),(13.32,4.68),(10.68,4.68)]:
        pts.append((12+(x-12)*math.cos(a)-(y-12)*math.sin(a),12+(x-12)*math.sin(a)+(y-12)*math.cos(a)))
    p+='M'+'L'.join(f'{x},{y}' for x,y in pts)+'Z'
save('sun',p)

# Owned source bookmark and arrow paths, used by destination/collection controls.
save('bookmark', 'M3,0H15Q18,0 18,3V23.2Q18,24.6 16.7,23.8L9,18.5L1.3,23.8Q0,24.6 0,23.2V3Q0,0 3,0Z',18,24)
save('arrow_right','M2,11H18L12,5L13.5,3.5L23,12L13.5,20.5L12,19L18,13H2Z')
v=[(0,0),(4.4,0),(14,11),(4.4,22),(0,22),(9.1,11)]
p=''
for i,a in enumerate(v):
    def rounded(q):
        distance=math.dist(a,q);return (a[0]+(q[0]-a[0])*.4/distance,a[1]+(q[1]-a[1])*.4/distance)
    b=rounded(v[i-1]);c=rounded(v[(i+1)%len(v)])
    p+=f'{"M" if i==0 else "L"}{b[0]},{b[1]}Q{a[0]},{a[1]} {c[0]},{c[1]}'
save('chevron',p+'Z',14,22)

save('bookmark_outline','M3,0H15Q18,0 18,3V23.2Q18,24.6 16.7,23.8L9,18.5L1.3,23.8Q0,24.6 0,23.2V3Q0,0 3,0ZM3,3.2V19.3L9,15.2L15,19.3V3.2Q15,2.6 14.4,2.6H3.6Q3,2.6 3,3.2Z',18,24)
