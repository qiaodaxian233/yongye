# -*- coding: utf-8 -*-
"""display 变换投影预览器(m398 首创,m399 入库)。

复刻 MC 物品 display 管线,离线渲染第一/第三人称观感,用于:
1. 复现「整屏大刀」类 display 病态(喂旧值应逐像素复现截图形态);
2. 迭代新 display 值(改数→出图→肉眼比对基准 docs/hud/m398_fp_fixed.png);
3. 「握柄点归位」求平移:t = target − R(S(grip)),不手调平移。

管线(m398 已验证=与截图逐像素一致):
  顶点 p(模型单位 0..16)→ c = p/16 − 0.5 → 乘 scale → 旋转 R → 加 translation/16
  第一人称再加手锚 (±0.56, −0.52, −0.72)(左手 x 取负,旋转 y/z 取负),FOV70 透视。
旋转次序:JSON rotation=[x,y,z] 度,作用到向量 = 先 Z 后 Y 后 X(rotationXYZ)。
用法:python3 tools/display_previewer.py <模型json> fp|tp [rotX rotY rotZ sc tx ty tz] -o out.png
不带数值参数=用模型文件里的现值。--grip gx gy gz --target tx ty tz 可打印握柄归位平移。
"""
import json, math, sys
from PIL import Image, ImageDraw

