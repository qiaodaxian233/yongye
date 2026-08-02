#!/usr/bin/env python3
"""只读分析 Minecraft Java 人物、DragonCore/萌芽风格与 MythicMobs 资源包。

不会执行压缩包中的脚本或程序。支持目录或 ZIP。
可选依赖：Pillow、PyYAML；音频元数据需要系统 ffprobe。
"""
from __future__ import annotations
import argparse, collections, hashlib, io, json, os, re, shutil, subprocess, tempfile, zipfile
from pathlib import Path
from typing import Any
try:
    from PIL import Image
except Exception:
    Image=None
try:
    import yaml
except Exception:
    yaml=None

def safe_extract(zf: zipfile.ZipFile,target: Path)->None:
    root=target.resolve()
    for m in zf.infolist():
        out=(target/m.filename).resolve()
        if out!=root and root not in out.parents: raise ValueError(f"ZIP 路径穿越: {m.filename}")
    zf.extractall(target)

def read_json(p:Path)->Any:
    return json.loads(p.read_text(encoding='utf-8-sig'))

def rel(p:Path,root:Path)->str:return p.relative_to(root).as_posix()

def geometry_report(p:Path,root:Path)->dict|None:
    try:d=read_json(p)
    except Exception:return None
    geoms=d.get('minecraft:geometry') if isinstance(d,dict) else None
    if not isinstance(geoms,list):return None
    out=[]
    for g in geoms:
        if not isinstance(g,dict):continue
        desc=g.get('description') or {}; bones=g.get('bones') or []
        names=[b.get('name') for b in bones if isinstance(b,dict) and b.get('name')]
        parents={b.get('name'):b.get('parent') for b in bones if isinstance(b,dict) and b.get('name')}
        roots=[n for n,pv in parents.items() if not pv]
        missing=[{'bone':n,'parent':pv} for n,pv in parents.items() if pv and pv not in parents]
        cube_count=zero=rot=empty_uv=faces=0; mins=[None]*3;maxs=[None]*3
        for b in bones:
            if not isinstance(b,dict):continue
            for c in b.get('cubes') or []:
                if not isinstance(c,dict):continue
                cube_count+=1
                o,s=c.get('origin'),c.get('size')
                if isinstance(o,list) and isinstance(s,list) and len(o)==len(s)==3:
                    for i in range(3):
                        lo=min(o[i],o[i]+s[i]);hi=max(o[i],o[i]+s[i])
                        mins[i]=lo if mins[i] is None else min(mins[i],lo)
                        maxs[i]=hi if maxs[i] is None else max(maxs[i],hi)
                    if any(abs(v)<1e-9 for v in s):zero+=1
                if c.get('rotation') is not None:rot+=1
                uv=c.get('uv')
                if isinstance(uv,dict):
                    if not uv:empty_uv+=1
                    faces+=len(uv)
                elif isinstance(uv,list):faces+=6
        out.append({'identifier':desc.get('identifier'),'texture_size':[desc.get('texture_width'),desc.get('texture_height')],
                    'visible_bounds':{'width':desc.get('visible_bounds_width'),'height':desc.get('visible_bounds_height'),'offset':desc.get('visible_bounds_offset')},
                    'bones':len(bones),'roots':roots,'duplicate_bones':[n for n,c in collections.Counter(names).items() if c>1],
                    'missing_parents':missing,'cubes':cube_count,'rotated_cubes':rot,'zero_thickness_cubes':zero,
                    'empty_uv_cubes':empty_uv,'faces':faces,'bounds_min':mins,'bounds_max':maxs})
    return {'path':rel(p,root),'format_version':d.get('format_version'),'geometries':out}

def animation_report(p:Path,root:Path)->dict|None:
    try:d=read_json(p)
    except Exception:return None
    anims=d.get('animations') if isinstance(d,dict) else None
    if not isinstance(anims,dict):return None
    rows=[]
    for name,a in anims.items():
        if not isinstance(a,dict):continue
        bones=a.get('bones') or {}; kf=0;mx=0.0;channels=collections.Counter()
        for bn,bd in bones.items():
            if not isinstance(bd,dict):continue
            for ch,v in bd.items():
                channels[ch]+=1
                if isinstance(v,dict):
                    kf+=len(v)
                    for t in v:
                        try:mx=max(mx,float(t))
                        except Exception:pass
        rows.append({'name':name,'loop':a.get('loop',False),'animation_length':a.get('animation_length'),
                     'animated_bones':len(bones),'bone_names':sorted(bones),'channels':dict(channels),'keyframes':kf,'max_keyframe_time':mx})
    return {'path':rel(p,root),'format_version':d.get('format_version'),'animations':rows}