def rot_mat(rx, ry, rz):
    """R = Rx·Ry·Rz(向量先 Z 后 Y 后 X)。角度制。"""
    ax, ay, az = (math.radians(v) for v in (rx, ry, rz))
    ca, sa = math.cos(ax), math.sin(ax)
    cb, sb = math.cos(ay), math.sin(ay)
    cc, sc = math.cos(az), math.sin(az)
    Rx = [[1,0,0],[0,ca,-sa],[0,sa,ca]]
    Ry = [[cb,0,sb],[0,1,0],[-sb,0,cb]]
    Rz = [[cc,-sc,0],[sc,cc,0],[0,0,1]]
    def mul(A,B):
        return [[sum(A[i][k]*B[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    return mul(Rx, mul(Ry, Rz))

def apply(M, v):
    return tuple(sum(M[i][k]*v[k] for k in range(3)) for i in range(3))

def load_boxes(path):
    d = json.load(open(path))
    return [(e['from'], e['to']) for e in d.get('elements', [])], d

FACES = [  # 每面 4 角索引(corner 位序 = (x选f/t, y选f/t, z选f/t) 二进制)与法轴
    ((0,1,3,2), 0, -1), ((4,5,7,6), 0, +1),   # x-,x+
    ((0,1,5,4), 1, -1), ((2,3,7,6), 1, +1),   # y-,y+
    ((0,2,6,4), 2, -1), ((1,3,7,5), 2, +1),   # z-,z+
]

def corners(f, t):
    return [ ( (f[0],t[0])[i>>2&1], (f[1],t[1])[i>>1&1], (f[2],t[2])[i&1] ) for i in range(8) ]

def transform_all(boxes, rot, scale, trans, hand='fp', left=False):
    rx, ry, rz = rot
    tx, ty, tz = (v/16.0 for v in trans)
    if left:
        ry, rz, tx = -ry, -rz, -tx
    M = rot_mat(rx, ry, rz)
    anchor = ((-0.56 if left else 0.56), -0.52, -0.72) if hand == 'fp' else (0.0, 0.0, 0.0)
    out = []
    for f, t in boxes:
        cs = []
        for p in corners(f, t):
            c = tuple(p[i]/16.0 - 0.5 for i in range(3))
            c = tuple(c[i]*scale for i in range(3))
            c = apply(M, c)
            cs.append(tuple(c[i] + (tx,ty,tz)[i] + anchor[i] for i in range(3)))
        out.append(cs)
    return out

def render_fp(tboxes, out_png, w=1280, h=720, fov=70.0):
    img = Image.new('RGB', (w, h), (110, 140, 190))
    dr = ImageDraw.Draw(img)
    dr.rectangle([0, int(h*0.62), w, h], fill=(96, 120, 78))  # 地平线草地
    ty = math.tan(math.radians(fov/2)); aspect = w/h
    def proj(p):
        x, y, z = p
        if z > -0.05: return None
        return (w/2 + x/(-z*ty*aspect)*(w/2), h/2 - y/(-z*ty)*(h/2), -z)
    quads = []
    for cs in tboxes:
        for idx, axis, sgn in FACES:
            ps = [proj(cs[i]) for i in idx]
            if any(p is None for p in ps): continue
            depth = sum(p[2] for p in ps)/4
            shade = (140, 150, 170)[axis]
            base = 60 + axis*40 + (25 if sgn > 0 else 0)
            quads.append((depth, [(p[0], p[1]) for p in ps], (base+60, base+40, base+90)))
    for depth, poly, col in sorted(quads, key=lambda q: -q[0]):
        dr.polygon(poly, fill=col, outline=None)
    cx, cy = w//2, h//2
    dr.line([cx-10, cy, cx+10, cy], fill='white', width=2)
    dr.line([cx, cy-10, cx, cy+10], fill='white', width=2)
    img.save(out_png)

def render_tp(tboxes, out_png, w=720, h=720):
    """第三人称侧视正交:x 屏右=世界 z(前),y 屏上;画玩家侧影,手点=原点。"""
    img = Image.new('RGB', (w, h), (110, 140, 190))
    dr = ImageDraw.Draw(img)
    ppb = 220.0  # 像素/格
    ox, oy = w*0.45, h*0.55  # 手点屏幕位置
    def px(z, y):  # 侧视:屏 x=世界 z(负 z 朝右=面前) 屏 y=世界 y
        return (ox - z*ppb, oy - y*ppb)
    # 玩家侧影(手点约在体前 0.35 格、身高 1.8,手高 ~0.9→手点即原点,脚在 y=-0.9)
    body_x = ox - 0.30*ppb
    dr.rectangle([body_x-0.15*ppb, oy-0.75*ppb, body_x+0.15*ppb, oy+0.9*ppb], fill=(70,80,100))
    dr.rectangle([body_x-0.14*ppb, oy-1.05*ppb, body_x+0.14*ppb, oy-0.77*ppb], fill=(90,100,120))
    quads = []
    for cs in tboxes:
        for idx, axis, sgn in FACES:
            ps = [px(cs[i][2], cs[i][1]) for i in idx]
            depth = sum(cs[i][0] for i in idx)/4
            base = 60 + axis*40 + (25 if sgn > 0 else 0)
            quads.append((depth, ps, (base+60, base+40, base+90)))
    for depth, poly, col in sorted(quads, key=lambda q: q[0]):
        dr.polygon(poly, fill=col)
    dr.ellipse([ox-4, oy-4, ox+4, oy+4], fill='red')  # 手点
    img.save(out_png)

def solve_translation(rot, scale, grip, target):
    M = rot_mat(*rot)
    g = tuple(grip[i]/16.0 - 0.5 for i in range(3))
    g = apply(M, tuple(v*scale for v in g))
    return tuple((target[i] - g[i]) * 16.0 for i in range(3))

if __name__ == '__main__':
    args = sys.argv[1:]
    model, mode = args[0], args[1]
    out = args[args.index('-o')+1] if '-o' in args else '/tmp/preview.png'
    boxes, d = load_boxes(model)
    key = 'firstperson_righthand' if mode == 'fp' else 'thirdperson_righthand'
    disp = d.get('display', {}).get(key, {})
    rot = disp.get('rotation', [0,0,0]); sc = disp.get('scale', [1,1,1])[0]; tr = disp.get('translation', [0,0,0])
    rest = [a for a in args[2:] if a not in ('-o', out) and not a.startswith('--')]
    if len(rest) >= 7:
        rot = [float(rest[0]), float(rest[1]), float(rest[2])]; sc = float(rest[3]); tr = [float(v) for v in rest[4:7]]
    if '--grip' in args:
        i = args.index('--grip'); grip = [float(args[i+1]), float(args[i+2]), float(args[i+3])]
        j = args.index('--target'); target = [float(args[j+1]), float(args[j+2]), float(args[j+3])]
        tr = solve_translation(rot, sc, grip, target)
        print('握柄归位平移(JSON 单位):', [round(v, 2) for v in tr])
    tb = transform_all(boxes, rot, sc, tr, hand=mode)
    (render_fp if mode == 'fp' else render_tp)(tb, out)
    print(f'{mode} rot={rot} scale={sc} trans={[round(v,2) for v in tr]} -> {out}')