def main()->int:
    ap=argparse.ArgumentParser();ap.add_argument('source');ap.add_argument('--output',default='character-report.json');ap.add_argument('--markdown-output')
    args=ap.parse_args();src=Path(args.source)
    with tempfile.TemporaryDirectory() as td:
        root=Path(td)/'root'
        if src.is_dir():shutil.copytree(src,root)
        elif zipfile.is_zipfile(src):root.mkdir();safe_extract(zipfile.ZipFile(src),root)
        else:raise SystemExit('只支持目录或 ZIP')
        files=[p for p in root.rglob('*') if p.is_file()]
        geometries=[];animations=[];invalid_json=[]
        for p in files:
            if p.suffix.lower()=='.json':
                try:
                    g=geometry_report(p,root);a=animation_report(p,root)
                    if g:geometries.append(g)
                    if a:animations.append(a)
                    if not g and not a:read_json(p)
                except Exception as e:invalid_json.append({'path':rel(p,root),'error':str(e)})
        images=[]
        if Image:
            for p in files:
                if p.suffix.lower()=='.png':
                    try:
                        im=Image.open(p).convert('RGBA');hist=im.getchannel('A').histogram();non=sum(hist[1:])
                        images.append({'path':rel(p,root),'size':[im.width,im.height],'occupancy':round(non/(im.width*im.height),6)})
                    except Exception as e:images.append({'path':rel(p,root),'error':str(e)})
        yaml_docs=[];all_text=''
        for p in files:
            if p.suffix.lower() in ('.yml','.yaml'):
                s=p.read_text(encoding='utf-8-sig',errors='replace');all_text+='\n'+s
                row={'path':rel(p,root)}
                if yaml:
                    try:
                        d=yaml.safe_load(s);row['top_level_keys']=list(d) if isinstance(d,dict) else []
                    except Exception as e:row['parse_error']=str(e)
                if re.search(r':\s*[^"\'\n]+"\s*$',s,re.M):row['possible_unbalanced_quote']=True
                yaml_docs.append(row)
        defined_anims={a['name'] for doc in animations for a in doc['animations']}
        anim_refs=re.findall(r'animation\{[^}]*?name=([^;}]+)',all_text,re.I)
        sound_refs=[Path(x).name for x in re.findall(r'sound\{[^}]*?s=([^;}]+)',all_text,re.I) if x.lower().endswith('.ogg')]
        sounds={p.name for p in files if p.suffix.lower()=='.ogg'}
        hashes=collections.defaultdict(list)
        for p in files:
            h=hashlib.sha256(p.read_bytes()).hexdigest();hashes[h].append(rel(p,root))
        report={'source':str(src),'file_count':len(files),'extensions':dict(collections.Counter(p.suffix.lower() for p in files)),
                'geometries':geometries,'animation_files':animations,'images':images,'yaml':yaml_docs,'invalid_json':invalid_json,
                'cross_references':{'animation_refs':dict(collections.Counter(anim_refs)),'missing_animations':sorted(set(anim_refs)-defined_anims),
                                    'defined_but_not_explicitly_referenced':sorted(defined_anims-set(anim_refs)),
                                    'sound_refs':sorted(set(sound_refs)),'missing_sounds':sorted(set(sound_refs)-sounds)},
                'duplicate_groups':[v for v in hashes.values() if len(v)>1]}
        Path(args.output).write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        if args.markdown_output:
            lines=['# 人物模型包静态分析','',f"- 文件：{len(files)}",f"- 几何文件：{len(geometries)}",f"- 动画文件：{len(animations)}",f"- PNG：{len(images)}",f"- YAML：{len(yaml_docs)}",f"- 缺失动画引用：{len(report['cross_references']['missing_animations'])}",f"- 缺失音效引用：{len(report['cross_references']['missing_sounds'])}",'','完整数据见 JSON。']
            Path(args.markdown_output).write_text('\n'.join(lines)+'\n',encoding='utf-8')
    return 0
if __name__=='__main__':raise SystemExit(main())
